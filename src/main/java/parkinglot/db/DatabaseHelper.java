package parkinglot.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
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
                + " user_email text NOT NULL,\n"
                + " spot_id text NOT NULL,\n"
                + " status text NOT NULL,\n" 
                + " timestamp DATETIME DEFAULT CURRENT_TIMESTAMP\n"
                + ");";

        try (Connection conn = connect(); Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS tickets"); // Reset for schema update
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
    public static void saveTicket(String ticketId, String userEmail, String spotId, String status) {
        String sql = "INSERT INTO tickets(ticket_id, user_email, spot_id, status) VALUES(?,?,?,?)";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, ticketId);
            pstmt.setString(2, userEmail);
            pstmt.setString(3, spotId);
            pstmt.setString(4, status);
            pstmt.executeUpdate();
        } catch (SQLException e) {}
    }

    public static void updateTicketStatus(String spotId, String status) {
        String sql = "UPDATE tickets SET status = ? WHERE spot_id = ? AND status = 'RESERVED'";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, status);
            pstmt.setString(2, spotId);
            pstmt.executeUpdate();
        } catch (SQLException e) {}
    }

    public static java.util.List<java.util.Map<String, String>> getTicketsForUser(String email) {
        java.util.List<java.util.Map<String, String>> tickets = new java.util.ArrayList<>();
        String sql = "SELECT ticket_id, spot_id, status, timestamp FROM tickets WHERE user_email = ? ORDER BY timestamp DESC";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, email);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                java.util.Map<String, String> ticket = new java.util.HashMap<>();
                ticket.put("ticketId", rs.getString("ticket_id"));
                ticket.put("spotId", rs.getString("spot_id"));
                ticket.put("status", rs.getString("status"));
                ticket.put("timestamp", rs.getString("timestamp"));
                tickets.add(ticket);
            }
        } catch (SQLException e) {}
        return tickets;
    }
}