"""
Ingestion script: CDW_BORR_MSTR -> loan_warehouse.borrowers (Delta Lake)

Reads legacy borrower data from CSV/Parquet source files, applies type
conversions and status expansions per column_mappings.md, and writes to
the borrowers Delta table.

Usage (Databricks notebook or spark-submit):
    %run ./common
    %run ./ingest_borrowers
    -- or --
    spark-submit --packages io.delta:delta-spark_2.12:3.1.0 ingest_borrowers.py \
        --source /mnt/landing/cdw_borr_mstr/ \
        --target loan_warehouse.borrowers
"""

import argparse
import logging
import sys

from pyspark.sql import SparkSession
from pyspark.sql.functions import col, current_timestamp, lit

from common import (
    BORROWER_STATUS_MAP,
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

# ---------------------------------------------------------------------------
# Configuration defaults
# ---------------------------------------------------------------------------
DEFAULT_SOURCE_PATH = "/mnt/landing/cdw_borr_mstr/"
DEFAULT_TARGET_TABLE = "loan_warehouse.borrowers"
DEFAULT_QUARANTINE_PATH = "/mnt/quarantine/borrowers/"
SOURCE_FORMAT = "csv"  # Change to "parquet" if source files are Parquet


def ingest_borrowers(
    spark: SparkSession,
    source_path: str = DEFAULT_SOURCE_PATH,
    target_table: str = DEFAULT_TARGET_TABLE,
    quarantine_path: str = DEFAULT_QUARANTINE_PATH,
    source_format: str = SOURCE_FORMAT,
) -> dict:
    """Run the borrower ingestion pipeline. Returns a stats dict."""

    logger.info("Reading legacy borrower data from %s (format=%s)", source_path, source_format)

    reader = spark.read.format(source_format)
    if source_format == "csv":
        reader = reader.option("header", "true").option("inferSchema", "false")

    raw_df = reader.load(source_path)
    source_count = raw_df.count()
    logger.info("Source row count: %d", source_count)

    # ----- Transformations -----
    transformed_df = raw_df.select(
        col("BORR_ID").alias("external_id"),
        col("BORR_FST_NM").alias("first_name"),
        col("BORR_LST_NM").alias("last_name"),
        col("BORR_MID_INIT").alias("middle_initial"),
        col("BORR_SSN_ENCR").alias("ssn_hash"),
        parse_date_col("BORR_DOB_DT", "date_of_birth"),
        col("BORR_ADDR_LN1").alias("address_line1"),
        col("BORR_ADDR_LN2").alias("address_line2"),
        col("BORR_CTY_NM").alias("city"),
        col("BORR_ST_CD").alias("state"),
        col("BORR_ZIP_CD").alias("zip_code"),
        col("BORR_PH_NBR").alias("phone"),
        col("BORR_EMAIL_ADDR").alias("email"),
        parse_int_col("BORR_CRDT_SCR", "credit_score"),
        col("BORR_EMP_STAT").alias("employment_status"),
        parse_decimal_col("BORR_ANN_INCM", 12, 2, "annual_income"),
        expand_status("BORR_STAT_CD", BORROWER_STATUS_MAP, "status"),
        parse_timestamp_col("BORR_CRET_DT", "created_at"),
        parse_timestamp_col("BORR_UPDT_DT", "updated_at"),
    )

    # ----- Null / quality checks -----
    required_cols = ["external_id", "first_name", "last_name"]
    valid_df, quarantined_df = quarantine_nulls(transformed_df, required_cols, "borrowers")

    quarantine_count = quarantined_df.count()
    if quarantine_count > 0:
        logger.warning(
            "Quarantining %d rows with NULL required fields -> %s",
            quarantine_count,
            quarantine_path,
        )
        quarantined_df.write.mode("append").format("delta").save(quarantine_path)

    # ----- Write to target Delta table -----
    target_count = valid_df.count()
    logger.info("Writing %d valid borrower records to %s", target_count, target_table)

    valid_df.write.format("delta").mode("overwrite").option(
        "mergeSchema", "true"
    ).saveAsTable(target_table)

    stats = {
        "source_count": source_count,
        "target_count": target_count,
        "quarantine_count": quarantine_count,
    }
    logger.info("Borrower ingestion complete: %s", stats)
    return stats


# ---------------------------------------------------------------------------
# CLI entrypoint
# ---------------------------------------------------------------------------

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Ingest CDW_BORR_MSTR into Delta Lake")
    parser.add_argument("--source", default=DEFAULT_SOURCE_PATH, help="Source file path")
    parser.add_argument("--target", default=DEFAULT_TARGET_TABLE, help="Target Delta table")
    parser.add_argument("--quarantine", default=DEFAULT_QUARANTINE_PATH, help="Quarantine path")
    parser.add_argument("--format", default=SOURCE_FORMAT, choices=["csv", "parquet"], help="Source format")
    args = parser.parse_args()

    spark = get_or_create_spark("IngestBorrowers")
    try:
        result = ingest_borrowers(spark, args.source, args.target, args.quarantine, args.format)
        logger.info("Final stats: %s", result)
    except Exception as e:
        logger.error("Borrower ingestion FAILED: %s", e, exc_info=True)
        sys.exit(1)
    finally:
        spark.stop()
