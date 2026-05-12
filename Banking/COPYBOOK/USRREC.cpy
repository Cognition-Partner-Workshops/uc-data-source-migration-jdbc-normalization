      *================================================================*
      *  USRREC - USER MASTER FILE RECORD LAYOUT                      *
      *  FILE: USERFILE (VSAM KSDS)                                   *
      *  KEY:  USER-ID (BYTES 1-8)                                    *
      *  RECL: 99 BYTES                                               *
      *================================================================*
      *
       01  USER-MASTER-RECORD.
           05  UM-USER-ID         PIC X(08).
           05  UM-PASSWORD        PIC X(08).
           05  UM-USER-NAME       PIC X(30).
           05  UM-USER-ROLE       PIC X(10).
               88  UM-ROLE-ADMIN              VALUE 'ADMIN     '.
               88  UM-ROLE-TELLER             VALUE 'TELLER    '.
               88  UM-ROLE-MANAGER            VALUE 'MANAGER   '.
               88  UM-ROLE-CUSTOMER           VALUE 'CUSTOMER  '.
           05  UM-STATUS          PIC X(01).
               88  UM-ACTIVE                  VALUE 'A'.
               88  UM-LOCKED                  VALUE 'L'.
               88  UM-DISABLED                VALUE 'D'.
           05  UM-LOGIN-ATTEMPTS  PIC 9(02).
           05  UM-LAST-LOGIN.
               10  UM-LAST-DATE   PIC X(10).
               10  UM-LAST-TIME   PIC X(08).
           05  UM-CREATED-DATE    PIC X(10).
           05  UM-FILLER          PIC X(12).
      *
