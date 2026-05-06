"""
Ingestion script: CDW_LN_PROD -> loan_warehouse.loan_products (Delta Lake)

Reads legacy loan product data from CSV/Parquet source files, applies type
conversions, and writes to the loan_products Delta table.
"""

import argparse
import logging
import sys

from pyspark.sql import SparkSession
from pyspark.sql.functions import col

from common import (
    PRODUCT_STATUS_MAP,
    expand_status_bool,
    get_or_create_spark,
    parse_date_col,
    parse_decimal_col,
    parse_int_col,
    quarantine_nulls,
)

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger(__name__)

DEFAULT_SOURCE_PATH = "/mnt/landing/cdw_ln_prod/"
DEFAULT_TARGET_TABLE = "loan_warehouse.loan_products"
DEFAULT_QUARANTINE_PATH = "/mnt/quarantine/loan_products/"
SOURCE_FORMAT = "csv"


def ingest_loan_products(
    spark: SparkSession,
    source_path: str = DEFAULT_SOURCE_PATH,
    target_table: str = DEFAULT_TARGET_TABLE,
    quarantine_path: str = DEFAULT_QUARANTINE_PATH,
    source_format: str = SOURCE_FORMAT,
) -> dict:
    """Run the loan products ingestion pipeline."""

    logger.info("Reading legacy loan product data from %s", source_path)

    reader = spark.read.format(source_format)
    if source_format == "csv":
        reader = reader.option("header", "true").option("inferSchema", "false")

    raw_df = reader.load(source_path)
    source_count = raw_df.count()
    logger.info("Source row count: %d", source_count)

    transformed_df = raw_df.select(
        col("PROD_CD").alias("code"),
        col("PROD_DESC_TXT").alias("name"),
        col("PROD_TYP_CD").alias("type"),
        parse_int_col("PROD_TERM_MOS", "term_months"),
        col("PROD_RT_TYP").alias("rate_type"),
        parse_decimal_col("PROD_MIN_AMT", 12, 2, "min_amount"),
        parse_decimal_col("PROD_MAX_AMT", 12, 2, "max_amount"),
        expand_status_bool("PROD_STAT_CD", PRODUCT_STATUS_MAP, "is_active"),
        parse_date_col("PROD_EFF_DT", "effective_date"),
        parse_date_col("PROD_EXP_DT", "expiration_date"),
    )

    required_cols = ["code", "name", "type", "term_months", "rate_type"]
    valid_df, quarantined_df = quarantine_nulls(transformed_df, required_cols, "loan_products")

    quarantine_count = quarantined_df.count()
    if quarantine_count > 0:
        logger.warning("Quarantining %d rows -> %s", quarantine_count, quarantine_path)
        quarantined_df.write.mode("append").format("delta").save(quarantine_path)

    target_count = valid_df.count()
    logger.info("Writing %d loan product records to %s", target_count, target_table)

    valid_df.write.format("delta").mode("overwrite").option(
        "mergeSchema", "true"
    ).saveAsTable(target_table)

    stats = {
        "source_count": source_count,
        "target_count": target_count,
        "quarantine_count": quarantine_count,
    }
    logger.info("Loan product ingestion complete: %s", stats)
    return stats


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Ingest CDW_LN_PROD into Delta Lake")
    parser.add_argument("--source", default=DEFAULT_SOURCE_PATH)
    parser.add_argument("--target", default=DEFAULT_TARGET_TABLE)
    parser.add_argument("--quarantine", default=DEFAULT_QUARANTINE_PATH)
    parser.add_argument("--format", default=SOURCE_FORMAT, choices=["csv", "parquet"])
    args = parser.parse_args()

    spark = get_or_create_spark("IngestLoanProducts")
    try:
        result = ingest_loan_products(spark, args.source, args.target, args.quarantine, args.format)
        logger.info("Final stats: %s", result)
    except Exception as e:
        logger.error("Loan product ingestion FAILED: %s", e, exc_info=True)
        sys.exit(1)
    finally:
        spark.stop()
