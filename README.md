# Notification Service

Production-grade ElectraHub notification service for asynchronous email, SMS, push, and in-app notification workflows.

## Build

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-25.0.3'
C:\tools\apache-maven-3.9.16\bin\mvn.cmd test package
```

## Docker

```powershell
docker buildx build --platform linux/amd64,linux/arm64 -t amolsurjuse/notification-service:<tag> --push .
```

## Runtime

The service uses:

- Postgres schema `notification`
- RabbitMQ queue `notifications.dispatch`
- Context path `/notifications`
- Real provider sending disabled by default until credentials and sandbox tests are complete
