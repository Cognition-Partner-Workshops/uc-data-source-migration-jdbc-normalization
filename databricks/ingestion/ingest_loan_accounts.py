"""
Ingestion script: CDW_LN_ACCT -> loan_warehouse.loan_accounts (Delta Lake)

Reads the denormalized legacy loan account table, drops redundant borrower
columns, resolves foreign keys to borrowers/loan_products tables, expands
status codes, and writes to the loan_accounts Delta table.
"""

import argparse
import logging
import sys

from pyspark.sql import SparkSession
from pyspark.sql.functions import col, lit

from common import (
    LOAN_STATUS_MAP,
    PROPERTY_TYPE_MAP,
    expand_status,
    get_or_create_spark,
    parse_date_col,
    parse_decimal_col,
    parse_int_col,
    parse_timestamp_col,
    quarantine_nulls,
)

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger(__name__)

DEFAULT_SOURCE_PATH = "/mnt/landing/cdw_ln_acct/"
DEFAULT_TARGET_TABLE = "loan_warehouse.loan_accounts"
DEFAULT_QUARANTINE_PATH = "/mnt/quarantine/loan_accounts/"
DEFAULT_BORROWER_TABLE = "loan_warehouse.borrowers"
DEFAULT_PRODUCT_TABLE = "loan_warehouse.loan_products"
SOURCE_FORMAT = "csv"


def ingest_loan_accounts(
    spark: SparkSession,
    source_path: str = DEFAULT_SOURCE_PATH,
    target_table: str = DEFAULT_TARGET_TABLE,
    quarantine_path: str = DEFAULT_QUARANTINE_PATH,
    borrower_table: str = DEFAULT_BORROWER_TABLE,
    product_table: str = DEFAULT_PRODUCT_TABLE,
    source_format: str = SOURCE_FORMAT,
) -> dict:
    """Run the loan accounts ingestion pipeline."""

    logger.info("Reading legacy loan account data from %s", source_path)

    reader = spark.read.format(source_format)
    if source_format == "csv":
        reader = reader.option("header", "true").option("inferSchema", "false")

    raw_df = reader.load(source_path)
    source_count = raw_df.count()
    logger.info("Source row count: %d", source_count)

    # ----- FK Resolution: borrower_id -----
    borrowers_df = spark.table(borrower_table).select(
        col("id").alias("_borrower_pk"),
        col("external_id").alias("_borr_ext_id"),
    )

    # ----- FK Resolution: product_id -----
    products_df = spark.table(product_table).select(
        col("id").alias("_product_pk"),
        col("code").alias("_prod_code"),
    )

    # ----- Core transformations (drop denormalized borrower fields) -----
    transformed_df = raw_df.select(
        col("LN_ACCT_NBR").alias("account_number"),
        col("BORR_ID").alias("_borr_ext_id"),
        col("PROD_CD").alias("_prod_code"),
        parse_decimal_col("LN_ORIG_AMT", 12, 2, "original_amount"),
        parse_decimal_col("LN_CURR_BAL", 12, 2, "current_balance"),
        parse_decimal_col("LN_INT_RT", 5, 3, "interest_rate"),
        parse_int_col("LN_TERM_MOS", "term_months"),
        parse_decimal_col("LN_PMT_AMT", 10, 2, "monthly_payment"),
        parse_date_col("LN_ORIG_DT", "origination_date"),
        parse_date_col("LN_MAT_DT", "maturity_date"),
        parse_date_col("LN_1ST_PMT_DT", "first_payment_date"),
        parse_date_col("LN_NXT_PMT_DT", "next_payment_date"),
        expand_status("LN_STAT_CD", LOAN_STATUS_MAP, "status"),
        parse_int_col("LN_DLQ_DAYS", "delinquency_days"),
        parse_decimal_col("LN_ESCROW_BAL", 10, 2, "escrow_balance"),
        parse_decimal_col("LN_LTV_PCT", 5, 2, "ltv_percent"),
        col("PROP_ADDR_LN1").alias("property_address"),
        col("PROP_CTY_NM").alias("property_city"),
        col("PROP_ST_CD").alias("property_state"),
        col("PROP_ZIP_CD").alias("property_zip"),
        expand_status("PROP_TYP_CD", PROPERTY_TYPE_MAP, "property_type"),
        parse_decimal_col("PROP_APRS_VAL", 12, 2, "appraised_value"),
        parse_timestamp_col("LN_CRET_DT", "created_at"),
        parse_timestamp_col("LN_UPDT_DT", "updated_at"),
    )

    # ----- Join to resolve borrower FK -----
    joined_df = transformed_df.join(borrowers_df, on="_borr_ext_id", how="left")

    # ----- Join to resolve product FK -----
    joined_df = joined_df.join(products_df, on="_prod_code", how="left")

    # Log unresolved FKs
    unresolved_borr = joined_df.filter(col("_borrower_pk").isNull()).count()
    unresolved_prod = joined_df.filter(col("_product_pk").isNull()).count()
    if unresolved_borr > 0:
        logger.warning("%d loan accounts have unresolved borrower FK", unresolved_borr)
    if unresolved_prod > 0:
        logger.warning("%d loan accounts have unresolved product FK", unresolved_prod)

    final_df = joined_df.select(
        col("account_number"),
        col("_borrower_pk").alias("borrower_id"),
        col("_product_pk").alias("product_id"),
        col("original_amount"),
        col("current_balance"),
        col("interest_rate"),
        col("term_months"),
        col("monthly_payment"),
        col("origination_date"),
        col("maturity_date"),
        col("first_payment_date"),
        col("next_payment_date"),
        col("status"),
        col("delinquency_days"),
        col("escrow_balance"),
        col("ltv_percent"),
        col("property_address"),
        col("property_city"),
        col("property_state"),
        col("property_zip"),
        col("property_type"),
        col("appraised_value"),
        col("created_at"),
        col("updated_at"),
    )

    # ----- Null / quality checks -----
    required_cols = ["account_number", "borrower_id", "product_id", "original_amount", "current_balance"]
    valid_df, quarantined_df = quarantine_nulls(final_df, required_cols, "loan_accounts")

    quarantine_count = quarantined_df.count()
    if quarantine_count > 0:
        logger.warning("Quarantining %d rows -> %s", quarantine_count, quarantine_path)
        quarantined_df.write.mode("append").format("delta").save(quarantine_path)

    target_count = valid_df.count()
    logger.info("Writing %d loan account records to %s", target_count, target_table)

    valid_df.write.format("delta").mode("overwrite").option(
        "mergeSchema", "true"
    ).partitionBy("status").saveAsTable(target_table)

    stats = {
        "source_count": source_count,
        "target_count": target_count,
        "quarantine_count": quarantine_count,
        "unresolved_borrower_fk": unresolved_borr,
        "unresolved_product_fk": unresolved_prod,
    }
    logger.info("Loan account ingestion complete: %s", stats)
    return stats


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Ingest CDW_LN_ACCT into Delta Lake")
    parser.add_argument("--source", default=DEFAULT_SOURCE_PATH)
    parser.add_argument("--target", default=DEFAULT_TARGET_TABLE)
    parser.add_argument("--quarantine", default=DEFAULT_QUARANTINE_PATH)
    parser.add_argument("--borrower-table", default=DEFAULT_BORROWER_TABLE)
    parser.add_argument("--product-table", default=DEFAULT_PRODUCT_TABLE)
    parser.add_argument("--format", default=SOURCE_FORMAT, choices=["csv", "parquet"])
    args = parser.parse_args()

    spark = get_or_create_spark("IngestLoanAccounts")
    try:
        result = ingest_loan_accounts(
            spark, args.source, args.target, args.quarantine,
            args.borrower_table, args.product_table, args.format,
        )
        logger.info("Final stats: %s", result)
    except Exception as e:
        logger.error("Loan account ingestion FAILED: %s", e, exc_info=True)
        sys.exit(1)
    finally:
        spark.stop()
