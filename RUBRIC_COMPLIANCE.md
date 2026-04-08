# Rubric Compliance Matrix (10 Marks)

This file maps project artifacts to the evaluation rubric.

## 1) Analysis and Design Models (2 marks)

- Use Case Diagram: `docs/diagrams/use_case_diagram.puml`
- Class Diagram: `docs/diagrams/class_diagram.puml`
- Activity Diagrams:
  - `docs/diagrams/activity_authentication.puml`
  - `docs/diagrams/activity_booking_checkout.puml`
  - `docs/diagrams/activity_payment_processing.puml`
  - `docs/diagrams/activity_booking_history.puml`
- State Diagrams:
  - `docs/diagrams/state_parking_spot.puml`
  - `docs/diagrams/state_ticket_lifecycle.puml`
  - `docs/diagrams/state_payment_flow.puml`
  - `docs/diagrams/state_session_flow.puml`

## 2) Use of MVC Architecture Pattern (2 marks)

- Controller layer: `src/main/java/com/team12/parkinglot_web/ParkingController.java`
- Service layer: `src/main/java/com/team12/parkinglot_web/service/ParkingApplicationService.java`
- View layer: `src/main/resources/templates/*.html`
- Domain/core layer: `src/main/java/parkinglot/*`

## 3) Use of Design Principles and Patterns (3 marks)

### Design patterns

- Singleton: `src/main/java/parkinglot/core/ParkingLot.java`
- Factory: `src/main/java/parkinglot/spots/SpotFactory.java`
- Observer: `src/main/java/parkinglot/core/DisplayBoard.java`, `src/main/java/parkinglot/interfaces/Observer.java`
- Strategy: `src/main/java/parkinglot/interfaces/PaymentStrategy.java`, `src/main/java/parkinglot/transactions/*.java`
- Adapter (Structural): `src/main/java/com/team12/parkinglot_web/payment/PaymentProcessorAdapter.java`

### Design principles

- SRP:
  - Request routing in controller only.
  - Business workflow in service only.
  - Payment request translation/validation in adapter only.
- OCP:
  - Extend payment methods by adding new `PaymentStrategy` implementations.
- DIP:
  - Service calls payment abstraction through adapter and strategy, not gateway internals.
- ISP:
  - Small interfaces: `PaymentStrategy`, `Observer`.

## 4) Presentation / Demo / Explaining the code (3 marks)

Suggested demo sequence:

1. Show architecture flow: Controller -> Service -> Domain -> DB -> View.
2. Show authentication flow with BCrypt-backed login.
3. Show spot selection and checkout summary.
4. Show payment flow via adapter and strategy.
5. Show booking persistence and bookings page.
6. Show key diagrams from `docs/diagrams/`.

## Build and Verification Commands

- Build and test:
  - `./mvnw test` (Linux/macOS)
  - `.\mvnw.cmd test` (Windows)
- Full verify:
  - `./mvnw verify` (Linux/macOS)
  - `.\mvnw.cmd verify` (Windows)
