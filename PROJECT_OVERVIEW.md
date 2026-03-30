# Multi-Level Parking Lot System - Project Overview

## 1. What This Project Is

This project is a Spring Boot web application for managing a multi-level parking lot.
It supports:

- Driver registration and login
- Live parking spot visibility across floors
- Spot booking with expiry-based reservation logic
- Checkout and payment flow
- Booking history per user
- Persistent ticket and user storage using SQLite

The application combines:

- A web layer (`com.team12.parkinglot_web`)
- A domain/core layer (`parkinglot.*`)
- Thymeleaf templates for UI
- SQLite for persistence

## 2. Tech Stack

- Language: Java 8+ source compatibility (project builds and runs with modern JDK)
- Framework: Spring Boot 2.7.x
- View Engine: Thymeleaf
- Database: SQLite (`org.xerial:sqlite-jdbc`)
- Build Tool: Maven (`mvnw`, `mvnw.cmd`)

## 3. High-Level Architecture

The codebase is organized into two main Java package roots:

- `com.team12.parkinglot_web`
- `parkinglot`

### 3.1 Web Layer (`com.team12.parkinglot_web`)

- `ParkinglotWebApplication`:
  - Spring Boot entry point.
- `ParkingController`:
  - Handles HTTP routes and session checks.
  - Delegates business actions to services.
- `service/ParkingApplicationService`:
  - Application-level workflow logic for registration, login, booking, checkout calculation, payment finalization, and booking retrieval.
- `service/ParkingLotBootstrapService`:
  - Startup bootstrap (`@PostConstruct`) that initializes DB schema, creates default lot/floors/spots if absent in memory, and syncs active reservations from DB.

### 3.2 Domain Layer (`parkinglot`)

- `core/ParkingLot`:
  - Singleton parking lot aggregate.
  - Owns floors and computes total available spots.
- `core/ParkingFloor`:
  - Manages spots on a floor.
  - Updates display board when spot availability changes.
- `core/DisplayBoard`:
  - Observer-style display for free spot count.
- `spots/ParkingSpot`:
  - Maintains spot ID/type/status and reservation expiry.
  - `isFree()` auto-releases expired reservations.
- `spots/SpotFactory`:
  - Centralized spot creation.
- `transactions/*`:
  - Payment abstractions/strategies and ticket model classes.
- `interfaces/*`:
  - Contracts like `Observer` and `PaymentStrategy`.
- `db/DatabaseHelper`:
  - Raw SQLite operations for drivers/tickets and active-spot synchronization.
- `users/*`:
  - Account and role entities.

## 4. End-to-End Functional Flow

## 4.1 User Account Flow

1. User opens registration page.
2. `POST /process-register` stores user in `drivers` table.
3. User logs in via `POST /process-login`.
4. On success, session stores `userEmail`.

## 4.2 Spot Discovery and Booking

1. Logged-in user opens `/spots`.
2. Controller fetches floors from `ParkingApplicationService`.
3. User chooses a spot and books with `POST /book`.
4. System reserves spot (default 1 hour for direct booking), updates floor display, writes ticket row.

## 4.3 Checkout and Payment

1. User selects one or more spots and hours.
2. `POST /checkout` computes summary (spot count x hours x hourly rate).
3. `POST /payment` renders payment form with same summary.
4. `POST /pay` reserves selected free spots for requested duration and stores PAID tickets.
5. User is redirected to `/success`.

## 4.4 Booking History

1. User opens `/bookings`.
2. System queries tickets by `user_email`.
3. Thymeleaf page displays booking entries.

## 5. Data Model (SQLite)

Main tables created by `DatabaseHelper.initializeDatabase()`:

- `drivers`
  - id, name, email, phone, password, license_plate, dl_number, aadhar_number
- `tickets`
  - ticket_id, user_email, spot_id, status, payment_method, bank_code, expiry_time_millis, timestamp

`expiry_time_millis` allows restart-safe reservation restoration.

## 6. Startup and State Synchronization

At startup, bootstrap service does:

1. Initialize DB schema.
2. Ensure in-memory lot/floor/spot structure exists.
3. Sync active (non-expired) reserved spots from DB into in-memory spot states.

This keeps live spot status consistent after restarts.

## 7. UI Templates

Located in `src/main/resources/templates`:

- `index.html`: Landing page
- `login.html`: Login form
- `register.html`: Registration form
- `dashboard.html`: User dashboard
- `spots.html`: Floor/spot map and booking actions
- `checkout.html`: Fee summary page
- `payment.html`: Payment input page
- `success.html`: Post-payment confirmation
- `bookings.html`: User ticket history

Static assets are under `src/main/resources/static`.

## 8. Important Design Notes

- Singleton pattern used for `ParkingLot` instance access.
- Factory pattern used for spot creation.
- Strategy pattern exists in payment module interfaces/classes.
- Reservation expiry is enforced in `ParkingSpot.isFree()`.
- Controller concerns are separated from application workflow concerns through service classes.

## 9. How to Run

From project root:

Windows:

```powershell
.\mvnw.cmd spring-boot:run
```

Linux/macOS:

```bash
./mvnw spring-boot:run
```

App URL:

- `http://localhost:8080`

## 10. How to Test

Run unit/integration tests:

Windows:

```powershell
.\mvnw.cmd test
```

Linux/macOS:

```bash
./mvnw test
```

## 11. Current Scope and Limitations

- Password storage is plain text in current implementation and should be replaced with hashing (e.g., BCrypt).
- Persistence helper is static utility based; could be evolved toward repository/service abstractions.
- Business rules such as pricing and reservation constraints are currently hardcoded.
- Existing automated test coverage is minimal and can be expanded for controller/service behavior.

## 12. Suggested Next Improvements

1. Introduce password hashing and validation hardening.
2. Add service-level and controller-level tests for booking/payment edge cases.
3. Move from static DB helper to Spring-managed repository/service components.
4. Add DTOs and validation annotations for request payloads.
5. Add API docs and optional REST endpoints for integration.
