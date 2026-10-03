# Akumé Smart Storage

Akumé Smart Storage is a personal software and study project for organizing physical storage: electronics, boards, cables, components, tools, and other household or lab items. It explores location and inventory concepts with a Java/Spring backend and an Angular frontend.

The repository is **under active development** and is not a production-ready inventory system. Its implemented user-facing workflow is currently AddressType management through the backend REST API and Angular/PO UI. Address hierarchy application capabilities are being developed, but do not yet form a complete Address REST/UI workflow.

## Current implementation status

| Area | Status |
| --- | --- |
| M0 Foundation | Complete |
| M1 AddressType | Complete |
| M2 Address | In progress |
| M2-01 Address Domain | Complete |
| M2-02 Persistence + Hierarchy Queries | Complete |
| M2-03A Serializable Transaction Executor | Complete |
| M2-03B Create + Rename Address | Complete |
| M2-03C Move Address | Complete |
| M2-03D Address Lifecycle | Complete |
| M2-03E AddressType lifecycle coordination | Not started |
| M2-03F Concurrency acceptance | Not started |
| M3–M12 V1 capabilities | Planned; not implemented |

The planned V1 scope includes items, inventory, operations (`ENTRY`, `EXIT`, and `INTERNAL_MOVEMENT`), and printable QR/PDF labels. These capabilities are **not currently implemented**. A complete Address REST/UI workflow and authentication/authorization are also not implemented. Authentication and authorization remain future work; there is no authentication in the current development application.

## Architecture and technology

- Java 21, Spring Boot, and the Maven Wrapper
- PostgreSQL and Flyway
- Domain-Driven Design, Hexagonal Architecture, and a Modular Monolith
- Angular and PO UI

Flyway is the schema authority. Hibernate validates mappings and does not generate or update the schema. See [Architecture](docs/ARCHITECTURE.md), [Domain](docs/DOMAIN.md), [Product](docs/PRODUCT.md), and the [V1 milestone plan](docs/milestones/V1.md) for more context.

## Prerequisites

- Java 21
- Node.js 24.x and npm
- Docker with the Docker Compose plugin

Use the checked-in Maven Wrapper; a separately installed Maven is not required. Frontend dependencies are pinned by `package-lock.json`.

## Start PostgreSQL

From the repository root:

```bash
docker compose up -d
```

PostgreSQL 18.1 is published on `127.0.0.1:5433` by default. Set `POSTGRES_PORT` to choose another host port. Compose and the backend configuration contain disposable local-development database credentials so a fresh checkout can run; never reuse them for production or a database exposed to an untrusted network. The database data is kept in the named `postgres-data` volume.

## Run the backend

With PostgreSQL running, from the repository root:

```bash
cd backend
./mvnw test
./mvnw verify
./mvnw spring-boot:run
```

The backend listens on port `8080` by default. Database URL, username, password, and server port can be overridden with `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, and `SERVER_PORT`.

## Run the frontend

From the repository root:

```bash
cd frontend
npm ci
npm start
```

The development server uses port `4200` by default. Supported checks are:

```bash
npm test -- --watch=false
npm run build
```

The repository does not currently provide an end-to-end test setup.

## Security / Development Tooling

The dependency assessment found two advisories in development tooling. They are not findings in the Smart Storage browser application's production/runtime dependency set. At assessment time, `npm audit --omit=dev` reported **0 findings**. This does not mean the repository is vulnerability-free: developers and CI still use the affected tooling, so their exposure is real.

- **Critical — Piscina**, [GHSA-67c8-pqhq-4rmx](https://github.com/advisories/GHSA-67c8-pqhq-4rmx) / CVE-2026-102992. `@angular/build` resolves affected Piscina `5.2.0`; upstream patched this advisory in `5.3.2`. Piscina is development/build tooling, is not present in the production audit, and is not directly used by Smart Storage application source. The Angular 21.2 build package available at assessment time pinned the affected version; no supported Angular 21.2 patch containing the fix was available then.
- **High — http-cache-semantics**, [GHSA-ch52-4w7c-c8xp](https://github.com/advisories/GHSA-ch52-4w7c-c8xp) / CVE-2026-93748. The Angular CLI package-fetch path (`@angular/cli` → `pacote` / registry tooling) resolves affected `http-cache-semantics` `4.2.0`. The advisory listed no patched upstream release at assessment time. This is development/package-fetch tooling, is not present in the production audit, and is not used by the browser application runtime.

These findings are temporarily accepted for source publication because they are confined to the development/build toolchain, the production/runtime audit reported no findings, this project is not production-ready, and no supported Angular 21 remediation was available at assessment time. npm's proposed clean fix requires an Angular 22 major migration; PO UI 21.32.0 declares Angular `^21` compatibility, not Angular 22. This is a time-limited risk decision, not a claim that the advisories are harmless.

Revisit this decision when Angular 21 LTS publishes a compatible CLI/build patch, `@angular/build` stops resolving the affected Piscina version, the http-cache-semantics advisory gets a patched release, PO UI supports Angular 22 and a major migration becomes reasonable, or Smart Storage approaches production deployment.

The project intentionally does not currently use `npm audit fix --force`, unsupported npm overrides, a forced Piscina replacement beneath Angular, or an Angular 22 migration solely to silence npm audit. To reproduce the audit views, run from the repository root:

```bash
cd frontend
npm audit
npm audit --omit=dev
```

## License

Licensed under the [Apache License, Version 2.0](LICENSE).
