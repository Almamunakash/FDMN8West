# 8 West FDMN Database

Android app for Camp-8W beneficiary records (Main Block → Sub-Block → families by FCN), with a PHP + MySQL backend on app.informationplant.com.

## Parts

- `app/` — Android app (Kotlin, Jetpack Compose). GitHub Actions builds the APK on every push → artifact **FDMN8West-apk**.
- `backend/` — goes in the root folder of app.informationplant.com: `index.php` (landing page) and `api/` (PHP API + one-time installer).

## Server setup (once, in cPanel)

1. **MySQL Databases** → create a database (e.g. `fdmn`) and a user with a strong password → *Add User To Database* → tick **ALL PRIVILEGES**.
2. **File Manager** → open the folder of app.informationplant.com → upload `fdmn-server.zip` → right-click → **Extract**. You get `index.php` and the `api/` folder.
3. Open `https://app.informationplant.com/api/install.php`, enter the database name, user and password from step 1, and choose the first Admin's name, email and password → **Set up now**.
4. Delete `install.php` from the `api/` folder (it is locked after setup anyway).
5. Check: `https://app.informationplant.com/api/index.php?a=ping` should show `"ok":true`.

## Roles

- **Admin** — add, edit, delete records; import from Excel; approve, block, promote users; reset passwords; reply to messages.
- **Viewer** — search, filter, view records and families, export, send requests to the Admin.

New sign-ups wait for Admin approval.

## API

`POST index.php?a=<action>` with a JSON body; the signed-in token goes in the `X-Auth-Token` header.
Actions: ping, register, login, logout, me, change_password, stats, blocks, options, list, family, get, save, delete, import, users, user_set, user_delete, msg_send, msg_mine, msg_all, msg_reply, msg_close.
