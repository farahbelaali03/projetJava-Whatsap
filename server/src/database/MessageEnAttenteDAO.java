package database;

import model.Message;
import model.TypeMessage;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class MessageEnAttenteDAO {

    private static final String HOST     = "localhost";
    private static final String PORT_DB  = "3306";
    private static final String DATABASE = "messagerie_db";
    private static final String USER     = "root";
    private static final String PASSWORD = "";

    private static final String URL =
            "jdbc:mysql://" + HOST + ":" + PORT_DB + "/" + DATABASE
                    + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";

    private static Connection getConnexion() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    public static void initialiserTable() {
        String sql = "CREATE TABLE IF NOT EXISTS messages_en_attente (" +
                "id INT AUTO_INCREMENT PRIMARY KEY, " +
                "expediteur VARCHAR(100) NOT NULL, " +
                "destinataire VARCHAR(100) NOT NULL, " +
                "contenu MEDIUMTEXT, " +
                "type VARCHAR(30) NOT NULL, " +
                "date_envoi TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ")";
        try (Connection c = getConnexion(); Statement s = c.createStatement()) {
            s.execute(sql);
            System.out.println("[DB] Table messages_en_attente prête.");
        } catch (Exception e) {
            System.err.println("[DB] Erreur init messages_en_attente : " + e.getMessage());
        }
    }

    public static void sauvegarder(Message msg) {
        String sql = "INSERT INTO messages_en_attente(expediteur, destinataire, contenu, type) VALUES(?,?,?,?)";
        try (Connection c = getConnexion(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, msg.getExpediteur());
            ps.setString(2, msg.getDestinataire());
            ps.setString(3, msg.getContenu());
            ps.setString(4, msg.getType().name());
            ps.executeUpdate();
            System.out.println("[File] Message sauvegardé pour " + msg.getDestinataire());
        } catch (Exception e) {
            System.err.println("[DB] Erreur sauvegarde message en attente : " + e.getMessage());
        }
    }

    public static List<Message> recuperer(String destinataire) {
        List<Message> result = new ArrayList<>();
        String sql = "SELECT expediteur, destinataire, contenu, type FROM messages_en_attente " +
                "WHERE destinataire = ? ORDER BY date_envoi ASC";
        try (Connection c = getConnexion(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, destinataire);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    TypeMessage type = TypeMessage.valueOf(rs.getString("type"));
                    Message m = new Message(
                            rs.getString("expediteur"),
                            rs.getString("destinataire"),
                            rs.getString("contenu"),
                            type
                    );
                    result.add(m);
                }
            }
        } catch (Exception e) {
            System.err.println("[DB] Erreur récupération messages en attente : " + e.getMessage());
        }
        return result;
    }

    public static void supprimer(String destinataire) {
        String sql = "DELETE FROM messages_en_attente WHERE destinataire = ?";
        try (Connection c = getConnexion(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, destinataire);
            int n = ps.executeUpdate();
            if (n > 0) System.out.println("[File] " + n + " message(s) en attente supprimé(s) pour " + destinataire);
        } catch (Exception e) {
            System.err.println("[DB] Erreur suppression messages en attente : " + e.getMessage());
        }
    }
}