/** Legacy CDW_BORR_MSTR table row */
export interface LegacyBorrower {
  BORR_ID: string;
  BORR_FST_NM: string;
  BORR_LST_NM: string;
  BORR_MID_INIT: string | null;
  BORR_SSN_ENCR: string | null;
  BORR_DOB_DT: string | null;
  BORR_ADDR_LN1: string | null;
  BORR_ADDR_LN2: string | null;
  BORR_CTY_NM: string | null;
  BORR_ST_CD: string | null;
  BORR_ZIP_CD: string | null;
  BORR_PH_NBR: string | null;
  BORR_EMAIL_ADDR: string | null;
  BORR_CRDT_SCR: string | null;
  BORR_EMP_STAT: string | null;
  BORR_ANN_INCM: string | null;
  BORR_CRET_DT: string | null;
  BORR_UPDT_DT: string | null;
  BORR_STAT_CD: string | null;
  BORR_REC_TYP: string | null;
}

/** Legacy CDW_LN_PROD table row */
export interface LegacyLoanProduct {
  PROD_CD: string;
  PROD_DESC_TXT: string | null;
  PROD_TYP_CD: string | null;
  PROD_TERM_MOS: string | null;
  PROD_RT_TYP: string | null;
  PROD_MIN_AMT: string | null;
  PROD_MAX_AMT: string | null;
  PROD_STAT_CD: string | null;
  PROD_EFF_DT: string | null;
  PROD_EXP_DT: string | null;
}

/** Legacy CDW_LN_ACCT table row */
export interface LegacyLoanAccount {
  LN_ACCT_NBR: string;
  BORR_ID: string;
  BORR_FST_NM: string | null;
  BORR_LST_NM: string | null;
  BORR_SSN_LST4: string | null;
  PROD_CD: string;
  LN_ORIG_AMT: string | null;
  LN_CURR_BAL: string | null;
  LN_INT_RT: string | null;
  LN_TERM_MOS: string | null;
  LN_PMT_AMT: string | null;
  LN_ORIG_DT: string | null;
  LN_MAT_DT: string | null;
  LN_1ST_PMT_DT: string | null;
  LN_NXT_PMT_DT: string | null;
  LN_STAT_CD: string | null;
  LN_DLQ_DAYS: string | null;
  LN_ESCROW_BAL: string | null;
  LN_LTV_PCT: string | null;
  PROP_ADDR_LN1: string | null;
  PROP_CTY_NM: string | null;
  PROP_ST_CD: string | null;
  PROP_ZIP_CD: string | null;
  PROP_TYP_CD: string | null;
  PROP_APRS_VAL: string | null;
  LN_CRET_DT: string | null;
  LN_UPDT_DT: string | null;
}

/** Legacy CDW_PMT_HIST table row */
export interface LegacyPayment {
  PMT_SEQ_NBR: string;
  LN_ACCT_NBR: string;
  PMT_DT: string | null;
  PMT_AMT: string | null;
  PMT_PRIN_AMT: string | null;
  PMT_INT_AMT: string | null;
  PMT_ESCROW_AMT: string | null;
  PMT_LATE_FEE: string | null;
  PMT_TYP_CD: string | null;
  PMT_STAT_CD: string | null;
  PMT_RECV_DT: string | null;
  PMT_PROC_DT: string | null;
  PMT_CRET_DT: string | null;
  PMT_UPDT_DT: string | null;
}
