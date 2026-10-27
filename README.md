# Reporting System

Finds machines (units) whose bill recycler fails more often than other units of the same model.

| Folder | What it is |
| --- | --- |
| [`ReportingSystem/`](ReportingSystem/) | Backend: Spring Boot 3.5, Java 17, MySQL 8. Mock event generator, Spring Batch statistics job, REST API (`/api/...`). |
| [`ReportingSystemFront/reporting-ui/`](ReportingSystemFront/reporting-ui/) | Frontend: Angular. Scanner and recycler problems first (part problems page, "mark as OK"), then unit ranking by tier, unit detail with events, units by fault kind, models. |

## Run

1. Backend: follow [`ReportingSystem/README.md`](ReportingSystem/README.md) (create the MySQL tables, set the password in `application-dev.yml`, then `mvn spring-boot:run`). It listens on port 8080.
2. Frontend: run `ReportingSystemFront/reporting-ui/start-frontend.bat` (or `npm install` and `npm start`). It opens on http://localhost:4200 and forwards `/api` to the backend.
