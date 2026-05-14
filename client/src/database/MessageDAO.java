package database;

import model.Message;
import model.TypeMessage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Historique des messages privés en MySQL.
 *
 * Filtre toujours par "proprietaire" pour isoler les données de chaque user.
 *
 * Différence SQLite → MySQL :
 *   SQLite : id INTEGER PRIMARY KEY AUTOINCREMENT → MySQL : INT AUTO_INCREMENT
 */
public class MessageDAO {

    public static void sauvegarder(Message m) {
        if (m.getType() != TypeMessage.MESSAGE) return;
        String moi = LocalDatabase.getUtilisateurCourant();
        if (moi == null) return;

        String sql = "INSERT INTO messages(proprietaire, expediteur, destinataire, contenu) "
                   + "VALUES(?, ?, ?, ?)";
        try (Connection c = LocalDatabase.getConnexion();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, moi);
            ps.setString(2, m.getExpediteur());
            ps.setString(3, m.getDestinataire());
            ps.setString(4, m.getContenu());
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[DB] Erreur sauvegarde message : " + e.getMessage());
        }
    }

    /**
     * Historique des messages échangés avec un autre utilisateur,
     * dans l'ordre chronologique.
     */
    public static List<Message> historiqueAvec(String autre) {
        List<Message> liste = new ArrayList<>();
        String moi = LocalDatabase.getUtilisateurCourant();
        if (moi == null) return liste;

        String sql = "SELECT expediteur, destinataire, contenu "
                   + "FROM messages "
                   + "WHERE proprietaire = ? "
                   + "  AND ((expediteur = ? AND destinataire = ?) "
                   + "    OR (expediteur = ? AND destinataire = ?)) "
                   + "ORDER BY id ASC";

        try (Connection c = LocalDatabase.getConnexion();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, moi);
            ps.setString(2, moi);
            ps.setString(3, autre);
            ps.setString(4, autre);
            ps.setString(5, moi);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    liste.add(new Message(
                            rs.getString("expediteur"),
                            rs.getString("destinataire"),
                            rs.getString("contenu"),
                            TypeMessage.MESSAGE));
                }
            }
        } catch (SQLException e) {
            System.err.println("[DB] Erreur lecture historique : " + e.getMessage());
        }
        return liste;
    }
}
