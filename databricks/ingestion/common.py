"""
Shared transformation utilities for the CDW-to-Delta-Lake migration pipeline.

All helper functions are pure Spark Column expressions or UDFs so they
can be used directly inside ``withColumn`` / ``select`` calls.
"""

from pyspark.sql import DataFrame, SparkSession
from pyspark.sql.functions import (
    coalesce,
    col,
    lit,
    regexp_replace,
    to_date,
    to_timestamp,
    trim,
    upper,
    when,
)
from pyspark.sql.types import DecimalType, IntegerType

# ---------------------------------------------------------------------------
# Date / timestamp parsing
# ---------------------------------------------------------------------------

DATE_FORMAT_LEGACY = "MM/dd/yyyy"


def parse_date_col(column_name: str, alias: str | None = None):
    """Return a Column expression that parses ``MM/DD/YYYY`` strings to DateType."""
    target = alias or column_name
    return to_date(trim(col(column_name)), DATE_FORMAT_LEGACY).alias(target)


def parse_timestamp_col(column_name: str, alias: str | None = None):
    """Return a Column expression that parses ``MM/DD/YYYY`` strings to TimestampType."""
    target = alias or column_name
    return to_timestamp(trim(col(column_name)), DATE_FORMAT_LEGACY).alias(target)


# ---------------------------------------------------------------------------
# Numeric parsing  (handles comma-formatted strings like "285,000")
# ---------------------------------------------------------------------------


def parse_decimal_col(column_name: str, precision: int, scale: int, alias: str | None = None):
    """Strip commas and cast to ``DecimalType(precision, scale)``."""
    target = alias or column_name
    return (
        regexp_replace(trim(col(column_name)), ",", "")
        .cast(DecimalType(precision, scale))
        .alias(target)
    )


def parse_int_col(column_name: str, alias: str | None = None):
    """Strip commas and cast to ``IntegerType``."""
    target = alias or column_name
    return (
        regexp_replace(trim(col(column_name)), ",", "")
        .cast(IntegerType())
        .alias(target)
    )


# ---------------------------------------------------------------------------
# Status / code expansion maps
# ---------------------------------------------------------------------------

LOAN_STATUS_MAP = {
    "ACT": "ACTIVE",
    "CLO": "CLOSED",
    "DFT": "DEFAULT",
    "FRB": "FORBEARANCE",
}

BORROWER_STATUS_MAP = {
    "ACT": "ACTIVE",
    "INA": "INACTIVE",
}

PRODUCT_STATUS_MAP = {
    "ACT": True,
    "INA": False,
}

PAYMENT_TYPE_MAP = {
    "REG": "REGULAR",
    "EXT": "EXTRA",
    "PRT": "PARTIAL",
    "PRE": "PREPAYMENT",
}

PAYMENT_STATUS_MAP = {
    "PST": "POSTED",
    "REV": "REVERSED",
    "NSF": "NSF",
    "PND": "PENDING",
}

PROPERTY_TYPE_MAP = {
    "SFR": "Single Family",
    "CND": "Condominium",
    "MFR": "Multi-Family",
    "TWN": "Townhouse",
}


def expand_status(column_name: str, mapping: dict, alias: str | None = None):
    """Build a ``CASE WHEN`` Column expression from *mapping*.

    Unrecognised codes are preserved as-is and logged by the caller.
    """
    target = alias or column_name
    trimmed = upper(trim(col(column_name)))
    expr = None
    for code, expanded in mapping.items():
        condition = when(trimmed == code, lit(expanded))
        expr = condition if expr is None else expr.when(trimmed == code, lit(expanded))
    return expr.otherwise(trimmed).alias(target)


def expand_status_bool(column_name: str, mapping: dict, alias: str | None = None):
    """Build a ``CASE WHEN`` Column expression that maps codes to boolean values."""
    target = alias or column_name
    trimmed = upper(trim(col(column_name)))
    expr = None
    for code, value in mapping.items():
        condition = when(trimmed == code, lit(value))
        expr = condition if expr is None else expr.when(trimmed == code, lit(value))
    return expr.otherwise(lit(None)).alias(target)


# ---------------------------------------------------------------------------
# Quarantine / error handling
# ---------------------------------------------------------------------------


def quarantine_nulls(df: DataFrame, required_cols: list[str], table_label: str) -> tuple[DataFrame, DataFrame]:
    """Split *df* into (valid, quarantined) based on nullability of *required_cols*.

    Quarantined rows get an extra ``_quarantine_reason`` column.
    """
    null_condition = None
    for c in required_cols:
        cond = col(c).isNull()
        null_condition = cond if null_condition is None else (null_condition | cond)

    if null_condition is None:
        return df, df.limit(0)

    valid = df.filter(~null_condition)
    quarantined = df.filter(null_condition).withColumn(
        "_quarantine_reason",
        lit(f"[{table_label}] NULL in required column(s): {', '.join(required_cols)}"),
    )
    return valid, quarantined


# ---------------------------------------------------------------------------
# Spark session helper (for local / notebook testing)
# ---------------------------------------------------------------------------


def get_or_create_spark(app_name: str = "LoanMigration") -> SparkSession:
    """Return the active SparkSession or create a new one (for notebooks / tests)."""
    return (
        SparkSession.builder
        .appName(app_name)
        .config("spark.sql.extensions", "io.delta.sql.DeltaSparkSessionExtension")
        .config(
            "spark.sql.catalog.spark_catalog",
            "org.apache.spark.sql.delta.catalog.DeltaCatalog",
        )
        .getOrCreate()
    )
