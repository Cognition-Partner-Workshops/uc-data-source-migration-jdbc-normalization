//COMPILE  JOB (BANKING),'COMPILE COBOL PROGS',
//         CLASS=A,MSGCLASS=X,MSGLEVEL=(1,1),
//         NOTIFY=&SYSUID
//*================================================================*
//*  JOB:  COMPILE                                                 *
//*  PURPOSE: COMPILE AND LINK BANKING COBOL PROGRAMS              *
//*================================================================*
//*
//*================================================================*
//*  STEP 1: ASSEMBLE BMS MAP                                      *
//*================================================================*
//ASMMAP   EXEC PGM=DFHMAPS,
//         PARM='SYSPARM(MAP)'
//SYSPRINT DD SYSOUT=*
//SYSPUNCH DD DSN=BANKING.LOAD.MAPS(LOGINM),DISP=SHR
//SYSLIB   DD DSN=CICS.SDFHMAC,DISP=SHR
//SYSIN    DD DSN=BANKING.SOURCE.BMS(LOGINM),DISP=SHR
//*
//*================================================================*
//*  STEP 2: COMPILE COBOL PROGRAM                                 *
//*================================================================*
//COBOL    EXEC PGM=IGYCRCTL,
//         PARM='LIB,APOST,RENT,NODYNAM'
//SYSPRINT DD SYSOUT=*
//SYSLIB   DD DSN=BANKING.SOURCE.COPYBOOK,DISP=SHR
//         DD DSN=CICS.SDFHCOB,DISP=SHR
//SYSLIN   DD DSN=&&OBJMOD,DISP=(NEW,PASS),
//         SPACE=(CYL,(1,1)),UNIT=SYSDA
//SYSIN    DD DSN=BANKING.SOURCE.COBOL(LOGINP),DISP=SHR
//*
//*================================================================*
//*  STEP 3: LINK-EDIT                                             *
//*================================================================*
//LKED     EXEC PGM=IEWL,
//         PARM='LIST,XREF,LET,RENT'
//SYSPRINT DD SYSOUT=*
//SYSLIB   DD DSN=CICS.SDFHLOAD,DISP=SHR
//         DD DSN=CEE.SCEELKED,DISP=SHR
//SYSLIN   DD DSN=&&OBJMOD,DISP=(OLD,DELETE)
//         DD *
  INCLUDE SYSLIB(DFHELII)
  NAME LOGINP(R)
/*
//SYSLMOD  DD DSN=BANKING.LOAD.PROGRAMS,DISP=SHR
//
