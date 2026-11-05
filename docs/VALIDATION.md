# Validation

Validated on October 16 2026 using OpenJDK 26 and Maven 3.9, compiling application classes for Java 21.

## Passing checks

- Maven `verify`: **19 tests passed**, zero failures, zero errors, zero skipped.
- 18 service tests cover validation, movie editing and removal, scheduling, 15-minute turnaround boundaries, booking and cancellation, historical record protection, currency totals, literal SQL-like input, concurrent bookings, concurrent schedule writes, and file-backed persistence.
- One Swing test loads the actual presentation component asynchronously and verifies literal search, clearing filters, and numeric column sorting. Tests run headlessly and do not require a desktop server.
- The packaged executable JAR initializes and reopens a separate demo database through `--demo --check`. Both launches report three movies, three screenings, and one booking, demonstrating that reopening does not duplicate demo records.
- `--help` works without opening a window or creating a database.
- All three real Swing screens were rendered at 1220 × 800 and visually inspected. The images in this folder are component renders, not mockups.
- Source package excludes the original hardcoded credentials, old binary classes and connector jars, local databases, real customer records, and generated build output. The application JAR includes the required H2 dependency.

## Verification limits

A native desktop-window launch could not be verified in this restricted session. Screens were instead rendered directly from Swing components; native window behavior, modal dialog interaction, and OS-specific launcher behavior still require a brief manual desktop check. The Windows launch scripts and Java 21 GitHub workflow are supplied but were not executed on those platforms here.

This is an application correctness review, not an independent security audit. The project is intended for local demonstration and single-operator use, not public multi-user deployment.

## Manual desktop walkthrough

1. Extract the ZIP, ensure Java 21+ is installed, and launch the demo.
2. Add a movie and correct its title. Search for the title, then clear the filter.
3. Schedule that film tomorrow in Screen 1 at a free time. Try another screening that overlaps; expect a helpful error.
4. Book two seats and confirm the displayed availability decreases by two.
5. Find the reservation in Bookings, cancel it, and confirm the seats become available again.
6. Try deleting that screening; expect history protection.
7. Close and reopen the app; the movie, screening, and cancelled reservation should remain.
