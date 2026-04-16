# Multi-Level Parking Lot System Documentation

Last updated: April 2026

## 1. Project Overview

This project is a Spring Boot MVC application that manages a multi-level parking lot with:

- Driver registration and login
- Live map of parking spots by floor
- Spot reservation with time-based expiry
- Multi-spot checkout and payment
- Booking history per user
- SQLite persistence for users and tickets

The app has two major package roots:

- `com.team12.parkinglot_web` (web + application workflow)
- `parkinglot` (domain/core, persistence utility, entities)

## 2. Architecture

## 2.1 Layered Architecture

### Web Layer

- `ParkingController`
- Handles HTTP routes, session checks, redirects, and view rendering.
- Converts form inputs to app-level requests (`PaymentRequest`).

### Application Service Layer

- `ParkingApplicationService`
- Central workflow orchestration:
  - input validation
  - registration/login calls
  - checkout summary calculations
  - reservation and payment transaction flow
- Uses synchronized locking (`bookingLock`) to prevent race conditions when booking/paying.

### Payment Integration Layer

- `PaymentProcessorAdapter`
- Structural adapter that maps web payment inputs to domain strategy implementations.
- Current supported methods:
  - `CREDIT_CARD`
  - `NET_BANKING`

### Domain/Core Layer

- `ParkingLot` (Singleton)
- `ParkingFloor`
- `ParkingSpot`
- `DisplayBoard`
- `SpotFactory`
- `Payment`, `PaymentStrategy`, strategy implementations

### Persistence Layer

- `DatabaseHelper` (SQLite utility)
- Manages:
  - schema initialization
  - user registration/login
  - ticket save/query/update
  - startup synchronization of active reservations

## 2.2 Request-Response Flow (High Level)

1. Browser sends request to `ParkingController`.
2. Controller validates session (for protected routes) and delegates to `ParkingApplicationService`.
3. Service validates business input and invokes domain/persistence logic.
4. Service returns result to controller.
5. Controller renders Thymeleaf view or redirects with status query params.

## 2.3 Startup Flow

`ParkingLotBootstrapService.initialize()` runs at startup (`@PostConstruct`):

1. Initializes DB tables/indexes if not present.
2. Builds in-memory lot/floor/spot structure if empty.
3. Restores active spot reservations from DB based on `expiry_time_millis`.

## 3. How It Works Internally

## 3.1 Authentication

### Register (`POST /process-register`)

1. Controller receives form values.
2. Service validates name/email/password format.
3. `DatabaseHelper.registerDriver(...)` hashes password with BCrypt.
4. User row is inserted in `drivers` table.

### Login (`POST /process-login`)

1. Service validates email/password presence.
2. DB fetches stored password hash by email.
3. BCrypt verification is performed (`BCrypt.checkpw`).
4. On success, controller stores lowercase email in session (`userEmail`).

## 3.2 Spot Visibility and Reservation

### View Spots (`GET /spots`)

- Loads all floors/spots from singleton `ParkingLot`.
- Each `ParkingSpot` uses expiry-aware state checks.

### Single Spot Booking (`POST /book`)

1. Service validates spot ID and user email.
2. Acquires `bookingLock`.
3. Re-checks spot availability in-memory.
4. Reserves spot for 1 hour and writes a `RESERVED` ticket.
5. Updates floor display board.

## 3.3 Checkout and Pricing

### Checkout (`POST /checkout`)

1. Service validates `hours` range (1 to 24).
2. Parses and deduplicates selected spot IDs.
3. Computes fee:

`totalFee = spotCount * hours * HOURLY_RATE`

Current `HOURLY_RATE` is 50.0.

## 3.4 Payment Finalization

### Payment Form (`POST /payment`)

- Rebuilds checkout summary and renders payment page.

### Final Pay (`POST /pay`)

1. Controller builds `PaymentRequest` from form values.
2. Service validates user + cart data.
3. Acquires `bookingLock`.
4. Re-checks all requested spots are still free.
5. Calls `PaymentProcessorAdapter.processPayment(...)`.
6. Adapter validates payment fields and picks strategy:
   - credit card -> `CreditCardPayment`
   - net banking -> `NetBankingPayment`
7. On success:
   - reserves all spots for requested hours
   - inserts `PAID` tickets
   - updates affected floor display boards

If any spot is no longer available, payment is rejected safely and user is redirected with an error flag.

## 3.5 Booking History

### Bookings (`GET /bookings`)

1. Controller checks session.
2. Service fetches tickets for logged-in user email.
3. Tickets are shown in descending timestamp order.

## 4. Key Endpoints

- `GET /` -> landing page
- `GET /register` -> registration form
- `POST /process-register` -> register user
- `GET /login` -> login form
- `POST /process-login` -> login user
- `GET /logout` -> destroy session
- `GET /dashboard/driver` -> user dashboard (auth required)
- `GET /spots` -> floor/spot map (auth required)
- `POST /book` -> quick reserve one spot (auth required)
- `POST /checkout` -> cart summary (auth required)
- `POST /payment` -> payment form (auth required)
- `POST /pay` -> finalize payment (auth required)
- `GET /success` -> success page (auth required)
- `GET /bookings` -> booking history (auth required)

## 5. Data Model

## 5.1 drivers table

- `id` (PK)
- `name`
- `email` (UNIQUE)
- `phone`
- `password` (BCrypt hash)
- `license_plate`
- `dl_number`
- `aadhar_number`

## 5.2 tickets table

- `ticket_id` (PK)
- `user_email`
- `spot_id`
- `status` (`RESERVED`, `PAID`, etc.)
- `payment_method`
- `bank_code`
- `expiry_time_millis`
- `timestamp`

Indexes:

- `idx_tickets_user_email`
- `idx_tickets_expiry`

## 6. Design Patterns and Principles Used

Patterns:

- Singleton: `ParkingLot`
- Factory: `SpotFactory`
- Observer: `DisplayBoard` with observer contract
- Strategy: payment strategies via `PaymentStrategy`
- Adapter: `PaymentProcessorAdapter`

Principles:

- SRP: controller routing, service orchestration, adapter translation each separated
- OCP: add payment methods via new strategy implementations
- DIP: service depends on abstraction flow through adapter/strategy
- ISP: small focused interfaces (`PaymentStrategy`, `Observer`)

## 7. How To Run

From project root:

### Windows

```powershell
.\mvnw.cmd spring-boot:run
```

### Linux/macOS

```bash
./mvnw spring-boot:run
```

Open:

- `http://localhost:8080`

Notes:

- `pom.xml` is configured with `java.version=8` source compatibility.
- Running with newer JDKs is supported as long as Maven and Spring Boot can build correctly in your environment.

## 8. How To Use (User Walkthrough)

1. Open the app home page.
2. Create account from Register page.
3. Sign in from Login page.
4. Open Live Map (`/spots`) and select one or more available spots.
5. Choose parking hours (1 to 24).
6. Continue to checkout and review fee.
7. Enter payment details and submit.
8. View success page and then check `/bookings` for ticket history.

## 9. Build and Test Commands

### Run tests

Windows:

```powershell
.\mvnw.cmd test
```

Linux/macOS:

```bash
./mvnw test
```

### Full verification

Windows:

```powershell
.\mvnw.cmd verify
```

Linux/macOS:

```bash
./mvnw verify
```

## 10. UML and Rubric Support Files

- `RUBRIC_COMPLIANCE.md`
- `docs/diagrams/use_case_diagram.puml`
- `docs/diagrams/class_diagram.puml`
- `docs/diagrams/activity_*.puml`
- `docs/diagrams/state_*.puml`

## 11. Current Limitations

- Payment gateways are simulated (no external payment API integration).
- Persistence access is implemented as a static helper, not repositories.
- Test coverage is currently minimal and should be expanded.

## 12. Suggested Next Work

1. Add service and controller tests for negative and concurrent scenarios.
2. Add admin/attendant workflows and role-based authorization.
3. Move persistence layer to repository abstractions.
4. Add audit logs/metrics for operational visibility.
