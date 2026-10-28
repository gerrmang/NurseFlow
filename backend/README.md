# NurseFlow Backend

Kotlin + Spring Boot backend. The first implementation slice contains a pure domain validator for approved leave/half-day overlap, same-nurse time overlap, and night-to-next-day-day constraints.

## Current boundaries

- Shift dates are ward-local `LocalDate` values. Convert local shift definitions to `Instant` intervals using the ward timezone before validation.
- Approved leave and half-day records contain time intervals only; no leave reason or health details belong in this model.
- The validator is not yet connected to persistence, authentication, roster APIs, or automatic scheduling.
- Exact minimum rest-hour policy, staffing demand, and qualifications remain P0 decisions.

## Build

The project targets JDK 21. Use the Gradle wrapper once it is generated; the current workstation Gradle installation is failing during native platform initialization, so bootstrap validation is pending.
