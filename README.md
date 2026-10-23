# Bluethink Employee Portal — Backend

## Overview
Spring Boot REST API for the Bluethink Employee Portal. The backend is API-only; the web UI is provided by the React frontend.

## Architecture

```text
React Frontend
      |
      | HTTP/JSON + JWT
      v
Spring Boot REST Controllers
      |
      v
Services / Business Logic
      |
      v
Repositories (Spring Data JPA)
      |
      v
MySQL

Cross-cutting:
- Security/JWT
- Kafka messaging
- Mail
- Validation
- Global API error handling
```

### Source layout

```text
src/main/java/EmployeeManagementSystem/
├── config/              Application configuration and startup initializers
├── constants/           Shared constants
├── controller/          REST API controllers
├── dto/                 Request/response DTOs
├── entity/              JPA entities
├── enums/               Domain enums
├── exception/           API exception handling
├── messaging/kafka/     Kafka producer, consumer, events and topic config
├── repository/          Spring Data repositories
├── security/jwt/        JWT utility and authentication filter
├── service/             Business services and implementations
└── util/                Small shared utilities
```

The project uses a single layered structure. Domain-specific admin classes are kept in the normal controller/service/repository/entity layers instead of separate `admin_*` package trees.

## Requirements
- Java 17+
- Maven Wrapper (`mvnw.cmd` on Windows)
- MySQL
- Kafka when messaging features are enabled

## Run

```powershell
.\mvnw.cmd clean package -DskipTests
.\mvnw.cmd spring-boot:run
```

## Configuration
Configure database, JWT, Kafka and mail settings in the application's configuration files/environment variables. Never commit production secrets.

Default local Kafka configuration used by this project is `localhost:9092` unless overridden.

### Policy RAG assistant
The policy RAG endpoints are disabled by default. To enable them, configure `RAG_ENABLED=true`, `GROQ_API_KEY`, `JINA_API_KEY`, and a MariaDB 11.3+ vector database using `RAG_MARIADB_URL`, `RAG_MARIADB_USERNAME`, and `RAG_MARIADB_PASSWORD`. Groq and Jina credentials must be supplied as environment variables; do not copy keys from the sample application into source files.

`POST /api/rag/ingest` is restricted to HR and admins. It indexes current effective policies from the portal and, optionally, a `file` multipart PDF (maximum 10 MB). Re-run ingestion after policy changes. `POST /api/rag/chat` accepts `{"question":"..."}` and is available to authenticated employees. Policy text and questions are sent to Jina and Groq for embeddings and answers; enable this feature only if that data processing is approved for your organization.

For local use on Windows, run `.\RUN_RAG_BACKEND.ps1` from PowerShell. It prompts for Groq/Jina keys and the MariaDB connection, hides secret input, and sets credentials only for the launched process; nothing is saved in the repository or persistent environment. The database named in the JDBC URL must already exist, and the MariaDB account needs permission to create the RAG vector table.

## API-only rule
This backend does **not** serve Thymeleaf pages or a server-rendered web UI. All browser functionality belongs in the React frontend.

## Verification

```powershell
.\mvnw.cmd test
.\mvnw.cmd clean package
```

Verify startup reaches `Started ...Application` without `APPLICATION FAILED TO START`.
