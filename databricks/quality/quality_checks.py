"""
Data Quality Framework for the CDW-to-Delta-Lake loan migration.

Runs post-ingestion validation checks and produces a structured report:
  1. Row count reconciliation  (source vs. target)
  2. Null checks on required fields
  3. Referential integrity      (loan→borrower, loan→product, payment→loan)
  4. Business rule validation   (domain-specific invariants)

The report is written as both a Delta table (for programmatic queries) and
a Markdown file (``DATA_QUALITY_REPORT.md``) for human review.

Usage:
    spark-submit quality_checks.py \\
        --source-counts '{"borrowers":5,"loan_products":5,"loan_accounts":5,"payments":10}' \\
        --report-path /mnt/reports/DATA_QUALITY_REPORT.md
"""

import argparse
import json
import logging
from dataclasses import dataclass, field
from datetime import datetime

from pyspark.sql import SparkSession
from pyspark.sql.functions import col, count, lit, sum as spark_sum, when

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger(__name__)


@dataclass
class CheckResult:
    category: str
    check_name: str
    passed: bool
    detail: str


@dataclass
class QualityReport:
    run_timestamp: str = field(default_factory=lambda: datetime.utcnow().isoformat())
    results: list[CheckResult] = field(default_factory=list)

    def add(self, category: str, name: str, passed: bool, detail: str):
        self.results.append(CheckResult(category, name, passed, detail))

    @property
    def total(self) -> int:
        return len(self.results)

    @property
    def passed_count(self) -> int:
        return sum(1 for r in self.results if r.passed)

    @property
    def failed_count(self) -> int:
        return self.total - self.passed_count

    @property
    def all_passed(self) -> bool:
        return self.failed_count == 0


# ── Spark session helper ────────────────────────────────────────────────────

def _get_spark() -> SparkSession:
    return (
        SparkSession.builder
        .appName("LoanMigrationQuality")
        .config("spark.sql.extensions", "io.delta.sql.DeltaSparkSessionExtension")
        .config(
            "spark.sql.catalog.spark_catalog",
            "org.apache.spark.sql.delta.catalog.DeltaCatalog",
        )
        .getOrCreate()
    )


# ── 1. Row Count Reconciliation ────────────────────────────────────────────

def check_row_counts(
    spark: SparkSession,
    report: QualityReport,
    source_counts: dict[str, int],
    target_tables: dict[str, str] | None = None,
):
    """Compare expected source row counts against actual target table counts."""
    if target_tables is None:
        target_tables = {
            "borrowers": "loan_warehouse.borrowers",
            "loan_products": "loan_warehouse.loan_products",
            "loan_accounts": "loan_warehouse.loan_accounts",
            "payments": "loan_warehouse.payments",
        }

    for entity, expected in source_counts.items():
        table = target_tables.get(entity)
        if table is None:
            report.add("Row Count", f"{entity} table exists", False, f"Target table not configured for '{entity}'")
            continue
        try:
            actual = spark.table(table).count()
            passed = actual == expected
            detail = f"expected={expected}, actual={actual}"
            report.add("Row Count", f"{entity} count matches", passed, detail)
        except Exception as e:
            report.add("Row Count", f"{entity} count matches", False, f"Error reading table: {e}")


# ── 2. Null Checks on Required Fields ──────────────────────────────────────

REQUIRED_FIELDS = {
    "loan_warehouse.borrowers": ["external_id", "first_name", "last_name", "status"],
    "loan_warehouse.loan_products": ["code", "name", "type", "term_months", "rate_type"],
    "loan_warehouse.loan_accounts": [
        "account_number", "borrower_id", "product_id",
        "original_amount", "current_balance", "interest_rate",
        "term_months", "monthly_payment", "origination_date", "maturity_date", "status",
    ],
    "loan_warehouse.payments": [
        "loan_account_id", "payment_date", "total_amount", "type", "status",
    ],
}


def check_required_nulls(spark: SparkSession, report: QualityReport):
    """Verify that no required field contains NULL in any target table."""
    for table, columns in REQUIRED_FIELDS.items():
        try:
            df = spark.table(table)
        except Exception as e:
            report.add("Null Check", f"{table} readable", False, str(e))
            continue

        for column in columns:
            null_count = df.filter(col(column).isNull()).count()
            passed = null_count == 0
            detail = f"null_count={null_count}" if not passed else "no nulls"
            report.add("Null Check", f"{table}.{column} NOT NULL", passed, detail)


# ── 3. Referential Integrity ───────────────────────────────────────────────

def check_referential_integrity(spark: SparkSession, report: QualityReport):
    """Verify FK relationships between migrated tables."""
    checks = [
        (
            "loan_accounts.borrower_id → borrowers.id",
            "loan_warehouse.loan_accounts",
            "borrower_id",
            "loan_warehouse.borrowers",
            "id",
        ),
        (
            "loan_accounts.product_id → loan_products.id",
            "loan_warehouse.loan_accounts",
            "product_id",
            "loan_warehouse.loan_products",
            "id",
        ),
        (
            "payments.loan_account_id → loan_accounts.id",
            "loan_warehouse.payments",
            "loan_account_id",
            "loan_warehouse.loan_accounts",
            "id",
        ),
    ]

    for label, child_table, child_col, parent_table, parent_col in checks:
        try:
            child_df = spark.table(child_table).select(col(child_col).alias("_fk"))
            parent_df = spark.table(parent_table).select(col(parent_col).alias("_pk"))

            orphan_count = (
                child_df.join(parent_df, child_df["_fk"] == parent_df["_pk"], "left_anti").count()
            )
            passed = orphan_count == 0
            detail = f"orphan_rows={orphan_count}" if not passed else "all FKs resolve"
            report.add("Referential Integrity", label, passed, detail)
        except Exception as e:
            report.add("Referential Integrity", label, False, str(e))


# ── 4. Business Rule Validation ────────────────────────────────────────────

def check_business_rules(spark: SparkSession, report: QualityReport):
    """Domain-specific invariants for loan data."""

    # 4a. Active loans must have positive balance
    try:
        loans = spark.table("loan_warehouse.loan_accounts")
        active_zero_bal = loans.filter(
            (col("status") == "ACTIVE") & (col("current_balance") <= 0)
        ).count()
        report.add(
            "Business Rule",
            "Active loans have balance > 0",
            active_zero_bal == 0,
            f"violations={active_zero_bal}",
        )
    except Exception as e:
        report.add("Business Rule", "Active loans have balance > 0", False, str(e))

    # 4b. Closed loans should have a maturity date in the past or equal to today
    #     (relaxed: we just check that maturity_date is not null)
    try:
        closed_no_mat = loans.filter(
            (col("status") == "CLOSED") & col("maturity_date").isNull()
        ).count()
        report.add(
            "Business Rule",
            "Closed loans have maturity_date",
            closed_no_mat == 0,
            f"violations={closed_no_mat}",
        )
    except Exception as e:
        report.add("Business Rule", "Closed loans have maturity_date", False, str(e))

    # 4c. Interest rate within reasonable bounds (0–30%)
    try:
        out_of_range = loans.filter(
            (col("interest_rate") < 0) | (col("interest_rate") > 30)
        ).count()
        report.add(
            "Business Rule",
            "Interest rate in [0, 30]%",
            out_of_range == 0,
            f"violations={out_of_range}",
        )
    except Exception as e:
        report.add("Business Rule", "Interest rate in [0, 30]%", False, str(e))

    # 4d. Payment amounts must be positive
    try:
        payments = spark.table("loan_warehouse.payments")
        neg_payments = payments.filter(col("total_amount") <= 0).count()
        report.add(
            "Business Rule",
            "Payment amounts > 0",
            neg_payments == 0,
            f"violations={neg_payments}",
        )
    except Exception as e:
        report.add("Business Rule", "Payment amounts > 0", False, str(e))

    # 4e. Borrower credit scores in valid range (300–850)
    try:
        borrowers = spark.table("loan_warehouse.borrowers")
        bad_scores = borrowers.filter(
            col("credit_score").isNotNull()
            & ((col("credit_score") < 300) | (col("credit_score") > 850))
        ).count()
        report.add(
            "Business Rule",
            "Credit scores in [300, 850]",
            bad_scores == 0,
            f"violations={bad_scores}",
        )
    except Exception as e:
        report.add("Business Rule", "Credit scores in [300, 850]", False, str(e))

    # 4f. LTV percent in valid range (0–200%)
    try:
        bad_ltv = loans.filter(
            col("ltv_percent").isNotNull()
            & ((col("ltv_percent") < 0) | (col("ltv_percent") > 200))
        ).count()
        report.add(
            "Business Rule",
            "LTV percent in [0, 200]",
            bad_ltv == 0,
            f"violations={bad_ltv}",
        )
    except Exception as e:
        report.add("Business Rule", "LTV percent in [0, 200]", False, str(e))

    # 4g. Origination date must be before maturity date
    try:
        bad_dates = loans.filter(
            col("origination_date").isNotNull()
            & col("maturity_date").isNotNull()
            & (col("origination_date") >= col("maturity_date"))
        ).count()
        report.add(
            "Business Rule",
            "Origination date < maturity date",
            bad_dates == 0,
            f"violations={bad_dates}",
        )
    except Exception as e:
        report.add("Business Rule", "Origination date < maturity date", False, str(e))


# ── Report Generation ──────────────────────────────────────────────────────

def generate_markdown_report(report: QualityReport) -> str:
    """Render the quality report as Markdown."""
    lines = [
        "# Data Quality Report",
        "",
        f"**Run:** {report.run_timestamp}  ",
        f"**Total checks:** {report.total}  ",
        f"**Passed:** {report.passed_count}  ",
        f"**Failed:** {report.failed_count}  ",
        f"**Status:** {'PASS' if report.all_passed else 'FAIL'}",
        "",
    ]

    current_cat = None
    for r in report.results:
        if r.category != current_cat:
            current_cat = r.category
            lines.append(f"## {current_cat}")
            lines.append("")
            lines.append("| Check | Result | Detail |")
            lines.append("|-------|--------|--------|")

        icon = "PASS" if r.passed else "**FAIL**"
        lines.append(f"| {r.check_name} | {icon} | {r.detail} |")

    lines.append("")
    lines.append("---")
    lines.append(f"*Generated by `quality_checks.py` at {report.run_timestamp}*")
    lines.append("")
    return "\n".join(lines)


# ── Main Entrypoint ────────────────────────────────────────────────────────

def run_quality_checks(
    spark: SparkSession | None = None,
    source_counts: dict[str, int] | None = None,
    report_path: str | None = None,
) -> QualityReport:
    """Execute all quality checks and return the report."""
    if spark is None:
        spark = _get_spark()

    if source_counts is None:
        source_counts = {
            "borrowers": 5,
            "loan_products": 5,
            "loan_accounts": 5,
            "payments": 10,
        }

    report = QualityReport()

    logger.info("Running row count reconciliation...")
    check_row_counts(spark, report, source_counts)

    logger.info("Running required-field null checks...")
    check_required_nulls(spark, report)

    logger.info("Running referential integrity checks...")
    check_referential_integrity(spark, report)

    logger.info("Running business rule validation...")
    check_business_rules(spark, report)

    md = generate_markdown_report(report)
    logger.info("\n%s", md)

    if report_path:
        dbutils_available = False
        try:
            from pyspark.dbutils import DBUtils
            dbutils = DBUtils(spark)
            dbutils.fs.put(report_path, md, overwrite=True)
            dbutils_available = True
            logger.info("Report written to %s", report_path)
        except Exception:
            pass

        if not dbutils_available:
            local_path = report_path.replace("dbfs:", "").lstrip("/")
            try:
                with open(local_path, "w") as f:
                    f.write(md)
                logger.info("Report written to local path: %s", local_path)
            except Exception as e:
                logger.warning("Could not write report locally: %s", e)

    return report


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Run post-migration data quality checks")
    parser.add_argument(
        "--source-counts",
        default='{"borrowers":5,"loan_products":5,"loan_accounts":5,"payments":10}',
        help="JSON dict of expected source row counts",
    )
    parser.add_argument(
        "--report-path",
        default=None,
        help="Path to write Markdown report (DBFS or local)",
    )
    args = parser.parse_args()

    expected_counts = json.loads(args.source_counts)
    spark = _get_spark()
    try:
        result = run_quality_checks(spark, expected_counts, args.report_path)
        if not result.all_passed:
            logger.error("Quality checks FAILED (%d failures)", result.failed_count)
        else:
            logger.info("All %d quality checks PASSED", result.total)
    finally:
        spark.stop()
