# Texaxis Accounts

Offline desktop accounting for a small office: parties (customers/vendors) with their own ledgers,
purchases, sales, payments, expenses, assets, and reports (P&L, Balance Sheet, Trial Balance).
Java 21+, JavaFX, SQLite, Flyway. No server, no internet needed.

## Run (development)
```bash
mvn javafx:run            # or run com.office.application.Main from IntelliJ
mvn test                  # 20 automated business/security tests
```

## First login
Username `admin`, password `Admin@123`. The app forces a new password at first login;
after that the default no longer works. 5 wrong passwords lock login for 5 minutes.
The app auto-locks after 15 idle minutes (Admin > Lock now locks immediately).

**Forgot the password?** Close the app and run
`sqlite3 ~/AccountingApp/AccountingDB "DELETE FROM users;"` - the next start restores `admin` / `Admin@123`
(forced change again). Requires access to the PC itself.

## Data, settings, backups
| What | Where (default) |
|---|---|
| Database | `~/AccountingApp/AccountingDB` |
| Settings | `~/AccountingApp/app.properties` (created on first run, see `app.properties.example`) |
| Daily backups | `~/AccountingApp/backups/` (newest 30 kept) |

Environment variables override settings: `OFFICELEDGER_DATA_DIR`, `OFFICELEDGER_COMPANY`,
`OFFICELEDGER_BACKUP_DAYS`, `OFFICELEDGER_AUTOLOCK_MINUTES`.

**Restore a backup:** close the app, copy `backups/AccountingDB-YYYY-MM-DD.db` over `AccountingDB`
(delete `AccountingDB-wal` and `AccountingDB-shm` if present), start the app.

## Build an installer
```bash
mvn -DskipTests package   # -> target/TexaxisAccounts-1.0.0-all.jar

# Linux (.deb) - build on Linux
jpackage --type deb --name TexaxisAccounts --app-version 1.0.0 --vendor "Texaxis (SMC-Private) Limited" \
  --input target --main-jar TexaxisAccounts-1.0.0-all.jar --main-class com.office.application.Main \
  --java-options "--enable-native-access=ALL-UNNAMED" --icon packaging/texaxis.png \
  --linux-shortcut --linux-menu-group Office --dest dist

# Windows (.exe) - build on Windows (needs WiX Toolset installed)
jpackage --type exe --name "Texaxis Accounts" --app-version 1.0.0 --vendor "Texaxis (SMC-Private) Limited" ^
  --input target --main-jar TexaxisAccounts-1.0.0-all.jar --main-class com.office.application.Main ^
  --java-options "--enable-native-access=ALL-UNNAMED" --icon packaging\texaxis.ico ^
  --win-shortcut --win-menu --win-dir-chooser --dest dist
```
The installer bundles its own Java; the customer installs nothing else.
JavaFX is platform-specific: build the Windows installer on Windows.

## Going live checklist
1. Install, log in with `Admin@123`, set the real password.
2. Accounts > add any extra expense categories.
3. Parties > add customers and vendors.
4. New Transaction > **Owner investment** for opening cash, bank and stock (Inventory).
5. New Transaction > **Opening balance of a party** for dues from before.
6. Check Reports > Balance Sheet.

## Security notes
- Passwords: salted PBKDF2-SHA256 (310k iterations), never stored in plain text.
- Every change and login is in Admin > Activity log.
- Data folder and database are readable only by the OS user (Linux/macOS).
- The login protects the app, not the file. Protect the PC: OS login password + disk encryption
  (BitLocker on Windows, LUKS on Ubuntu). Keep manual backups somewhere safe; they contain all data.

## Rules for developers
- Never edit an applied migration (`V1`..`V7`). Add `V8__...sql`.
- Never commit `AccountingDB`, `*.db` or `app.properties`.
