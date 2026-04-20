package parkinglot.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.springframework.security.crypto.bcrypt.BCrypt;

import parkinglot.core.ParkingFloor;
import parkinglot.core.ParkingLot;
import parkinglot.spots.ParkingSpot;

public class DatabaseHelper {
    private static final String DB_URL = "jdbc:sqlite:parkinglot.db";
    private static final Logger LOGGER = Logger.getLogger(DatabaseHelper.class.getName());
    public static final String ROLE_DRIVER = "DRIVER";
    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_ATTENDANT = "ATTENDANT";
    public static final String ROLE_SECURITY_GUARD = "SECURITY_GUARD";
    public static final String TICKET_STATUS_RESERVED = "RESERVED";
    public static final String TICKET_STATUS_PAID = "PAID";
    public static final String TICKET_STATUS_OCCUPIED = "OCCUPIED";
    public static final String TICKET_STATUS_EXPIRED = "EXPIRED";
    public static final String TICKET_STATUS_CLOSED = "CLOSED";
    public static final String TICKET_STATUS_LOST = "LOST_TICKET";

    public static Connection connect() {
        Connection conn = null;
        try {
            conn = DriverManager.getConnection(DB_URL);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "DB connection error", e);
        }
        return conn;
    }

    private static Connection requireConnection() throws SQLException {
        Connection connection = connect();
        if (connection == null) {
            throw new SQLException("Unable to establish SQLite connection");
        }
        return connection;
    }

    public static void initializeDatabase() {
        String createDriverTable = "CREATE TABLE IF NOT EXISTS drivers (\n"
                + " id text PRIMARY KEY,\n"
                + " name text NOT NULL,\n"
                + " email text NOT NULL UNIQUE,\n"
                + " phone text,\n"
                + " password text NOT NULL,\n"
                + " license_plate text,\n"
                + " dl_number text,\n"
                + " aadhar_number text,\n"
                + " role text NOT NULL DEFAULT 'DRIVER'\n"
                + ");";

        String createTicketTable = "CREATE TABLE IF NOT EXISTS tickets (\n"
                + " ticket_id text PRIMARY KEY,\n"
                + " user_email text NOT NULL,\n"
                + " spot_id text NOT NULL,\n"
            + " vehicle_id text NOT NULL DEFAULT 'UNKNOWN',\n"
                + " status text NOT NULL,\n"
                + " payment_method text,\n"
                + " bank_code text,\n"
                + " expiry_time_millis INTEGER,\n"
            + " entry_time_millis INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000),\n"
            + " exit_time_millis INTEGER,\n"
                + " timestamp DATETIME DEFAULT CURRENT_TIMESTAMP\n"
                + ");";

        String createViolationTable = "CREATE TABLE IF NOT EXISTS violations (\n"
                + " violation_id text PRIMARY KEY,\n"
                + " reported_by_email text NOT NULL,\n"
                + " vehicle_id text NOT NULL,\n"
                + " spot_id text,\n"
                + " description text NOT NULL,\n"
                + " status text NOT NULL,\n"
                + " timestamp DATETIME DEFAULT CURRENT_TIMESTAMP\n"
                + ");";

        String createGateOverrideTable = "CREATE TABLE IF NOT EXISTS gate_override_logs (\n"
                + " log_id text PRIMARY KEY,\n"
                + " gate_id text NOT NULL,\n"
                + " override_action text NOT NULL,\n"
                + " reason text NOT NULL,\n"
                + " overridden_by_email text NOT NULL,\n"
                + " timestamp DATETIME DEFAULT CURRENT_TIMESTAMP\n"
                + ");";

        String createLostTicketTable = "CREATE TABLE IF NOT EXISTS lost_ticket_cases (\n"
                + " case_id text PRIMARY KEY,\n"
                + " ticket_id text NOT NULL,\n"
                + " spot_id text,\n"
                + " user_email text,\n"
                + " vehicle_id text,\n"
                + " handled_by_email text NOT NULL,\n"
                + " notes text,\n"
                + " timestamp DATETIME DEFAULT CURRENT_TIMESTAMP\n"
                + ");";

        try (Connection conn = requireConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute(createDriverTable);
            stmt.execute(createTicketTable);
            stmt.execute(createViolationTable);
            stmt.execute(createGateOverrideTable);
            stmt.execute(createLostTicketTable);

            ensureColumnExists(conn, "drivers", "role",
                    "ALTER TABLE drivers ADD COLUMN role text NOT NULL DEFAULT 'DRIVER';");

                ensureColumnExists(conn, "tickets", "vehicle_id",
                    "ALTER TABLE tickets ADD COLUMN vehicle_id text NOT NULL DEFAULT 'UNKNOWN';");
                ensureColumnExists(conn, "tickets", "entry_time_millis",
                    "ALTER TABLE tickets ADD COLUMN entry_time_millis INTEGER;");
                ensureColumnExists(conn, "tickets", "exit_time_millis",
                    "ALTER TABLE tickets ADD COLUMN exit_time_millis INTEGER;");

                stmt.execute("UPDATE tickets SET vehicle_id = COALESCE(NULLIF(vehicle_id, ''), 'UNKNOWN');");
                stmt.execute("UPDATE tickets SET entry_time_millis = COALESCE(entry_time_millis, CAST(strftime('%s', timestamp) AS INTEGER) * 1000, CAST(strftime('%s','now') AS INTEGER) * 1000);");

            stmt.execute("CREATE INDEX IF NOT EXISTS idx_drivers_role ON drivers(role);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_tickets_user_email ON tickets(user_email);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_tickets_expiry ON tickets(expiry_time_millis);");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_tickets_vehicle ON tickets(vehicle_id);");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_tickets_status ON tickets(status);");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_tickets_spot_status ON tickets(spot_id, status);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_violations_status ON violations(status);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_violations_reporter ON violations(reported_by_email);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_gate_override_by_user ON gate_override_logs(overridden_by_email);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_lost_ticket_ticket ON lost_ticket_cases(ticket_id);");
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Table creation error", e);
        }
    }

    public static boolean registerDriver(String name, String email, String password) {
        return registerUser(name, email, password, ROLE_DRIVER);
    }

    public static boolean registerUser(String name, String email, String password, String role) {
        String id = "D-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        String hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt(12));
        String sql = "INSERT INTO drivers(id, name, email, phone, password, license_plate, dl_number, aadhar_number, role) VALUES(?,?,?,'N/A',?,'N/A','N/A','N/A',?)";
        String normalizedRole = normalizeRole(role);

        try (Connection conn = requireConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, id);
            pstmt.setString(2, name);
            pstmt.setString(3, email);
            pstmt.setString(4, hashedPassword);
            pstmt.setString(5, normalizedRole);
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Register error", e);
            return false;
        }
    }

    public static void ensureUserAccount(String name, String email, String password, String role) {
        if (userExists(email)) {
            return;
        }

        boolean created = registerUser(name, email, password, role);
        if (!created) {
            LOGGER.log(Level.WARNING, "Unable to seed account for {0}", email);
        }
    }

    public static boolean loginDriver(String email, String password) {
        String sql = "SELECT password FROM drivers WHERE email = ?";

        try (Connection conn = requireConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, email);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (!rs.next()) {
                    return false;
                }

                String storedPassword = rs.getString("password");
                if (storedPassword == null || storedPassword.trim().isEmpty()) {
                    return false;
                }

                if (isBcryptHash(storedPassword)) {
                    return BCrypt.checkpw(password, storedPassword);
                }

                return storedPassword.equals(password);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Login error", e);
            return false;
        }
    }

    public static String getUserRole(String email) {
        String sql = "SELECT role FROM drivers WHERE email = ?";

        try (Connection conn = requireConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, email);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (!rs.next()) {
                    return ROLE_DRIVER;
                }
                return normalizeRole(rs.getString("role"));
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Get role error", e);
            return ROLE_DRIVER;
        }
    }

    private static boolean userExists(String email) {
        String sql = "SELECT 1 FROM drivers WHERE email = ?";

        try (Connection conn = requireConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, email);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "User exists check error", e);
            return false;
        }
    }

    public static void saveTicket(String ticketId, String userEmail, String spotId, String vehicleId, String status,
                                  String paymentMethod, String bankCode, long expiryTimeMillis) {
        saveTicket(ticketId, userEmail, spotId, vehicleId, status, paymentMethod, bankCode, expiryTimeMillis,
                System.currentTimeMillis());
    }

    public static void saveTicket(String ticketId, String userEmail, String spotId, String vehicleId, String status,
                                  String paymentMethod, String bankCode, long expiryTimeMillis,
                                  long entryTimeMillis) {
        String sql = "INSERT INTO tickets(ticket_id, user_email, spot_id, vehicle_id, status, payment_method, bank_code, expiry_time_millis, entry_time_millis) VALUES(?,?,?,?,?,?,?,?,?)";

        try (Connection conn = requireConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, ticketId);
            pstmt.setString(2, userEmail);
            pstmt.setString(3, spotId);
            pstmt.setString(4, normalizeVehicleId(vehicleId));
            pstmt.setString(5, normalizeTicketStatus(status));
            pstmt.setString(6, paymentMethod);
            pstmt.setString(7, bankCode);
            pstmt.setLong(8, expiryTimeMillis);
            pstmt.setLong(9, entryTimeMillis);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Save ticket error", e);
        }
    }

    public static void updateTicketStatus(String spotId, String status) {
        String normalizedStatus = normalizeTicketStatus(status);
        boolean terminalStatus = isTerminalStatus(normalizedStatus);
        String sql = terminalStatus
                ? "UPDATE tickets SET status = ?, exit_time_millis = COALESCE(exit_time_millis, ?) WHERE spot_id = ? AND status IN (?, ?, ?)"
                : "UPDATE tickets SET status = ? WHERE spot_id = ? AND status IN (?, ?, ?)";

        try (Connection conn = requireConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            if (terminalStatus) {
                pstmt.setString(1, normalizedStatus);
                pstmt.setLong(2, System.currentTimeMillis());
                pstmt.setString(3, spotId);
                pstmt.setString(4, TICKET_STATUS_RESERVED);
                pstmt.setString(5, TICKET_STATUS_PAID);
                pstmt.setString(6, TICKET_STATUS_OCCUPIED);
            } else {
                pstmt.setString(1, normalizedStatus);
                pstmt.setString(2, spotId);
                pstmt.setString(3, TICKET_STATUS_RESERVED);
                pstmt.setString(4, TICKET_STATUS_PAID);
                pstmt.setString(5, TICKET_STATUS_OCCUPIED);
            }
            pstmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Update ticket status error", e);
        }
    }

    public static boolean updateTicketStatusByTicketId(String ticketId, String status) {
        String normalizedStatus = normalizeTicketStatus(status);
        boolean terminalStatus = isTerminalStatus(normalizedStatus);
        String sql = terminalStatus
                ? "UPDATE tickets SET status = ?, exit_time_millis = COALESCE(exit_time_millis, ?) WHERE ticket_id = ?"
                : "UPDATE tickets SET status = ? WHERE ticket_id = ?";

        try (Connection conn = requireConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            if (terminalStatus) {
                pstmt.setString(1, normalizedStatus);
                pstmt.setLong(2, System.currentTimeMillis());
                pstmt.setString(3, ticketId);
            } else {
                pstmt.setString(1, normalizedStatus);
                pstmt.setString(2, ticketId);
            }
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Update ticket by id error", e);
            return false;
        }
    }

    public static Map<String, String> getTicketById(String ticketId) {
        String sql = "SELECT ticket_id, user_email, spot_id, vehicle_id, status, payment_method, bank_code, timestamp, expiry_time_millis, entry_time_millis, exit_time_millis FROM tickets WHERE ticket_id = ?";

        try (Connection conn = requireConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, ticketId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return mapTicketRow(rs);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Get ticket by id error", e);
            return null;
        }
    }

    public static Map<String, String> findLatestTicketByUserAndSpot(String userEmail, String spotId) {
        String sql = "SELECT ticket_id, user_email, spot_id, vehicle_id, status, payment_method, bank_code, timestamp, expiry_time_millis, entry_time_millis, exit_time_millis "
            + "FROM tickets WHERE user_email = ? AND spot_id = ? ORDER BY entry_time_millis DESC, timestamp DESC LIMIT 1";

        try (Connection conn = requireConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, userEmail);
            pstmt.setString(2, spotId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return mapTicketRow(rs);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Find ticket by user and spot error", e);
            return null;
        }
    }

    public static List<Map<String, String>> getTicketsForUser(String email) {
        List<Map<String, String>> tickets = new ArrayList<>();
        String sql = "SELECT ticket_id, user_email, spot_id, vehicle_id, status, payment_method, bank_code, timestamp, expiry_time_millis, entry_time_millis, exit_time_millis FROM tickets WHERE user_email = ? ORDER BY entry_time_millis DESC, timestamp DESC";

        try (Connection conn = requireConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, email);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    tickets.add(mapTicketRow(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Get tickets for user error", e);
        }

        return tickets;
    }

    public static int reconcileExpiredTickets(long currentMillis) {
        String sql = "UPDATE tickets SET status = ?, exit_time_millis = COALESCE(exit_time_millis, ?) "
                + "WHERE status IN (?, ?, ?) AND expiry_time_millis IS NOT NULL AND expiry_time_millis <= ?";

        try (Connection conn = requireConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, TICKET_STATUS_EXPIRED);
            pstmt.setLong(2, currentMillis);
            pstmt.setString(3, TICKET_STATUS_RESERVED);
            pstmt.setString(4, TICKET_STATUS_PAID);
            pstmt.setString(5, TICKET_STATUS_OCCUPIED);
            pstmt.setLong(6, currentMillis);
            return pstmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Reconcile expired tickets error", e);
            return 0;
        }
    }

    public static List<Map<String, String>> getActiveParkingSessions(int limit) {
        List<Map<String, String>> sessions = new ArrayList<>();
        int safeLimit = limit <= 0 ? 20 : Math.min(limit, 200);
        long now = System.currentTimeMillis();
        String sql = "SELECT ticket_id, user_email, spot_id, vehicle_id, status, payment_method, bank_code, timestamp, expiry_time_millis, entry_time_millis, exit_time_millis "
                + "FROM tickets WHERE status IN (?, ?, ?) AND (expiry_time_millis IS NULL OR expiry_time_millis > ?) "
                + "ORDER BY entry_time_millis ASC, timestamp ASC LIMIT ?";

        try (Connection conn = requireConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, TICKET_STATUS_RESERVED);
            pstmt.setString(2, TICKET_STATUS_PAID);
            pstmt.setString(3, TICKET_STATUS_OCCUPIED);
            pstmt.setLong(4, now);
            pstmt.setInt(5, safeLimit);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    sessions.add(mapTicketRow(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Get active parking sessions error", e);
        }

        return sessions;
    }

    public static Map<String, String> findActiveSessionByVehicle(String vehicleId) {
        String normalizedVehicle = normalizeVehicleId(vehicleId);
        long now = System.currentTimeMillis();
        String sql = "SELECT ticket_id, user_email, spot_id, vehicle_id, status, payment_method, bank_code, timestamp, expiry_time_millis, entry_time_millis, exit_time_millis "
                + "FROM tickets WHERE vehicle_id = ? AND status IN (?, ?, ?) AND (expiry_time_millis IS NULL OR expiry_time_millis > ?) "
                + "ORDER BY entry_time_millis DESC, timestamp DESC LIMIT 1";

        try (Connection conn = requireConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, normalizedVehicle);
            pstmt.setString(2, TICKET_STATUS_RESERVED);
            pstmt.setString(3, TICKET_STATUS_PAID);
            pstmt.setString(4, TICKET_STATUS_OCCUPIED);
            pstmt.setLong(5, now);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return mapTicketRow(rs);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Find active session by vehicle error", e);
            return null;
        }
    }

    public static String saveViolation(String reportedByEmail, String vehicleId, String spotId, String description) {
        String violationId = compactUuid("VIO");
        String sql = "INSERT INTO violations(violation_id, reported_by_email, vehicle_id, spot_id, description, status) VALUES(?,?,?,?,?,?)";

        try (Connection conn = requireConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, violationId);
            pstmt.setString(2, reportedByEmail);
            pstmt.setString(3, vehicleId);
            pstmt.setString(4, spotId == null || spotId.isEmpty() ? null : spotId);
            pstmt.setString(5, description);
            pstmt.setString(6, "OPEN");
            pstmt.executeUpdate();
            return violationId;
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Save violation error", e);
            return null;
        }
    }

    public static List<Map<String, String>> getViolations() {
        List<Map<String, String>> violations = new ArrayList<>();
        String sql = "SELECT violation_id, reported_by_email, vehicle_id, spot_id, description, status, timestamp FROM violations ORDER BY timestamp DESC";

        try (Connection conn = requireConnection(); PreparedStatement pstmt = conn.prepareStatement(sql);
                ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                Map<String, String> violation = new HashMap<>();
                violation.put("violationId", rs.getString("violation_id"));
                violation.put("reportedBy", rs.getString("reported_by_email"));
                violation.put("vehicleId", rs.getString("vehicle_id"));
                violation.put("spotId", rs.getString("spot_id"));
                violation.put("description", rs.getString("description"));
                violation.put("status", rs.getString("status"));
                violation.put("timestamp", rs.getString("timestamp"));
                violations.add(violation);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Get violations error", e);
        }

        return violations;
    }

    public static String saveGateOverrideLog(String gateId, String action, String reason, String overriddenByEmail) {
        String logId = compactUuid("GATE");
        String sql = "INSERT INTO gate_override_logs(log_id, gate_id, override_action, reason, overridden_by_email) VALUES(?,?,?,?,?)";

        try (Connection conn = requireConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, logId);
            pstmt.setString(2, gateId);
            pstmt.setString(3, action);
            pstmt.setString(4, reason);
            pstmt.setString(5, overriddenByEmail);
            pstmt.executeUpdate();
            return logId;
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Save gate override log error", e);
            return null;
        }
    }

    public static List<Map<String, String>> getGateOverrideLogs(int limit) {
        List<Map<String, String>> logs = new ArrayList<>();
        int safeLimit = limit <= 0 ? 20 : Math.min(limit, 200);
        String sql = "SELECT log_id, gate_id, override_action, reason, overridden_by_email, timestamp FROM gate_override_logs ORDER BY timestamp DESC LIMIT ?";

        try (Connection conn = requireConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, safeLimit);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, String> log = new HashMap<>();
                    log.put("logId", rs.getString("log_id"));
                    log.put("gateId", rs.getString("gate_id"));
                    log.put("action", rs.getString("override_action"));
                    log.put("reason", rs.getString("reason"));
                    log.put("overriddenBy", rs.getString("overridden_by_email"));
                    log.put("timestamp", rs.getString("timestamp"));
                    logs.add(log);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Get gate override logs error", e);
        }

        return logs;
    }

    public static String saveLostTicketCase(String ticketId, String spotId, String userEmail, String vehicleId,
            String handledByEmail, String notes) {
        String caseId = compactUuid("LTC");
        String sql = "INSERT INTO lost_ticket_cases(case_id, ticket_id, spot_id, user_email, vehicle_id, handled_by_email, notes) VALUES(?,?,?,?,?,?,?)";

        try (Connection conn = requireConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, caseId);
            pstmt.setString(2, ticketId);
            pstmt.setString(3, spotId);
            pstmt.setString(4, userEmail);
            pstmt.setString(5, vehicleId == null || vehicleId.isEmpty() ? null : vehicleId);
            pstmt.setString(6, handledByEmail);
            pstmt.setString(7, notes == null || notes.isEmpty() ? null : notes);
            pstmt.executeUpdate();
            return caseId;
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Save lost ticket case error", e);
            return null;
        }
    }

    public static List<Map<String, String>> getLostTicketCases(int limit) {
        List<Map<String, String>> cases = new ArrayList<>();
        int safeLimit = limit <= 0 ? 20 : Math.min(limit, 200);
        String sql = "SELECT case_id, ticket_id, spot_id, user_email, vehicle_id, handled_by_email, notes, timestamp FROM lost_ticket_cases ORDER BY timestamp DESC LIMIT ?";

        try (Connection conn = requireConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, safeLimit);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, String> lostCase = new HashMap<>();
                    lostCase.put("caseId", rs.getString("case_id"));
                    lostCase.put("ticketId", rs.getString("ticket_id"));
                    lostCase.put("spotId", rs.getString("spot_id"));
                    lostCase.put("userEmail", rs.getString("user_email"));
                    lostCase.put("vehicleId", rs.getString("vehicle_id"));
                    lostCase.put("handledBy", rs.getString("handled_by_email"));
                    lostCase.put("notes", rs.getString("notes"));
                    lostCase.put("timestamp", rs.getString("timestamp"));
                    cases.add(lostCase);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Get lost ticket cases error", e);
        }

        return cases;
    }

    public static void syncActiveSpots(ParkingLot lot) {
        long currentMillis = System.currentTimeMillis();
        reconcileExpiredTickets(currentMillis);
        String sql = "SELECT spot_id, status, MAX(expiry_time_millis) AS expiry_time_millis "
                + "FROM tickets WHERE status IN (?, ?, ?) AND expiry_time_millis > ? GROUP BY spot_id, status";

        try (Connection conn = requireConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, TICKET_STATUS_RESERVED);
            pstmt.setString(2, TICKET_STATUS_PAID);
            pstmt.setString(3, TICKET_STATUS_OCCUPIED);
            pstmt.setLong(4, currentMillis);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String spotId = rs.getString("spot_id");
                    String status = rs.getString("status");
                    long expiryMillis = rs.getLong("expiry_time_millis");
                    int remainingHours = (int) Math.ceil((expiryMillis - currentMillis) / (1000.0 * 60 * 60));

                    if (remainingHours <= 0) {
                        continue;
                    }

                    for (ParkingFloor floor : lot.getFloors()) {
                        for (ParkingSpot spot : floor.getSpots()) {
                            if (spot.getId().equals(spotId) && spot.isFree()) {
                                if (TICKET_STATUS_OCCUPIED.equalsIgnoreCase(status)
                                        || TICKET_STATUS_PAID.equalsIgnoreCase(status)) {
                                    spot.occupy(remainingHours);
                                } else {
                                    spot.reserve(remainingHours);
                                }
                                break;
                            }
                        }
                    }
                }
            }

            for (ParkingFloor floor : lot.getFloors()) {
                floor.updateDisplay();
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Sync active spots error", e);
        }
    }

    private static Map<String, String> mapTicketRow(ResultSet rs) throws SQLException {
        Map<String, String> ticket = new HashMap<>();
        ticket.put("ticketId", rs.getString("ticket_id"));
        ticket.put("userEmail", rs.getString("user_email"));
        ticket.put("spotId", rs.getString("spot_id"));
        ticket.put("vehicleId", normalizeVehicleId(rs.getString("vehicle_id")));
        ticket.put("status", rs.getString("status"));
        ticket.put("paymentMethod", rs.getString("payment_method"));
        ticket.put("bankCode", rs.getString("bank_code"));
        ticket.put("timestamp", rs.getString("timestamp"));
        ticket.put("expiryTimeMillis", String.valueOf(rs.getLong("expiry_time_millis")));

        long entryTimeMillis = rs.getLong("entry_time_millis");
        if (rs.wasNull()) {
            entryTimeMillis = 0L;
        }
        long exitTimeMillis = rs.getLong("exit_time_millis");
        if (rs.wasNull()) {
            exitTimeMillis = 0L;
        }

        ticket.put("entryTimeMillis", entryTimeMillis > 0 ? String.valueOf(entryTimeMillis) : "");
        ticket.put("exitTimeMillis", exitTimeMillis > 0 ? String.valueOf(exitTimeMillis) : "");
        return ticket;
    }

    private static void ensureColumnExists(Connection conn, String tableName, String columnName, String alterSql)
            throws SQLException {
        if (hasColumn(conn, tableName, columnName)) {
            return;
        }

        try (Statement stmt = conn.createStatement()) {
            stmt.execute(alterSql);
        }
    }

    private static boolean hasColumn(Connection conn, String tableName, String columnName) throws SQLException {
        String sql = "PRAGMA table_info(" + tableName + ")";
        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                if (columnName.equalsIgnoreCase(rs.getString("name"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String compactUuid(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private static String normalizeVehicleId(String vehicleId) {
        if (vehicleId == null || vehicleId.trim().isEmpty()) {
            return "UNKNOWN";
        }
        return vehicleId.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizeTicketStatus(String status) {
        if (status == null || status.trim().isEmpty()) {
            return TICKET_STATUS_RESERVED;
        }

        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (TICKET_STATUS_RESERVED.equals(normalized)
                || TICKET_STATUS_PAID.equals(normalized)
                || TICKET_STATUS_OCCUPIED.equals(normalized)
                || TICKET_STATUS_EXPIRED.equals(normalized)
                || TICKET_STATUS_CLOSED.equals(normalized)
                || TICKET_STATUS_LOST.equals(normalized)) {
            return normalized;
        }
        return normalized;
    }

    private static boolean isTerminalStatus(String status) {
        return TICKET_STATUS_EXPIRED.equals(status)
                || TICKET_STATUS_CLOSED.equals(status)
                || TICKET_STATUS_LOST.equals(status);
    }

    private static String normalizeRole(String role) {
        if (role == null) {
            return ROLE_DRIVER;
        }

        String normalized = role.trim().toUpperCase(Locale.ROOT);
        if (ROLE_ADMIN.equals(normalized) || ROLE_ATTENDANT.equals(normalized)
                || ROLE_SECURITY_GUARD.equals(normalized) || ROLE_DRIVER.equals(normalized)) {
            return normalized;
        }
        return ROLE_DRIVER;
    }

    private static boolean isBcryptHash(String value) {
        return value.startsWith("$2a$") || value.startsWith("$2b$") || value.startsWith("$2y$");
    }
}
