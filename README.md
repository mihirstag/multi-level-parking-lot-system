# Park Manager

A full-stack Spring Boot web application for operating a modern multi-level parking facility. The platform now supports role-based operations for drivers, admins, attendants, and security guards with a unified web experience and persistent lifecycle-aware ticketing.

## Features

- **Premium UI / UX**: A complete dark-theme aesthetic featuring glassmorphism elements, neon accents, and unified responsive navigation across all pages.
- **Role-Based Authentication & Session Routing**:
  - Secure login with role-aware redirects and route guards.
  - Driver registration plus seeded operational accounts for admin, attendant, and security guard roles.
- **Credential Security**: Passwords are stored using BCrypt hashing and verified securely during login.
- **Role Dashboards**:
  - Driver dashboard for booking and payments.
  - Admin control dashboard and incident center.
  - Attendant desk for lost-ticket handling.
  - Security dashboard for violations and gate overrides.
- **Real-Time Spot Status & Expiry**: 
  - View available spots across multiple floors on the Live Map.
  - ***Dynamic DOM Rendering***: Booked spots are physically removed from the UI to prevent clutter.
  - ***Time-based Expiry***: Spots automatically free themselves after the booked duration (hours) expires.
  - ***Cross-Session Persistence***: Active bookings are synchronized from the database on startup, ensuring the Live Map remains accurate after restarts.
- **Vehicle-to-Session Tracking**:
  - Vehicle ID is required in checkout/payment flow and persisted with each ticket.
  - Multi-spot checkout now uses explicit per-spot mapping (`spotIds[]` + `vehicleIds[]`) so each selected spot can have a different vehicle number.
  - Active sessions can be queried by vehicle ID in the admin incident center.
- **Vehicle-Type-First Booking Flow**:
  - Drivers must select vehicle type before selecting spots.
  - Live map highlights and allows only matching spot types.
  - Backend re-validates spot type to prevent payload tampering.
- **Lifecycle Reconciliation**:
  - Ticket lifecycle states include `RESERVED`, `PAID`, `OCCUPIED`, `EXPIRED`, `CLOSED`, and `LOST_TICKET`.
  - Expired active tickets are reconciled automatically and spot availability is re-synced.
- **Diverse Spot Types**: Supports various spot types including Compact, Large, Handicapped, and EV Charging spots.
- **Structured Floor Allocation**:
  - 3 floors are initialized.
  - Each floor has 20 Compact, 20 Large, 5 Handicapped, and 5 EV Charging spots.
- **Booking & Checkout System**: 
  - Select single or multiple parking spots dynamically.
  - Enter one vehicle number for each selected spot.
  - Select a single vehicle type per checkout batch.
  - Specify parking duration (1-24 hours).
  - Automatic fee calculation (e.g., ₹50/hour per spot).
- **Payment Processing**:
  - Supported methods in web flow: `CREDIT_CARD`, `NET_BANKING`, and `CASH`.
  - Payment adapter validates method-specific input and routes to the proper strategy implementation.
- **Reliable Booking Semantics**: Booking and payment flows use synchronized reservation handling to prevent duplicate spot assignment under concurrent requests.
- **My Bookings Portal**: Drivers can view ticket history with vehicle mapping, payment method, and lifecycle status.
- **Operations Visibility**:
  - Admin incident center shows violations, lost-ticket cases, gate overrides, and active parking sessions.
  - Security role can submit violation reports and gate override logs.
  - Attendant role can resolve lost-ticket cases and release spots with audit records.
- **Database Integration**: Embedded SQLite database (`parkinglot.db`) stores users and tickets with vehicle/session fields (`vehicle_id`, `entry_time_millis`, `exit_time_millis`, `expiry_time_millis`).
- **Test Coverage Expansion**: Added service business-path tests, controller route tests, and a concurrency test for booking race handling.
- **Single Source Tree**: Duplicate non-Maven source mirror under `src/parkinglot` has been retired. Runtime source of truth is `src/main/java`.
- **Rubric-Ready Artifacts**: UML diagrams, pattern mapping, and principle traceability are included under `docs/`.

## Technologies Used

- **Backend**: Java 8+ source compatibility (tested with modern JDK), Spring Boot (Spring Web MVC)
- **Frontend**: HTML5, CSS3 (Vanilla), JavaScript, Thymeleaf templates, Google Inter Font
- **Database**: SQLite (`org.xerial:sqlite-jdbc`)
- **Build Tool**: Maven

## Architecture & Project Structure

The project follows a standard MVC internal structure:
- **`src/main/java/com/team12/parkinglot_web/ParkingController.java`**: Core Spring MVC controller for routing, session-role guards, booking/payment flow, and operations pages.
- **`src/main/java/parkinglot/core/*`**: Core business logic modules including `ParkingLot`, `ParkingFloor`.
- **`src/main/java/parkinglot/spots/*`**: `ParkingSpot` lifecycle with `reserve`, `occupy`, `expire`, and auto-release checks.
- **`src/main/java/parkinglot/db/DatabaseHelper.java`**: SQLite schema/migration logic, ticket lifecycle updates, reconciliation, and active-session lookups.
- **`src/main/resources/templates/`**: The modern glassmorphism Thymeleaf views:
  - `index.html`: Landing page with realistic structural imagery.
  - `login.html` & `register.html`: Authentication portals.
  - `dashboard.html`: Driver portal.
  - `admin_dashboard.html`, `attendant_dashboard.html`, `security_dashboard.html`: Role-specific operations dashboards.
  - `spots.html`: Type-first live map with multi-spot selection, per-spot vehicle inputs, and sticky checkout bar.
  - `checkout.html`: Reservation summary including vehicle type and spot-to-vehicle mapping.
  - `payment.html`: Supported payment tabs (`CREDIT_CARD`, `NET_BANKING`, `CASH`).
  - `bookings.html`: Ticket history interface with status and vehicle mapping.
  - `admin_incidents.html`: Unified operations view including active parking sessions and vehicle lookup.
  - `report_violation.html`: Security violation reporting flow.
  - `gate_override.html`: Security manual gate override logging flow.
  - `lost_ticket.html`: Attendant lost-ticket resolution flow.
- **`src/main/resources/static/images/`**: Contains the local static assets used on the landing page.
- **`src/test/java/com/team12/parkinglot_web/`**: Context-load, controller-route, and service/concurrency tests.

## Detailed Documentation

For full architecture internals, workflow explanations, endpoint mapping, data model details, and usage/run/test guidance, see:

- `documentation.md`
- `DESIGN_PATTERNS_AND_TEAM_SPLIT.md`
- `PROJECT_OVERVIEW.md`
- `RUBRIC_COMPLIANCE.md`

## Prerequisites

- **Java JDK 25** (Ensure `JAVA_HOME` is set exactly to your JDK 25 installation path).
- Maven Wrapper is included, so a standalone Maven installation is optional.

## Seeded Accounts (Created on Startup)

These accounts are seeded if they do not already exist:

- **Admin**: `admin@parkmanager.com` / `Admin@123`
- **Attendant**: `attendant@parkmanager.com` / `Attend@123`
- **Security Guard**: `guard@parkmanager.com` / `Guard@123`

Driver accounts are created via the registration page.

## How to Execute and Run the System

1. **Clone the Repository**
   ```bash
   git clone https://github.com/mihirstag/multi-level-parking-lot-system.git
   ```

2. **Navigate to the Directory**
   ```bash
   cd multi-level-parking-lot-system
   ```

3. **Set your Java Home (Windows PowerShell Example)**
   *If your default system Java is older than 25, force the environment variable for your session:*
   ```powershell
   $env:JAVA_HOME="C:\Program Files\Java\jdk-25.0.2"
   ```

4. **Clean and Run the Application**
   Use the included Maven wrapper to cleanly build and start the Spring Boot server:
   - On **Windows**:
     ```powershell
     .\mvnw clean spring-boot:run
     ```
   - On **Mac/Linux**:
     ```bash
     ./mvnw clean spring-boot:run
     ```

5. **Access the Web App**
   Open your browser and navigate to:
   ```
   http://localhost:8080
   ```

## Run Tests

- Windows:
  ```powershell
  .\mvnw.cmd test
  ```
- Mac/Linux:
  ```bash
  ./mvnw test
  ```

Latest local verification in this workspace: **11 tests, 0 failures, 0 errors**.

## Usage Flow

1. **Explore**: View the landing page to see the stats and high-quality aesthetic.
2. **Register**: Go to `Create Account` and sign up as a new driver.
3. **Login**: Go to `Sign In` with your newly created credentials.
4. **Live Map (Driver)**:
  - Select **vehicle type** first.
  - Pick one or more matching spots.
  - Enter a different vehicle number for each selected spot.
  - Choose hours and click *Review & Pay*.
5. **Checkout + Payment (Driver)**:
  - Review spot-to-vehicle mapping, vehicle type, and computed fee.
  - Pay via `CREDIT_CARD`, `NET_BANKING`, or `CASH`.
6. **My Bookings (Driver)**: View active/past tickets with status, vehicle mapping, and payment details.
7. **Security Operations**:
  - Report violations from **Report Violation**.
  - Log manual gate actions from **Gate Override**.
8. **Attendant Operations**:
  - Resolve lost ticket cases from **Handle Lost Ticket**.
  - Automatically release spot and persist audit case records.
9. **Admin Operations**:
  - Use **Incident Center** to review violations, gate logs, lost-ticket cases, and active sessions.
  - Search active sessions by vehicle ID.

## Rubric Coverage Snapshot

- **Analysis and Design Models**:
  - Use Case Diagram: `docs/diagrams/use_case_diagram.puml`
  - Class Diagram: `docs/diagrams/class_diagram.puml`
  - Activity Diagrams: `docs/diagrams/activity_*.puml`
  - State Diagrams: `docs/diagrams/state_*.puml`
- **MVC Architecture**:
  - Controller: `src/main/java/com/team12/parkinglot_web/ParkingController.java`
  - Service: `src/main/java/com/team12/parkinglot_web/service/ParkingApplicationService.java`
  - Views: `src/main/resources/templates/*.html`
- **Design Patterns (implemented in runtime flow)**:
  - Singleton: `parkinglot/core/ParkingLot.java`
  - Factory: `parkinglot/spots/SpotFactory.java`
  - Observer: `parkinglot/core/DisplayBoard.java`
  - Strategy: `parkinglot/interfaces/PaymentStrategy.java`
  - Adapter (Structural): `com/team12/parkinglot_web/payment/PaymentProcessorAdapter.java`
- **Design Principles (documented and traceable)**:
  - SRP, OCP, DIP, ISP mapped in `RUBRIC_COMPLIANCE.md`
- **Demo/Explanation Support**:
  - End-to-end flow and speaking checklist in `PROJECT_OVERVIEW.md` and `RUBRIC_COMPLIANCE.md`
