"""
Ingestion script: CDW_PMT_HIST -> loan_warehouse.payments (Delta Lake)

Reads legacy payment history from CSV/Parquet source files, resolves
loan_account foreign keys, expands type/status codes, derives the
partition column (payment_year), and writes to the payments Delta table.
"""

import argparse
import logging
import sys

from pyspark.sql import SparkSession
from pyspark.sql.functions import col, year

from common import (
    PAYMENT_STATUS_MAP,
    PAYMENT_TYPE_MAP,
    expand_status,
    get_or_create_spark,
    parse_date_col,
    parse_decimal_col,
    parse_timestamp_col,
    quarantine_nulls,
)

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger(__name__)

DEFAULT_SOURCE_PATH = "/mnt/landing/cdw_pmt_hist/"
DEFAULT_TARGET_TABLE = "loan_warehouse.payments"
DEFAULT_QUARANTINE_PATH = "/mnt/quarantine/payments/"
DEFAULT_LOAN_TABLE = "loan_warehouse.loan_accounts"
SOURCE_FORMAT = "csv"


def ingest_payments(
    spark: SparkSession,
    source_path: str = DEFAULT_SOURCE_PATH,
    target_table: str = DEFAULT_TARGET_TABLE,
    quarantine_path: str = DEFAULT_QUARANTINE_PATH,
    loan_table: str = DEFAULT_LOAN_TABLE,
    source_format: str = SOURCE_FORMAT,
) -> dict:
    """Run the payments ingestion pipeline."""

    logger.info("Reading legacy payment data from %s", source_path)

    reader = spark.read.format(source_format)
    if source_format == "csv":
        reader = reader.option("header", "true").option("inferSchema", "false")

    raw_df = reader.load(source_path)
    source_count = raw_df.count()
    logger.info("Source row count: %d", source_count)

    # ----- FK Resolution: loan_account_id -----
    loans_df = spark.table(loan_table).select(
        col("id").alias("_loan_pk"),
        col("account_number").alias("_acct_nbr"),
    )

    # ----- Core transformations -----
    transformed_df = raw_df.select(
        col("PMT_SEQ_NBR").alias("legacy_payment_id"),
        col("LN_ACCT_NBR").alias("_acct_nbr"),
        parse_date_col("PMT_DT", "payment_date"),
        parse_decimal_col("PMT_AMT", 10, 2, "total_amount"),
        parse_decimal_col("PMT_PRIN_AMT", 10, 2, "principal_amount"),
        parse_decimal_col("PMT_INT_AMT", 10, 2, "interest_amount"),
        parse_decimal_col("PMT_ESCROW_AMT", 10, 2, "escrow_amount"),
        parse_decimal_col("PMT_LATE_FEE", 10, 2, "late_fee"),
        expand_status("PMT_TYP_CD", PAYMENT_TYPE_MAP, "type"),
        expand_status("PMT_STAT_CD", PAYMENT_STATUS_MAP, "status"),
        parse_date_col("PMT_RECV_DT", "received_date"),
        parse_date_col("PMT_PROC_DT", "processed_date"),
        parse_timestamp_col("PMT_CRET_DT", "created_at"),
        parse_timestamp_col("PMT_UPDT_DT", "updated_at"),
    )

    # ----- Join to resolve loan FK -----
    joined_df = transformed_df.join(loans_df, on="_acct_nbr", how="left")

    unresolved_loans = joined_df.filter(col("_loan_pk").isNull()).count()
    if unresolved_loans > 0:
        logger.warning("%d payments have unresolved loan account FK", unresolved_loans)

    # Derive partition column
    final_df = joined_df.select(
        col("legacy_payment_id"),
        col("_loan_pk").alias("loan_account_id"),
        col("payment_date"),
        col("total_amount"),
        col("principal_amount"),
        col("interest_amount"),
        col("escrow_amount"),
        col("late_fee"),
        col("type"),
        col("status"),
        col("received_date"),
        col("processed_date"),
        col("created_at"),
        col("updated_at"),
        year(col("payment_date")).alias("payment_year"),
    )

    # ----- Null / quality checks -----
    required_cols = ["loan_account_id", "payment_date", "total_amount", "type", "status"]
    valid_df, quarantined_df = quarantine_nulls(final_df, required_cols, "payments")

    quarantine_count = quarantined_df.count()
    if quarantine_count > 0:
        logger.warning("Quarantining %d rows -> %s", quarantine_count, quarantine_path)
        quarantined_df.write.mode("append").format("delta").save(quarantine_path)

    target_count = valid_df.count()
    logger.info("Writing %d payment records to %s", target_count, target_table)

    valid_df.write.format("delta").mode("overwrite").option(
        "mergeSchema", "true"
    ).partitionBy("payment_year").saveAsTable(target_table)

    stats = {
        "source_count": source_count,
        "target_count": target_count,
        "quarantine_count": quarantine_count,
        "unresolved_loan_fk": unresolved_loans,
    }
    logger.info("Payment ingestion complete: %s", stats)
    return stats


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Ingest CDW_PMT_HIST into Delta Lake")
    parser.add_argument("--source", default=DEFAULT_SOURCE_PATH)
    parser.add_argument("--target", default=DEFAULT_TARGET_TABLE)
    parser.add_argument("--quarantine", default=DEFAULT_QUARANTINE_PATH)
    parser.add_argument("--loan-table", default=DEFAULT_LOAN_TABLE)
    parser.add_argument("--format", default=SOURCE_FORMAT, choices=["csv", "parquet"])
    args = parser.parse_args()

    spark = get_or_create_spark("IngestPayments")
    try:
        result = ingest_payments(
            spark, args.source, args.target, args.quarantine, args.loan_table, args.format
        )
        logger.info("Final stats: %s", result)
    except Exception as e:
        logger.error("Payment ingestion FAILED: %s", e, exc_info=True)
        sys.exit(1)
    finally:
        spark.stop()
