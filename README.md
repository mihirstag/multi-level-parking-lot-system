# Park Manager

A full-stack Spring Boot web application for managing a modern, multi-level parking facility. This system features a stunning, state-of-the-art dark glassmorphism UI. It allows drivers to register, log in, view available parking spots across different floors in real-time, book spots with dynamic time-based expiry, process payments, and track their active parking tickets.

## Features

- **Premium UI / UX**: A complete dark-theme aesthetic featuring glassmorphism elements, neon accents, and unified responsive navigation across all pages.
- **User Authentication**: Secure driver registration and login portal with session tracking.
- **Credential Security**: Passwords are stored using BCrypt hashing and verified securely during login.
- **Interactive Dashboard**: Personalized dashboard for logged-in drivers showing their email and active status.
- **Real-Time Spot Status & Expiry**: 
  - View available spots across multiple floors on the Live Map.
  - ***Dynamic DOM Rendering***: Booked spots are physically removed from the UI to prevent clutter.
  - ***Time-based Expiry***: Spots automatically free themselves after the booked duration (hours) expires.
  - ***Cross-Session Persistence***: Active bookings are synchronized from the database on startup, ensuring the Live Map remains perfectly accurate even if the server restarts.
- **Diverse Spot Types**: Supports various spot types including Compact, Large, Handicapped, and EV Charging spots.
- **Booking & Checkout System**: 
  - Select single or multiple parking spots dynamically.
  - Specify parking duration (1-24 hours).
  - Automatic fee calculation (e.g., ₹50/hour per spot).
- **Payment Processing**: Process upfront payments and instantly generate parking tickets linked to the user's account.
- **Reliable Booking Semantics**: Booking and payment flows use synchronized reservation handling to prevent duplicate spot assignment under concurrent requests.
- **My Bookings Portal**: A dedicated page for users to view their active and past parking tickets, complete with timestamps and status tracking.
- **Database Integration**: Embedded SQLite database (`parkinglot.db`) for persistent storage of users and tickets. Tracks explicit `expiry_time_millis` for all bookings to enable accurate cross-session server synchronization.
- **Rubric-Ready Artifacts**: UML diagrams, pattern mapping, and principle traceability are included under `docs/`.

## Technologies Used

- **Backend**: Java 8+ source compatibility (tested with modern JDK), Spring Boot (Spring Web MVC)
- **Frontend**: HTML5, CSS3 (Vanilla), JavaScript, Thymeleaf templates, Google Inter Font
- **Database**: SQLite (`org.xerial:sqlite-jdbc`)
- **Build Tool**: Maven

## Architecture & Project Structure

The project follows a standard MVC internal structure:
- **`src/main/java/com/team12/parkinglot_web/ParkingController.java`**: The core Spring MVC controller. Handles routing, session management, booking, multi-spot checkout logic, and rendering the Thymeleaf views.
- **`src/main/java/parkinglot/core/*`**: Core business logic modules including `ParkingLot`, `ParkingFloor`.
- **`src/main/java/parkinglot/spots/*`**: Definition of `ParkingSpot` including the core time-based `isFree()` expiry logic.
- **`src/main/java/parkinglot/db/DatabaseHelper.java`**: Manages all raw SQLite operations for managing the `drivers` and `tickets` tables.
- **`src/main/resources/templates/`**: The modern glassmorphism Thymeleaf views:
  - `index.html`: Landing page with realistic structural imagery.
  - `login.html` & `register.html`: Authentication portals.
  - `dashboard.html`: The main user portal.
  - `spots.html`: The interactive Live Map highlighting available spots and featuring a sticky checkout bar.
  - `checkout.html`: The payment summary screen.
  - `bookings.html`: The ticket tracking interface.
- **`src/main/resources/static/images/`**: Contains the local static assets used on the landing page.

## Detailed Documentation

For full architecture internals, workflow explanations, endpoint mapping, data model details, and usage/run/test guidance, see:

- `documentation.md`

## Prerequisites

- **Java JDK 25** (Ensure `JAVA_HOME` is set exactly to your JDK 25 installation path).
- Maven Wrapper is included, so a standalone Maven installation is optional.

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

## Usage Flow

1. **Explore**: View the landing page to see the stats and high-quality aesthetic.
2. **Register**: Go to `Create Account` and sign up as a new driver.
3. **Login**: Go to `Sign In` with your newly created credentials.
4. **Live Map**: Click on **Live Map** in your unified top navigation bar. Select one or more spots, choose how many hours you need them for, and click *Review & Pay*.
5. **Checkout**: Review your selected slots and total fee. Click *Confirm Payment*.
6. **My Bookings**: Navigate to the **My Bookings** tab to view your active parking tickets, showing the exact spots and timestamps. Wait for your booked hours to pass to see the spots reappear on the Live Map!

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
