## V24.5: AI preview ledger data retention

Consumed/cancelled previews now erase their detailed JSON and reviewer target ID atomically, retaining ledger state and commit receipts. Scheduled cleanup also redacts expired/historical action payloads. No database migration is required. Verify this behavior with Spring Boot + MySQL staging transactions before deployment. See root `V24_5_AI_DRAFT_DATA_MINIMIZATION_RELEASE_NOTES.md`.

> **AI V23.9:** Ollama synchronous chat has dedicated configurable connect/read timeouts. These are not total deadlines, and real model timeout testing is still required. See root `V23_9_AI_OLLAMA_TIMEOUT_RELEASE_NOTES.md`.

> **AI V23.8:** `GET /api/ai/model-health` provides read-only ADMIN-only local circuit/capacity state; no chat content, no provider ping. See top-level release notes.

> **V23.7:** AI model recovery guard for repeated Ollama failures; process-local, configurable, with safe Retry-After and offline JDK behavior verification. See root `V23_7_AI_MODEL_RECOVERY_RELEASE_NOTES.md`. No new migrations; live staging tests pending.

> **BlueThink V23.5:** AI chat failures distinguish model/database/unexpected errors; failed questions stay editable for retry. See top-level V23_5_AI_CHAT_FAILURE_RECOVERY_RELEASE_NOTES.md. Staging tests pending; not production-certified.

> **BlueThink V23.4:** AI-wide no-store responses including auth/error paths, a separately budgeted AI-chat request limiter, and corrected deny metrics. See top-level V23_4_AI_PRIVACY_RATE_GUARD_RELEASE_NOTES.md. Staging verification required; not production-certified.

# BlueThink backend

See `../README.md` for the consolidated release scope, configuration, migration and exact verification evidence. Historical build claims do not apply to this release.

Use JDK 21. The Maven source compatibility target remains Java 17. With dependency access, run `mvnw.cmd -B verify` on Windows or `bash mvnw -B verify` on Linux/macOS. The ordinary test task does not enable the opt-in application-context integration test; use a disposable configured environment before setting `HRMS_RUN_INTEGRATION_TESTS=true`.

For development, export database/JWT/optional service environment variables and run `mvnw.cmd spring-boot:run` or `bash mvnw spring-boot:run`. Default local port is 8081. Administrator bootstrap is disabled; it must be explicitly enabled with a strong configured password for a new local database. Preserve an existing database and upload directories.

Production uses `SPRING_PROFILES_ACTIVE=prod`, default port 8080 and schema validation. Review `../deploy/.env.example`, apply the reviewed incremental migration to staging first, then validate production configuration. No live service or full Maven build was verified in this execution environment.
