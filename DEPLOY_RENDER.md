# Free LinkUp test deployment

Status: deployment files and integrations are prepared. No cloud accounts or hosted service have been created. This is a single-instance test deployment, not an availability guarantee. Keep all services on their Free plans and check each provider's current quota in its dashboard.

## 1. Create accounts

Create accounts yourself and complete verification in each provider's website:

| Provider | Create/select | Used for |
| --- | --- | --- |
| [Render](https://dashboard.render.com/) | Free Web Service using Docker | Spring Boot and WebSockets |
| [Neon](https://console.neon.tech/) | Free PostgreSQL project | Users, direct messages, friends, notifications |
| [MongoDB Atlas](https://www.mongodb.com/cloud/atlas/register) | Free cluster | Room history |
| [Cloudinary](https://cloudinary.com/users/register/free) | Free product environment | Profile images |
| [Brevo](https://www.brevo.com/) | Free account with transactional sending enabled | Verification emails over HTTPS |

Choose nearby regions where free plans are available. Do not paste API keys or database passwords into chat. Enter them directly in Render Environment. Firebase is already configured in the Android project; no new Firebase project is needed.

## 2. Prepare databases

For Neon, create an empty `linkup` database (or use the generated database) and copy its pooled hostname, database name, role and password from Connect. Set these Render variables separately:

```text
DATABASE_JDBC_URL=jdbc:postgresql://<pooled-host>/<database>?sslmode=require
DATABASE_USERNAME=<role>
DATABASE_PASSWORD=<password>
```

Do not paste a `postgresql://user:password@host` URL into the JDBC field. Use the `jdbc:postgresql://` form above. The pool is limited to three connections.

For Atlas, create a database user restricted to read/write on `linkup`; your Atlas website login is not this database user. Use its driver connection string with the database name:

```text
MONGODB_URI=mongodb+srv://<user>:<encoded-password>@<cluster-host>/linkup?retryWrites=true&w=majority
```

URL-encode special characters in the password. Add the Render service's outbound IP ranges, shown in Render's Connect menu, to Atlas Network Access. If these ranges are not visible until the service exists, finish creating the Render service, add its ranges, then redeploy. Do not leave unrestricted network access as a workaround.

This setup starts with an empty account/chat database. It does not copy local data. If preserving local accounts matters, migrate PostgreSQL, MongoDB and photos together before distributing the new APK. Existing localhost photo URLs must be migrated/re-uploaded; simply changing the API URL does not fix them.

## 3. Configure photo and email providers

From Cloudinary's API Keys settings, set `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY` and `CLOUDINARY_API_SECRET` in Render. Uploads are signed by the backend and stored under `linkup/photos/`. No unsigned upload preset or client-side secret is needed. Only app-generated assets in this cloud can be deleted through this integration.

In Brevo, verify your sender address (and authenticate a sending domain if required for that address/account), enable transactional sending, and create an API key. Set `BREVO_API_KEY` and `MAIL_FROM` to that verified address. Use an API key, not an SMTP key. Account approval/sender restrictions must be resolved before testing with other users. No real test emails are sent during automated tests.

## 4. Publish the prepared backend source

The existing backend Git remote is `https://github.com/Rajnagargoje/linkup-api.git`. The local changes must be reviewed and committed/pushed before Render can build them; they have not been pushed by this task. Connect Render to this repository with the permissions it needs. This repository's root contains `Dockerfile` and `render.yaml`.

Never upload a locally built JAR: local Maven packaging can include your local `application.properties`. Deploy through the provided Dockerfile, whose build context includes only the non-secret Render resource configuration. `.gitignore` excludes local application properties, environment files, Firebase Admin keys and uploads. Review staged files before publishing.

## 5. Create the Render Blueprint

In Render choose New → Blueprint and select the backend repository/branch containing these changes. Review `render.yaml`: it declares only one Free Docker web service, no paid databases or disks. Supply the variables marked `sync: false` using the values collected above.

Generate `JWT_SECRET` as Base64 of 64 cryptographically random bytes. For example, run this locally and paste its output only into Render's secret field:

```powershell
[Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(64))
```

`CORS_ALLOWED_ORIGINS=https://localhost` supports the current Android Capacitor app. Append the exact website HTTPS origin, comma-separated, when deploying the website. Do not use `*`. The same setting controls REST and WebSocket origins.

The Render profile reads `PORT`, disables SQL logging/API docs, uses Cloudinary and Brevo, and exposes `/actuator/health` without diagnostic details. That endpoint checks database connectivity. The container runs as a non-root user, with JVM heap limited to 60% of container memory. This needs real memory/load testing on Render Free; the heap cap does not cap all JVM/native memory.

For this initial test database, Hibernate `DDL_AUTO=update` creates the schema. Before a production launch, replace auto-update with reviewed versioned migrations and enable database backups. Do not point this initial bootstrap at an existing production database without review.

## 6. Enable hosted Firebase push

After the first successful deployment, add a Render Secret File named `firebase-admin.json` containing the Firebase Admin JSON already saved on your computer. Do not put it in Git or paste it into chat. Set:

```text
GOOGLE_APPLICATION_CREDENTIALS=/etc/secrets/firebase-admin.json
FIREBASE_PROJECT_ID=linkup-7898e
LINKUP_PUSH_ENABLED=true
```

Save/redeploy. The Blueprint initially has push disabled so the app can start before the secret file exists. If syncing the Blueprint again, preserve your enabled setting (update its non-secret value in `render.yaml` after activation). These hosted settings are separate from the local PowerShell launcher.

## 7. Rebuild and install LinkUp

Wait for Render to report Healthy. Open `https://<service>.onrender.com/actuator/health` and verify `{"status":"UP"}`. Set `LinkUpApp/.env.local`:

```text
VITE_API_HOST=https://<service>.onrender.com
```

Run `npm run build`, `npx cap sync android`, and build the Android APK. For ongoing testing, use a release APK signed with a stable keystore that you keep backed up outside Git. All testers must use the APK built with the same backend URL. The existing Firebase Android package remains `com.linkup.app`.

Test two accounts on separate phones/networks: registration, email verification, upload/delete photo, random matching, friendship, direct messages, room history, blocking/report visibility, badges, background push and notification tap navigation. Redeploy once and check that database records and uploaded images remain available.

## Free-plan limitations

Render Free sleeps after 15 minutes without inbound traffic, loses local filesystem changes and blocks common SMTP ports. Its scheduled push worker cannot run during sleep; new requests wake the service. Random matchmaking and active matches are in memory, so restarts interrupt them. One instance is deliberate: multiple replicas need shared matching state and a shared message broker. External databases and Cloudinary preserve stored data, not live random-chat sessions. Do not add artificial keep-alive traffic to bypass the free plan.

No hosted deployment, real Cloudinary upload, Brevo delivery or hosted push test is complete until the accounts are connected. Docker is not installed on this workstation, so the image itself must be built/verified on Render or a Docker-enabled machine.

## Official references

- [Render Blueprint configuration](https://render.com/docs/blueprint-spec)
- [Render Free limitations](https://render.com/docs/free)
- [Render secret files](https://render.com/docs/configure-environment-variables)
- [Cloudinary Java SDK](https://cloudinary.com/documentation/java_integration)
- [Brevo transactional email API](https://developers.brevo.com/docs/send-a-transactional-email)
