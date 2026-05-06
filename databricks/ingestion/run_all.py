"""
Orchestrator: run the full CDW-to-Delta-Lake ingestion pipeline in the
correct dependency order.

    1. borrowers       (no dependencies)
    2. loan_products   (no dependencies)
    3. loan_accounts   (depends on borrowers + loan_products for FK resolution)
    4. payments        (depends on loan_accounts for FK resolution)

Usage:
    spark-submit --packages io.delta:delta-spark_2.12:3.1.0 run_all.py
"""

import logging
import sys

from common import get_or_create_spark
from ingest_borrowers import ingest_borrowers
from ingest_loan_accounts import ingest_loan_accounts
from ingest_loan_products import ingest_loan_products
from ingest_payments import ingest_payments

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger("migration-orchestrator")

PIPELINE_STEPS = [
    ("borrowers", ingest_borrowers),
    ("loan_products", ingest_loan_products),
    ("loan_accounts", ingest_loan_accounts),
    ("payments", ingest_payments),
]


def run_pipeline():
    spark = get_or_create_spark("CDW_Migration_Full")
    all_stats = {}
    failed = False

    for step_name, step_fn in PIPELINE_STEPS:
        logger.info("=" * 60)
        logger.info("STARTING STEP: %s", step_name)
        logger.info("=" * 60)
        try:
            stats = step_fn(spark)
            all_stats[step_name] = stats
            logger.info("COMPLETED STEP: %s -> %s", step_name, stats)
        except Exception as e:
            logger.error("FAILED STEP: %s -> %s", step_name, e, exc_info=True)
            all_stats[step_name] = {"error": str(e)}
            failed = True
            break

    logger.info("=" * 60)
    logger.info("PIPELINE SUMMARY")
    logger.info("=" * 60)
    for name, stats in all_stats.items():
        logger.info("  %-20s %s", name, stats)

    if failed:
        logger.error("Pipeline completed with ERRORS")
        sys.exit(1)
    else:
        logger.info("Pipeline completed SUCCESSFULLY")

    spark.stop()


if __name__ == "__main__":
    run_pipeline()
