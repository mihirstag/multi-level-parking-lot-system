package parkinglot.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.UUID;

public class DatabaseHelper {
    private static final String DB_URL = "jdbc:sqlite:parkinglot.db";

    public static Connection connect() {
        Connection conn = null;
        try {
            conn = DriverManager.getConnection(DB_URL);
        } catch (SQLException e) {
            System.out.println("DB Connection Error: " + e.getMessage());
        }
        return conn;
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
                + " spot_id text NOT NULL,\n"
                + " status text NOT NULL,\n"
                + " reservation_expiry DATETIME,\n" 
                + " timestamp DATETIME DEFAULT CURRENT_TIMESTAMP\n"
                + ");";

        String createPaymentTable = "CREATE TABLE IF NOT EXISTS payments (\n"
            + " transaction_id text PRIMARY KEY,\n"
            + " ticket_id text NOT NULL,\n"
            + " amount real NOT NULL,\n"
            + " timestamp text NOT NULL\n"
            + ");";

        try (Connection conn = connect(); Statement stmt = conn.createStatement()) {
            stmt.execute(createDriverTable);
            stmt.execute(createTicketTable);
        } catch (SQLException e) {
            System.out.println("Table Creation Error: " + e.getMessage());
        }
    }

    // --- NEW: Register a new User ---
    public static boolean registerDriver(String name, String email, String password) {
        String id = "D-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        // Filling missing info with placeholders for now since UI only asks for Name, Email, Password
        String sql = "INSERT INTO drivers(id, name, email, phone, password, license_plate, dl_number, aadhar_number) VALUES(?,?,?,'N/A',?,'N/A','N/A','N/A')";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, id);
            pstmt.setString(2, name);
            pstmt.setString(3, email);
            pstmt.setString(4, password);
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.out.println("Register Error (Email might exist): " + e.getMessage());
            return false;
        }
    }

    // --- NEW: Verify User Login ---
    public static boolean loginDriver(String email, String password) {
        String sql = "SELECT id FROM drivers WHERE email = ? AND password = ?";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, email);
            pstmt.setString(2, password);
            ResultSet rs = pstmt.executeQuery();
            return rs.next(); // Returns true if a match is found
        } catch (SQLException e) {
            return false;
        }
    }

    // --- Ticket Methods (From previous steps) ---
    public static void saveTicket(String ticketId, String spotId, String status) {
        String sql = "INSERT INTO tickets(ticket_id, spot_id, status) VALUES(?,?,?)";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, ticketId);
            pstmt.setString(2, spotId);
            pstmt.setString(3, status);
            pstmt.executeUpdate();
        } catch (SQLException e) {}
    }

    // --- NEW: Save Reservation (temporary hold until payment) ---
    public static void saveReservation(String ticketId, String spotId) {
        String sql = "INSERT INTO tickets(ticket_id, spot_id, status) VALUES(?,?,?)";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, ticketId);
            pstmt.setString(2, spotId);
            pstmt.setString(3, "RESERVED");
            pstmt.executeUpdate();
            System.out.println("✓ Spot " + spotId + " reserved (waiting for payment)");
        } catch (SQLException e) {
            System.out.println("Reservation Error: " + e.getMessage());
        }
    }

    // --- NEW: Clear reservation after payment (removes RESERVED record so spot becomes available again) ---
    public static void clearReservation(String spotId) {
        String deleteSql = "DELETE FROM tickets WHERE spot_id = ? AND status = 'RESERVED'";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(deleteSql)) {
            pstmt.setString(1, spotId);
            pstmt.executeUpdate();
            System.out.println("✓ Cleared reservation for spot " + spotId + " after payment");
        } catch (SQLException e) {
            System.out.println("Reservation Clear Error: " + e.getMessage());
        }
    }
    
    
    //
    public static void savePayment(String ticketId, double amount) {
    String sql = "INSERT INTO payments(transaction_id, ticket_id, amount, timestamp) VALUES(?,?,?,?)";
    try (Connection conn = connect();
         PreparedStatement pstmt = conn.prepareStatement(sql)) {
        pstmt.setString(1, UUID.randomUUID().toString());
        pstmt.setString(2, ticketId);
        pstmt.setDouble(3, amount);
        pstmt.setString(4, LocalDateTime.now().toString());
        pstmt.executeUpdate();
    } catch (SQLException e) {
        System.out.println(e.getMessage());
    }
    }

    // --- NEW: Get all reserved spots from database ---
    public static java.util.List<String> getReservedSpots() {
        java.util.List<String> reservedSpots = new java.util.ArrayList<>();
        String sql = "SELECT DISTINCT spot_id FROM tickets WHERE status = 'RESERVED'";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                reservedSpots.add(rs.getString("spot_id"));
            }
        } catch (SQLException e) {
            System.out.println("Error fetching reserved spots: " + e.getMessage());
        }
        return reservedSpots;
    }
    
    // --- NEW: Get all paid spots from database ---
    public static java.util.List<String> getPaidSpots() {
        java.util.List<String> paidSpots = new java.util.ArrayList<>();
        String sql = "SELECT DISTINCT spot_id FROM tickets WHERE status = 'PAID'";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                paidSpots.add(rs.getString("spot_id"));
            }
        } catch (SQLException e) {
            System.out.println("Error fetching paid spots: " + e.getMessage());
        }
        return paidSpots;
    }
    
    // --- NEW: Get ticketId for a reserved spot ---
    public static String getReservedTicketId(String spotId) {
        String sql = "SELECT ticket_id FROM tickets WHERE spot_id = ? AND status = 'RESERVED' LIMIT 1";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, spotId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getString("ticket_id");
            }
        } catch (SQLException e) {
            System.out.println("Error getting reserved ticket: " + e.getMessage());
        }
        return null;
    }

    //
    public static void updateTicketStatus(String spotId, String status) {
        String sql = "UPDATE tickets SET status = ? WHERE spot_id = ? AND status = 'RESERVED'";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, status);
            pstmt.setString(2, spotId);
            pstmt.executeUpdate();
        } catch (SQLException e) {}
    }
}