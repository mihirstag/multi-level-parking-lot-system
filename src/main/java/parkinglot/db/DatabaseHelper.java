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
                + " aadhar_number text\n"
                + ");";

        String createTicketTable = "CREATE TABLE IF NOT EXISTS tickets (\n"
                + " ticket_id text PRIMARY KEY,\n"
                + " user_email text NOT NULL,\n"
                + " spot_id text NOT NULL,\n"
                + " status text NOT NULL,\n"
                + " payment_method text,\n"
                + " bank_code text,\n"
                + " expiry_time_millis INTEGER,\n"
                + " timestamp DATETIME DEFAULT CURRENT_TIMESTAMP\n"
                + ");";

        try (Connection conn = requireConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute(createDriverTable);
            stmt.execute(createTicketTable);
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_tickets_user_email ON tickets(user_email);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_tickets_expiry ON tickets(expiry_time_millis);");
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Table creation error", e);
        }
    }

    public static boolean registerDriver(String name, String email, String password) {
        String id = "D-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        String hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt(12));
        String sql = "INSERT INTO drivers(id, name, email, phone, password, license_plate, dl_number, aadhar_number) VALUES(?,?,?,'N/A',?,'N/A','N/A','N/A')";

        try (Connection conn = requireConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, id);
            pstmt.setString(2, name);
            pstmt.setString(3, email);
            pstmt.setString(4, hashedPassword);
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Register error", e);
            return false;
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

    public static void saveTicket(String ticketId, String userEmail, String spotId, String status, String paymentMethod,
                                  String bankCode, long expiryTimeMillis) {
        String sql = "INSERT INTO tickets(ticket_id, user_email, spot_id, status, payment_method, bank_code, expiry_time_millis) VALUES(?,?,?,?,?,?,?)";

        try (Connection conn = requireConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, ticketId);
            pstmt.setString(2, userEmail);
            pstmt.setString(3, spotId);
            pstmt.setString(4, status);
            pstmt.setString(5, paymentMethod);
            pstmt.setString(6, bankCode);
            pstmt.setLong(7, expiryTimeMillis);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Save ticket error", e);
        }
    }

    public static void updateTicketStatus(String spotId, String status) {
        String sql = "UPDATE tickets SET status = ? WHERE spot_id = ? AND status = 'RESERVED'";

        try (Connection conn = requireConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, status);
            pstmt.setString(2, spotId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Update ticket status error", e);
        }
    }

    public static List<Map<String, String>> getTicketsForUser(String email) {
        List<Map<String, String>> tickets = new ArrayList<>();
        String sql = "SELECT ticket_id, spot_id, status, payment_method, bank_code, timestamp, expiry_time_millis FROM tickets WHERE user_email = ? ORDER BY timestamp DESC";

        try (Connection conn = requireConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, email);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, String> ticket = new HashMap<>();
                    ticket.put("ticketId", rs.getString("ticket_id"));
                    ticket.put("spotId", rs.getString("spot_id"));
                    ticket.put("status", rs.getString("status"));
                    ticket.put("paymentMethod", rs.getString("payment_method"));
                    ticket.put("bankCode", rs.getString("bank_code"));
                    ticket.put("timestamp", rs.getString("timestamp"));
                    ticket.put("expiryTimeMillis", String.valueOf(rs.getLong("expiry_time_millis")));
                    tickets.add(ticket);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Get tickets for user error", e);
        }

        return tickets;
    }

    public static void syncActiveSpots(ParkingLot lot) {
        long currentMillis = System.currentTimeMillis();
        String sql = "SELECT spot_id, MAX(expiry_time_millis) AS expiry_time_millis FROM tickets WHERE expiry_time_millis > ? GROUP BY spot_id";

        try (Connection conn = requireConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, currentMillis);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String spotId = rs.getString("spot_id");
                    long expiryMillis = rs.getLong("expiry_time_millis");
                    int remainingHours = (int) Math.ceil((expiryMillis - currentMillis) / (1000.0 * 60 * 60));

                    if (remainingHours <= 0) {
                        continue;
                    }

                    for (ParkingFloor floor : lot.getFloors()) {
                        for (ParkingSpot spot : floor.getSpots()) {
                            if (spot.getId().equals(spotId) && spot.isFree()) {
                                spot.book(remainingHours);
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

    private static boolean isBcryptHash(String value) {
        return value.startsWith("$2a$") || value.startsWith("$2b$") || value.startsWith("$2y$");
    }
}
