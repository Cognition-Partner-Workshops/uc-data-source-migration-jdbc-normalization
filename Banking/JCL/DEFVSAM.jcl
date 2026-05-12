//DEFVSAM  JOB (BANKING),'DEFINE VSAM FILES',
//         CLASS=A,MSGCLASS=X,MSGLEVEL=(1,1),
//         NOTIFY=&SYSUID
//*================================================================*
//*  JOB:  DEFVSAM                                                 *
//*  PURPOSE: DEFINE VSAM KSDS FILE FOR USER MASTER                *
//*================================================================*
//*
//STEP010  EXEC PGM=IDCAMS
//SYSPRINT DD SYSOUT=*
//SYSIN    DD *
  DELETE BANKING.USER.MASTER -
         CLUSTER -
         PURGE
  SET MAXCC = 0
  DEFINE CLUSTER -
         (NAME(BANKING.USER.MASTER) -
          INDEXED -
          RECSZ(99 99) -
          KEYS(8 0) -
          CISZ(4096) -
          FREESPACE(20 10) -
          SHAREOPTIONS(2 3)) -
         DATA -
         (NAME(BANKING.USER.MASTER.DATA) -
          CYLINDERS(5 2)) -
         INDEX -
         (NAME(BANKING.USER.MASTER.INDEX) -
          TRACKS(3 1))
  REPRO INFILE(SEEDIN) -
        OUTDATASET(BANKING.USER.MASTER)
/*
//*
//*================================================================*
//*  SEED DATA FOR USER MASTER FILE                                *
//*================================================================*
//SEEDIN   DD *
ADMIN001PASS1234SYSTEM ADMINISTRATOR            ADMIN     A00          00
TELLER01TELL1234JOHN SMITH - BRANCH TELLER      TELLER    A00          00
MANAGER1MGMT1234JANE DOE - BRANCH MANAGER       MANAGER   A00          00
CUSTMR01CUST1234ROBERT JOHNSON - CUSTOMER       CUSTOMER  A00          00
/*
//
