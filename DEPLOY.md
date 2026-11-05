# Deployment protocol - Texaxis Accounts (Windows)

## 1. Pre-flight (developer laptop)
- [ ] `mvn test` -> 20 tests pass
- [ ] `mvn -DskipTests package && java -jar target/TexaxisAccounts-<ver>-all.jar` -> logs in, screens open
- [ ] `<version>` in `pom.xml` = the version you are releasing
- [ ] No `AccountingDB`, `*.db`, `app.properties` in git (`git status` clean)

## 2. Build the installer (GitHub Actions, Windows runner)
```bash
git add -A && git commit -m "Release vX.Y.Z" && git push
git tag vX.Y.Z && git push origin vX.Y.Z
```
GitHub > Actions > "Windows installer" (about 5-8 min). Runs the tests again, then publishes
`TexaxisAccounts-X.Y.Z.exe` under GitHub > Releases.

## 3. Install on each office PC
1. Copy the `.exe` (USB / download from Releases).
2. Run it. The file is not code-signed, so Windows SmartScreen may show
   "Windows protected your PC": click **More info > Run anyway**.
3. Start menu > Texaxis > TexaxisAccounts (desktop shortcut is also created).
4. Login `admin` / `Admin@123` -> set the real password immediately.

## 4. Go-live data (once, in this order)
1. Accounts: add any extra expense categories.
2. Parties: add all customers and vendors.
3. New Transaction > Owner investment: opening Cash, Bank and Inventory (stock value).
4. New Transaction > Opening balance of a party: old dues, one per party.
5. Reports > Balance Sheet: confirm it balances and matches your old books.

## 5. Protect the PC
- [ ] Windows account has a password; screen locks when idle.
- [ ] BitLocker on (Settings > Privacy & security > Device encryption / BitLocker).
- [ ] Weekly: copy `C:\Users\<user>\AccountingApp\backups\` to a USB drive or company cloud.

## 6. Updating to a new version
1. Bump `<version>` in `pom.xml`, tag `vX.Y.Z+1`, let Actions build.
2. Run the new installer on each PC: it replaces the app (same upgrade UUID); **data stays**.
3. On first start the app upgrades the database automatically (Flyway). A daily backup exists from before the update.

## 7. Rollback
1. Uninstall (Settings > Apps), install the previous `.exe`.
2. If the new version changed the database, also restore the backup taken before the update:
   File > Restore from backup... > `backups\AccountingDB-<date>.db`.

## Moving to another PC
1. Old PC: File > Backup database... > save to USB.
2. New PC: install the same or a newer version, log in, File > Restore from backup... > choose the file.
3. The app closes; reopen and log in with the admin password from the old PC.

## Where things are on Windows
| What | Path |
|---|---|
| App | `C:\Program Files\TexaxisAccounts` (or the folder chosen at install) |
| Data | `C:\Users\<user>\AccountingApp\AccountingDB` |
| Settings | `C:\Users\<user>\AccountingApp\app.properties` |
| Backups | `C:\Users\<user>\AccountingApp\backups\` |

## Forgot admin password
Install DB Browser for SQLite on that PC, open `AccountingDB`, run `DELETE FROM users;`, write changes.
Next start: `admin` / `Admin@123` with forced change.
