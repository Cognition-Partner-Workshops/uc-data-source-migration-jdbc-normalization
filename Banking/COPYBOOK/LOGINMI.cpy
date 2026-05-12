      *================================================================*
      *  LOGINMI - SYMBOLIC MAP COPYBOOK FOR LOGIN SCREEN              *
      *  GENERATED FROM BMS MAP: LOGINM / LOGINI                      *
      *  THIS COPYBOOK IS INCLUDED IN LOGINP.CBL                      *
      *================================================================*
      *
       01  LOGINII.
           05  FILLER              PIC X(12).
      *
           05  USERIDL             PIC S9(04) COMP.
           05  USERIDF             PIC X(01).
           05  FILLER REDEFINES USERIDF.
               10  USERIDA         PIC X(01).
           05  USERIDI             PIC X(08).
      *
           05  PASSWDL             PIC S9(04) COMP.
           05  PASSWDF             PIC X(01).
           05  FILLER REDEFINES PASSWDF.
               10  PASSWDA         PIC X(01).
           05  PASSWDI             PIC X(08).
      *
           05  MSGL                PIC S9(04) COMP.
           05  MSGF                PIC X(01).
           05  FILLER REDEFINES MSGF.
               10  MSGA            PIC X(01).
           05  MSGI                PIC X(40).
      *
           05  DATEL               PIC S9(04) COMP.
           05  DATEF               PIC X(01).
           05  FILLER REDEFINES DATEF.
               10  DATEA           PIC X(01).
           05  DATEI               PIC X(10).
      *
           05  TIMEL               PIC S9(04) COMP.
           05  TIMEF               PIC X(01).
           05  FILLER REDEFINES TIMEF.
               10  TIMEA           PIC X(01).
           05  TIMEI               PIC X(08).
      *
           05  ERRMSGL             PIC S9(04) COMP.
           05  ERRMSGF             PIC X(01).
           05  FILLER REDEFINES ERRMSGF.
               10  ERRMSGA         PIC X(01).
           05  ERRMSGI             PIC X(30).
      *
       01  LOGINIO REDEFINES LOGINII.
           05  FILLER              PIC X(12).
      *
           05  FILLER              PIC X(03).
           05  USERIDO             PIC X(08).
      *
           05  FILLER              PIC X(03).
           05  PASSWDO             PIC X(08).
      *
           05  FILLER              PIC X(03).
           05  MSGO                PIC X(40).
      *
           05  FILLER              PIC X(03).
           05  DATEO               PIC X(10).
      *
           05  FILLER              PIC X(03).
           05  TIMEO               PIC X(08).
      *
           05  FILLER              PIC X(03).
           05  ERRMSGO             PIC X(30).
      *
