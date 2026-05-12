# Banking Application - Mainframe CICS/COBOL

A mainframe banking application with a **CICS BMS frontend** and **COBOL backend**.

## Project Structure

```
Banking/
├── BMS/                        # BMS Map Definitions (CICS Screens)
│   └── LOGINM.bms              # Login screen map
├── COBOL/                      # COBOL Programs (Backend Logic)
│   └── LOGINP.cbl              # Login program handler
├── COPYBOOK/                   # COBOL Copybooks (Shared Data Structures)
│   ├── LOGINMI.cpy             # Login screen symbolic map
│   └── USRREC.cpy              # User master file record layout
├── JCL/                        # JCL Jobs (Build & Deployment)
│   ├── COMPILE.jcl             # Compile COBOL & assemble BMS maps
│   ├── DEFVSAM.jcl             # Define VSAM files & seed data
│   └── CICSDEF.jcl             # CICS resource definitions (CSD)
├── DOCS/                       # Documentation
└── README.md
```

## Features

### Login Screen (Transaction: BLOG)
- 3270 terminal-based login screen with User ID and Password fields
- Password field uses dark attribute (hidden input)
- Credential validation against VSAM KSDS user master file (`USERFILE`)
- Account lockout after 3 failed login attempts
- Account status enforcement (Active / Locked / Disabled)
- Last login date/time tracking
- Date and time display on login screen
- PF3 key to exit the application

## Technical Details

| Component         | Detail                          |
|-------------------|---------------------------------|
| **Frontend**      | CICS BMS (3270 Terminal)        |
| **Backend**       | COBOL (Enterprise COBOL)        |
| **Data Store**    | VSAM KSDS (User Master File)    |
| **Transaction**   | BLOG (Banking Login)            |
| **Program**       | LOGINP                          |
| **Map/Mapset**    | LOGINI / LOGINM                 |
| **Terminal**      | IBM 3270 Model 2 (24x80)        |

## CICS Resources

| Resource    | Name      | Description                  |
|-------------|-----------|------------------------------|
| Program     | LOGINP    | Login screen handler         |
| Transaction | BLOG      | Banking login transaction    |
| Mapset      | LOGINM    | Login screen BMS mapset      |
| File        | USERFILE  | User master VSAM KSDS file   |
| Group       | BANKING   | CSD resource group           |

## User Roles

| Role     | Description          |
|----------|----------------------|
| ADMIN    | System Administrator |
| TELLER   | Branch Teller        |
| MANAGER  | Branch Manager       |
| CUSTOMER | Bank Customer        |

## Seed Users (for Development)

| User ID   | Password  | Name                        | Role     |
|-----------|-----------|-----------------------------|----------|
| ADMIN001  | PASS1234  | System Administrator        | ADMIN    |
| TELLER01  | TELL1234  | John Smith - Branch Teller  | TELLER   |
| MANAGER1  | MGMT1234  | Jane Doe - Branch Manager   | MANAGER  |
| CUSTMR01  | CUST1234  | Robert Johnson - Customer   | CUSTOMER |

## Build & Deployment

### 1. Define VSAM Files
```
SUBMIT JCL/DEFVSAM.jcl
```
Creates the `BANKING.USER.MASTER` VSAM KSDS cluster and loads seed data.

### 2. Compile and Link
```
SUBMIT JCL/COMPILE.jcl
```
Assembles BMS maps and compiles COBOL programs.

### 3. Define CICS Resources
```
SUBMIT JCL/CICSDEF.jcl
```
Installs program, transaction, mapset, and file definitions into the CICS CSD.

### 4. Install and Test
```
CEMT SET PROG(LOGINP) NEW
CECI BLOG
```

## Login Screen Layout (3270 Terminal)

```
 ================================
    BANKING SYSTEM - LOGIN
 ================================

 Welcome to the Banking Application
 Please enter your credentials below:


 USER ID  : ________

 PASSWORD : ________


 [Message Line]



 ENTER = Login                    PF3 = Exit


 DATE : MM/DD/YYYY   TIME : HH:MM:SS


 BANKING SYSTEM V1.0              [Error Messages]
```
