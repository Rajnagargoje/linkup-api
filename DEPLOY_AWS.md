# LinkUp backend deployment on AWS Elastic Beanstalk

This project is prepared for a **single-container Docker Elastic Beanstalk environment**. It replaces the previous Render hosting configuration. Neon PostgreSQL, MongoDB Atlas, Cloudinary, Brevo, and Firebase can remain unchanged.

## 1. AWS service to create

In AWS Console open **Elastic Beanstalk** and create:

- Application name: `linkup-api`
- Environment tier: **Web server environment**
- Platform: **Docker**
- Platform branch: current supported Docker on Amazon Linux 2023
- Application code: upload a source bundle/ZIP containing the project files at the ZIP root
- Preset/configuration: for initial low-cost testing, use a **single instance** environment rather than a load-balanced environment

`Dockerfile` builds the Spring Boot application and `Dockerrun.aws.json` tells Elastic Beanstalk that the container serves port `8081`.

## 2. Configure environment variables

Open:

**Elastic Beanstalk -> your environment -> Configuration -> Updates, monitoring, and logging -> Edit -> Environment properties**

Add:

```text
SPRING_PROFILES_ACTIVE=aws
PORT=8081
DATABASE_JDBC_URL=jdbc:postgresql://<neon-pooled-host>/<database>?sslmode=require
DATABASE_USERNAME=<neon-role>
DATABASE_PASSWORD=<neon-password>
MONGODB_URI=mongodb+srv://<user>:<encoded-password>@<cluster>/linkup?retryWrites=true&w=majority
JWT_SECRET=<base64 secret>
CORS_ALLOWED_ORIGINS=https://localhost,capacitor://localhost,http://localhost
CLOUDINARY_CLOUD_NAME=<cloudinary-cloud>
CLOUDINARY_API_KEY=<cloudinary-key>
CLOUDINARY_API_SECRET=<cloudinary-secret>
BREVO_API_KEY=<brevo-key>
MAIL_FROM=<verified-sender>
FIREBASE_PROJECT_ID=linkup-7898e
LINKUP_PUSH_ENABLED=false
```

Do not put real secrets in Git. On current Elastic Beanstalk Docker platform releases, sensitive values can also be referenced from AWS Secrets Manager or Systems Manager Parameter Store instead of storing the secret value directly as an environment property.

Generate a JWT secret locally in PowerShell:

```powershell
[Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(64))
```

## 3. PostgreSQL and MongoDB network access

The application still uses the existing external databases.

For Neon, use the pooled PostgreSQL connection and SSL URL shown above.

For MongoDB Atlas, allow network access from the AWS environment. Avoid leaving `0.0.0.0/0` enabled for production. If the public outbound IP can change in your Elastic Beanstalk setup, use a networking design that provides stable egress before locking Atlas to a fixed IP.

## 4. WebSockets

Elastic Beanstalk places nginx in front of the Docker container. The project includes:

```text
.platform/nginx/conf.d/01_websocket_timeouts.conf
```

This increases proxy read/send timeouts for long-running STOMP/WebSocket sessions. The application still listens on the `PORT` value supplied by AWS.

## 5. Health check

After deployment, test:

```text
http://<your-environment-domain>/actuator/health
```

Expected response:

```json
{"status":"UP"}
```

If you later change to a load-balanced Elastic Beanstalk environment, configure the load balancer process health check path as `/actuator/health`.

## 6. HTTPS

The default Elastic Beanstalk environment URL may initially be HTTP. For a public/mobile release, configure HTTPS using a domain/certificate and an AWS load balancer or another suitable HTTPS front end. Do not ship production authentication traffic over plain HTTP.

When HTTPS is ready, update the Ionic frontend environment, for example:

```text
VITE_API_HOST=https://api.yourdomain.com
```

Then rebuild and sync Android:

```powershell
npm run build
npx cap sync android
```

## 7. Firebase Admin push

Leave:

```text
LINKUP_PUSH_ENABLED=false
```

until the Firebase Admin service-account credential is securely available to the running container. Do not commit the JSON file to Git. Once mounted/provided securely, set `GOOGLE_APPLICATION_CREDENTIALS` to its container path and change `LINKUP_PUSH_ENABLED=true`.

## 8. Build verification before deployment

Run locally:

```powershell
./mvnw.cmd test
./mvnw.cmd clean package
```

For a Docker check, if Docker Desktop is installed:

```powershell
docker build -t linkup-api .
docker run --rm -p 8081:8081 --env-file .env linkup-api
```

## Files changed from the old Render setup

Removed:

```text
render.yaml
DEPLOY_RENDER.md
src/main/resources/application-render.properties
```

Added/replaced:

```text
Dockerrun.aws.json
DEPLOY_AWS.md
src/main/resources/application-aws.properties
.platform/nginx/conf.d/01_websocket_timeouts.conf
Dockerfile
.dockerignore
.env.example
```
