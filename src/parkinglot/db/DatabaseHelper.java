package parkinglot.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseHelper {
    // The database file will be created in your root project folder
    private static final String DB_URL = "jdbc:sqlite:parkinglot.db";

    public static Connection connect() {
        Connection conn = null;
        try {
            conn = DriverManager.getConnection(DB_URL);
            System.out.println("Connection to SQLite has been established.");
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
        return conn;
    }

    public static void initializeDatabase() {
        // Creates the table for Drivers if it doesn't already exist
        String createDriverTable = "CREATE TABLE IF NOT EXISTS drivers (\n"
                + " id text PRIMARY KEY,\n"
                + " name text NOT NULL,\n"
                + " email text NOT NULL,\n"
                + " phone text NOT NULL,\n"
                + " password text NOT NULL,\n"
                + " license_plate text NOT NULL,\n"
                + " dl_number text NOT NULL,\n"
                + " aadhar_number text NOT NULL\n"
                + ");";

        try (Connection conn = connect();
             Statement stmt = conn.createStatement()) {
            stmt.execute(createDriverTable);
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }
}