       IDENTIFICATION DIVISION.
       PROGRAM-ID.    LOGINP.
       AUTHOR.        BANKING-SYSTEM.
       DATE-WRITTEN.  2026-05-12.
      *================================================================*
      *  PROGRAM:  LOGINP                                              *
      *  PURPOSE:  BANKING APPLICATION LOGIN SCREEN HANDLER            *
      *  CICS TRANSACTION: BLOG                                        *
      *  BMS MAP: LOGINM / LOGINI                                      *
      *                                                                *
      *  DESCRIPTION:                                                  *
      *    THIS PROGRAM HANDLES THE LOGIN SCREEN FOR THE BANKING       *
      *    APPLICATION. IT VALIDATES USER CREDENTIALS AGAINST THE      *
      *    USER MASTER FILE (USERFILE) AND MANAGES THE LOGIN FLOW.     *
      *                                                                *
      *  FLOW:                                                         *
      *    1. FIRST TIME - SEND BLANK LOGIN MAP                        *
      *    2. USER ENTERS USERID AND PASSWORD                          *
      *    3. VALIDATE CREDENTIALS AGAINST USERFILE (VSAM KSDS)       *
      *    4. IF VALID   - TRANSFER TO MAIN MENU (MENUP)              *
      *    5. IF INVALID - DISPLAY ERROR AND ALLOW RETRY               *
      *    6. PF3 - EXIT APPLICATION                                   *
      *================================================================*
      *
       ENVIRONMENT DIVISION.
      *
       DATA DIVISION.
      *
       WORKING-STORAGE SECTION.
      *
       01  WS-PROGRAM-FIELDS.
           05  WS-RESP-CODE        PIC S9(08) COMP VALUE +0.
           05  WS-COMMAREA-LEN     PIC S9(04) COMP VALUE +0.
           05  WS-MAP-NAME         PIC X(08)  VALUE 'LOGINI'.
           05  WS-MAPSET-NAME      PIC X(08)  VALUE 'LOGINM'.
           05  WS-TRAN-ID          PIC X(04)  VALUE 'BLOG'.
           05  WS-MENU-PROG        PIC X(08)  VALUE 'MENUP'.
           05  WS-FIRST-TIME-FLAG  PIC X(01)  VALUE 'Y'.
               88  FIRST-TIME                  VALUE 'Y'.
               88  NOT-FIRST-TIME              VALUE 'N'.
      *
       01  WS-DATE-TIME-FIELDS.
           05  WS-CURRENT-DATE.
               10  WS-DATE-YYYY   PIC 9(04).
               10  WS-DATE-MM     PIC 9(02).
               10  WS-DATE-DD     PIC 9(02).
           05  WS-CURRENT-TIME.
               10  WS-TIME-HH     PIC 9(02).
               10  WS-TIME-MM     PIC 9(02).
               10  WS-TIME-SS     PIC 9(02).
           05  WS-DISPLAY-DATE    PIC X(10)  VALUE SPACES.
           05  WS-DISPLAY-TIME    PIC X(08)  VALUE SPACES.
      *
       01  WS-USER-RECORD.
           05  WS-USER-ID         PIC X(08).
           05  WS-USER-PASSWORD   PIC X(08).
           05  WS-USER-NAME       PIC X(30).
           05  WS-USER-ROLE       PIC X(10).
           05  WS-USER-STATUS     PIC X(01).
               88  USER-ACTIVE                 VALUE 'A'.
               88  USER-LOCKED                 VALUE 'L'.
               88  USER-DISABLED               VALUE 'D'.
           05  WS-LOGIN-ATTEMPTS  PIC 9(02).
           05  WS-LAST-LOGIN-DATE PIC X(10).
           05  WS-LAST-LOGIN-TIME PIC X(08).
      *
       01  WS-VALIDATION-FIELDS.
           05  WS-VALID-USER-FLAG PIC X(01)  VALUE 'N'.
               88  VALID-USER                  VALUE 'Y'.
               88  INVALID-USER                VALUE 'N'.
           05  WS-MAX-ATTEMPTS    PIC 9(02)  VALUE 03.
           05  WS-ATTEMPT-COUNT   PIC 9(02)  VALUE 00.
      *
       01  WS-ERROR-MESSAGES.
           05  WS-MSG-WELCOME     PIC X(40) VALUE
               'ENTER YOUR USER ID AND PASSWORD         '.
           05  WS-MSG-INVALID     PIC X(40) VALUE
               'INVALID USER ID OR PASSWORD             '.
           05  WS-MSG-LOCKED      PIC X(40) VALUE
               'ACCOUNT IS LOCKED - CONTACT ADMIN       '.
           05  WS-MSG-DISABLED    PIC X(40) VALUE
               'ACCOUNT IS DISABLED - CONTACT ADMIN     '.
           05  WS-MSG-BLANK-USER  PIC X(40) VALUE
               'PLEASE ENTER YOUR USER ID               '.
           05  WS-MSG-BLANK-PASS  PIC X(40) VALUE
               'PLEASE ENTER YOUR PASSWORD              '.
           05  WS-MSG-SUCCESS     PIC X(40) VALUE
               'LOGIN SUCCESSFUL - LOADING MAIN MENU    '.
           05  WS-MSG-MAX-RETRY   PIC X(40) VALUE
               'MAX ATTEMPTS REACHED - ACCOUNT LOCKED   '.
           05  WS-MSG-GOODBYE     PIC X(40) VALUE
               'THANK YOU FOR USING BANKING SYSTEM      '.
           05  WS-MSG-FILE-ERR    PIC X(40) VALUE
               'SYSTEM ERROR - PLEASE CONTACT SUPPORT   '.
      *
       01  WS-COMMAREA.
           05  CA-USER-ID         PIC X(08).
           05  CA-USER-NAME       PIC X(30).
           05  CA-USER-ROLE       PIC X(10).
           05  CA-LOGIN-STATUS    PIC X(01).
               88  CA-LOGGED-IN                VALUE 'Y'.
               88  CA-NOT-LOGGED-IN            VALUE 'N'.
           05  CA-RETURN-TRAN     PIC X(04).
      *
      *----------------------------------------------------------------*
      *  BMS MAP COPYBOOK                                              *
      *----------------------------------------------------------------*
       COPY LOGINMI.
      *
       LINKAGE SECTION.
      *
       01  DFHCOMMAREA.
           05  LS-COMMAREA        PIC X(53).
      *
       PROCEDURE DIVISION.
      *
      *================================================================*
      *  MAIN PROCESSING LOGIC                                         *
      *================================================================*
       0000-MAIN-PROCESS.
      *
           PERFORM 1000-INITIALIZE
      *
           EVALUATE TRUE
               WHEN FIRST-TIME
                   PERFORM 2000-SEND-MAP
               WHEN NOT-FIRST-TIME
                   PERFORM 3000-RECEIVE-MAP
                   PERFORM 4000-PROCESS-INPUT
           END-EVALUATE
      *
           PERFORM 9000-RETURN-TRANSID
      *
           GOBACK.
      *
      *================================================================*
      *  1000 - INITIALIZE PROGRAM                                     *
      *================================================================*
       1000-INITIALIZE.
      *
           MOVE LOW-VALUES TO LOGINII
           MOVE LOW-VALUES TO LOGINIO
      *
           IF EIBCALEN > 0
               MOVE DFHCOMMAREA TO WS-COMMAREA
               SET NOT-FIRST-TIME TO TRUE
           ELSE
               SET FIRST-TIME TO TRUE
               INITIALIZE WS-COMMAREA
               SET CA-NOT-LOGGED-IN TO TRUE
           END-IF
      *
           PERFORM 1100-GET-DATE-TIME
      *
           .
      *
      *================================================================*
      *  1100 - GET CURRENT DATE AND TIME                              *
      *================================================================*
       1100-GET-DATE-TIME.
      *
           EXEC CICS ASKTIME
               ABSTIME(WS-CURRENT-DATE)
           END-EXEC
      *
           EXEC CICS FORMATTIME
               ABSTIME(WS-CURRENT-DATE)
               DATESEP('/')
               MMDDYYYY(WS-DISPLAY-DATE)
               TIME(WS-DISPLAY-TIME)
               TIMESEP(':')
           END-EXEC
      *
           .
      *
      *================================================================*
      *  2000 - SEND LOGIN MAP TO TERMINAL                             *
      *================================================================*
       2000-SEND-MAP.
      *
           MOVE WS-MSG-WELCOME  TO MSGO
           MOVE WS-DISPLAY-DATE TO DATEO
           MOVE WS-DISPLAY-TIME TO TIMEO
           MOVE SPACES           TO ERRMSGO
      *
           EXEC CICS SEND
               MAP(WS-MAP-NAME)
               MAPSET(WS-MAPSET-NAME)
               FROM(LOGINIO)
               ERASE
               CURSOR
           END-EXEC
      *
           .
      *
      *================================================================*
      *  3000 - RECEIVE MAP INPUT FROM TERMINAL                        *
      *================================================================*
       3000-RECEIVE-MAP.
      *
           EXEC CICS RECEIVE
               MAP(WS-MAP-NAME)
               MAPSET(WS-MAPSET-NAME)
               INTO(LOGINII)
               RESP(WS-RESP-CODE)
           END-EXEC
      *
           IF WS-RESP-CODE NOT = DFHRESP(NORMAL)
               MOVE WS-MSG-FILE-ERR TO MSGO
               PERFORM 2000-SEND-MAP
               PERFORM 9000-RETURN-TRANSID
           END-IF
      *
           .
      *
      *================================================================*
      *  4000 - PROCESS USER INPUT                                     *
      *================================================================*
       4000-PROCESS-INPUT.
      *
           EVALUATE EIBAID
               WHEN DFHENTER
                   PERFORM 5000-VALIDATE-LOGIN
               WHEN DFHPF3
                   PERFORM 8000-EXIT-PROGRAM
               WHEN OTHER
                   MOVE 'INVALID KEY PRESSED'  TO MSGO
                   PERFORM 2000-SEND-MAP
           END-EVALUATE
      *
           .
      *
      *================================================================*
      *  5000 - VALIDATE USER LOGIN CREDENTIALS                        *
      *================================================================*
       5000-VALIDATE-LOGIN.
      *
           IF USERIDI = SPACES OR USERIDI = LOW-VALUES
               MOVE WS-MSG-BLANK-USER TO MSGO
               PERFORM 2000-SEND-MAP
               PERFORM 9000-RETURN-TRANSID
           END-IF
      *
           IF PASSWDI = SPACES OR PASSWDI = LOW-VALUES
               MOVE WS-MSG-BLANK-PASS TO MSGO
               PERFORM 2000-SEND-MAP
               PERFORM 9000-RETURN-TRANSID
           END-IF
      *
           PERFORM 6000-READ-USER-FILE
      *
           IF VALID-USER
               PERFORM 7000-LOGIN-SUCCESS
           ELSE
               ADD 1 TO WS-ATTEMPT-COUNT
               IF WS-ATTEMPT-COUNT >= WS-MAX-ATTEMPTS
                   PERFORM 5100-LOCK-ACCOUNT
               ELSE
                   MOVE WS-MSG-INVALID TO MSGO
                   PERFORM 2000-SEND-MAP
               END-IF
           END-IF
      *
           .
      *
      *================================================================*
      *  5100 - LOCK ACCOUNT AFTER MAX ATTEMPTS                        *
      *================================================================*
       5100-LOCK-ACCOUNT.
      *
           MOVE 'L' TO WS-USER-STATUS
      *
           EXEC CICS READ
               FILE('USERFILE')
               INTO(WS-USER-RECORD)
               RIDFLD(USERIDI)
               UPDATE
               RESP(WS-RESP-CODE)
           END-EXEC
      *
           IF WS-RESP-CODE = DFHRESP(NORMAL)
               MOVE 'L' TO WS-USER-STATUS
               EXEC CICS REWRITE
                   FILE('USERFILE')
                   FROM(WS-USER-RECORD)
                   RESP(WS-RESP-CODE)
               END-EXEC
           END-IF
      *
           MOVE WS-MSG-MAX-RETRY TO MSGO
           PERFORM 2000-SEND-MAP
      *
           .
      *
      *================================================================*
      *  6000 - READ USER MASTER FILE (VSAM KSDS)                     *
      *================================================================*
       6000-READ-USER-FILE.
      *
           SET INVALID-USER TO TRUE
      *
           EXEC CICS READ
               FILE('USERFILE')
               INTO(WS-USER-RECORD)
               RIDFLD(USERIDI)
               RESP(WS-RESP-CODE)
           END-EXEC
      *
           EVALUATE WS-RESP-CODE
               WHEN DFHRESP(NORMAL)
                   PERFORM 6100-VALIDATE-PASSWORD
               WHEN DFHRESP(NOTFND)
                   SET INVALID-USER TO TRUE
               WHEN OTHER
                   MOVE WS-MSG-FILE-ERR TO MSGO
                   PERFORM 2000-SEND-MAP
                   PERFORM 9000-RETURN-TRANSID
           END-EVALUATE
      *
           .
      *
      *================================================================*
      *  6100 - VALIDATE PASSWORD AND ACCOUNT STATUS                   *
      *================================================================*
       6100-VALIDATE-PASSWORD.
      *
           IF USER-LOCKED
               MOVE WS-MSG-LOCKED TO MSGO
               PERFORM 2000-SEND-MAP
               PERFORM 9000-RETURN-TRANSID
           END-IF
      *
           IF USER-DISABLED
               MOVE WS-MSG-DISABLED TO MSGO
               PERFORM 2000-SEND-MAP
               PERFORM 9000-RETURN-TRANSID
           END-IF
      *
           IF WS-USER-PASSWORD = PASSWDI
               SET VALID-USER TO TRUE
           ELSE
               SET INVALID-USER TO TRUE
           END-IF
      *
           .
      *
      *================================================================*
      *  7000 - PROCESS SUCCESSFUL LOGIN                               *
      *================================================================*
       7000-LOGIN-SUCCESS.
      *
           MOVE USERIDI        TO CA-USER-ID
           MOVE WS-USER-NAME   TO CA-USER-NAME
           MOVE WS-USER-ROLE   TO CA-USER-ROLE
           SET  CA-LOGGED-IN   TO TRUE
           MOVE WS-TRAN-ID     TO CA-RETURN-TRAN
      *
           MOVE WS-DISPLAY-DATE TO WS-LAST-LOGIN-DATE
           MOVE WS-DISPLAY-TIME TO WS-LAST-LOGIN-TIME
      *
           EXEC CICS READ
               FILE('USERFILE')
               INTO(WS-USER-RECORD)
               RIDFLD(USERIDI)
               UPDATE
               RESP(WS-RESP-CODE)
           END-EXEC
      *
           IF WS-RESP-CODE = DFHRESP(NORMAL)
               MOVE WS-DISPLAY-DATE TO WS-LAST-LOGIN-DATE
               MOVE WS-DISPLAY-TIME TO WS-LAST-LOGIN-TIME
               MOVE 0 TO WS-LOGIN-ATTEMPTS
               EXEC CICS REWRITE
                   FILE('USERFILE')
                   FROM(WS-USER-RECORD)
                   RESP(WS-RESP-CODE)
               END-EXEC
           END-IF
      *
           MOVE WS-MSG-SUCCESS  TO MSGO
           PERFORM 2000-SEND-MAP
      *
           EXEC CICS RETURN
               TRANSID('BMNU')
               COMMAREA(WS-COMMAREA)
               LENGTH(LENGTH OF WS-COMMAREA)
           END-EXEC
      *
           .
      *
      *================================================================*
      *  8000 - EXIT APPLICATION                                       *
      *================================================================*
       8000-EXIT-PROGRAM.
      *
           EXEC CICS SEND TEXT
               FROM(WS-MSG-GOODBYE)
               LENGTH(40)
               ERASE
               FREEKB
           END-EXEC
      *
           EXEC CICS RETURN
           END-EXEC
      *
           .
      *
      *================================================================*
      *  9000 - RETURN WITH TRANSID FOR PSEUDO-CONVERSATIONAL          *
      *================================================================*
       9000-RETURN-TRANSID.
      *
           EXEC CICS RETURN
               TRANSID(WS-TRAN-ID)
               COMMAREA(WS-COMMAREA)
               LENGTH(LENGTH OF WS-COMMAREA)
           END-EXEC
      *
           .
      *
