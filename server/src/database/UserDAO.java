package database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Gère la base MySQL côté SERVEUR : comptes utilisateurs (username / password).
 *
 * Base : messagerie_db
 * Table : users (username VARCHAR(50) PK, password VARCHAR(255))
 *
 * ── Config MySQL (à adapter si besoin) ──────────────────────────────────────
 *   HOST     = localhost
 *   PORT     = 3306
 *   DATABASE = messagerie_db
 *   USER     = root
 *   PASSWORD = ""   ← changer ici si votre MySQL a un mot de passe
 * ────────────────────────────────────────────────────────────────────────────
 */
public class UserDAO {

    private static final String HOST     = "localhost";
    private static final String PORT_DB  = "3306";
    private static final String DATABASE = "messagerie_db";
    private static final String USER     = "root";
    private static final String PASSWORD = "";

    private static final String URL =
            "jdbc:mysql://" + HOST + ":" + PORT_DB + "/" + DATABASE
            + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
            + "&createDatabaseIfNotExist=true";

    public static void initialiserBase() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            System.err.println("[DB] Driver MySQL introuvable dans server/lib/ !");
            return;
        }

        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
             Statement st = conn.createStatement()) {

            st.execute(
                "CREATE TABLE IF NOT EXISTS users (" +
                "  username VARCHAR(50)  PRIMARY KEY," +
                "  password VARCHAR(255) NOT NULL" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );
            System.out.println("[DB] MySQL serveur prêt (messagerie_db.users).");

        } catch (SQLException e) {
            System.err.println("[DB] Erreur connexion MySQL serveur : " + e.getMessage());
            System.err.println("     Vérifiez que MySQL tourne et que les paramètres sont corrects.");
        }
    }

    public static boolean verifierLogin(String username, String password) {
        String sql = "SELECT 1 FROM users WHERE username = ? AND password = ?";
        try (Connection conn = getConn();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, password);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        } catch (SQLException e) {
            System.err.println("[DB] verifierLogin : " + e.getMessage());
            return false;
        }
    }

    public static boolean utilisateurExiste(String username) {
        String sql = "SELECT 1 FROM users WHERE username = ?";
        try (Connection conn = getConn();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        } catch (SQLException e) { return false; }
    }

    public static boolean creerUtilisateur(String username, String password) {
        String sql = "INSERT IGNORE INTO users(username, password) VALUES(?, ?)";
        try (Connection conn = getConn();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, password);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[DB] creerUtilisateur : " + e.getMessage());
            return false;
        }
    }

    private static Connection getConn() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }
}
