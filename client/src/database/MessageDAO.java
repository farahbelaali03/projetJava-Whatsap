package database;

import model.Message;
import model.TypeMessage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class MessageDAO {

    public static void sauvegarder(Message m) {
        if (m.getType() != TypeMessage.MESSAGE
                && m.getType() != TypeMessage.VOICE_MESSAGE
                && m.getType() != TypeMessage.FILE) return;

        String moi = LocalDatabase.getUtilisateurCourant();
        if (moi == null) return;

        String sql = "INSERT INTO messages(proprietaire, expediteur, destinataire, contenu, type) "
                + "VALUES(?, ?, ?, ?, ?)";
        try (Connection c = LocalDatabase.getConnexion();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, moi);
            ps.setString(2, m.getExpediteur());
            ps.setString(3, m.getDestinataire());
            String contenu = m.getContenu();
            if (m.getType() == TypeMessage.FILE) {
                contenu = contenu.split("\\|\\|")[0];
            }
            ps.setString(4, contenu);
            ps.setString(5, m.getType().name());
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[DB] Erreur sauvegarde message : " + e.getMessage());
        }
    }

    public static void sauvegarderVocal(String expediteur, String destinataire, String cheminLocal) {
        String moi = LocalDatabase.getUtilisateurCourant();
        if (moi == null) return;
        String sql = "INSERT INTO messages(proprietaire, expediteur, destinataire, contenu, type) "
                + "VALUES(?, ?, ?, ?, ?)";
        try (Connection c = LocalDatabase.getConnexion();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, moi);
            ps.setString(2, expediteur);
            ps.setString(3, destinataire);
            ps.setString(4, cheminLocal);
            ps.setString(5, TypeMessage.VOICE_MESSAGE.name());
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[DB] Erreur sauvegarde vocal : " + e.getMessage());
        }
    }

    public static void sauvegarderFichier(String expediteur, String destinataire, String nomFichier, boolean sortant) {
        String moi = LocalDatabase.getUtilisateurCourant();
        if (moi == null) return;
        String sql = "INSERT INTO messages(proprietaire, expediteur, destinataire, contenu, type) "
                + "VALUES(?, ?, ?, ?, ?)";
        try (Connection c = LocalDatabase.getConnexion();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, moi);
            ps.setString(2, expediteur);
            ps.setString(3, destinataire);
            ps.setString(4, nomFichier);
            ps.setString(5, TypeMessage.FILE.name());
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[DB] Erreur sauvegarde fichier : " + e.getMessage());
        }
    }

    public static List<Message> historiqueAvec(String autre) {
        List<Message> liste = new ArrayList<>();
        String moi = LocalDatabase.getUtilisateurCourant();
        if (moi == null) return liste;

        String sql = "SELECT expediteur, destinataire, contenu, type "
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
                    String typeStr = rs.getString("type");
                    TypeMessage type;
                    try { type = TypeMessage.valueOf(typeStr); }
                    catch (Exception e) { type = TypeMessage.MESSAGE; }
                    liste.add(new Message(
                            rs.getString("expediteur"),
                            rs.getString("destinataire"),
                            rs.getString("contenu"),
                            type));
                }
            }
        } catch (SQLException e) {
            System.err.println("[DB] Erreur lecture historique : " + e.getMessage());
        }
        return liste;
    }
}