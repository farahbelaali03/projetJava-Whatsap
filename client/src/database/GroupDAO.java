package database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GroupDAO {

    public static void sauvegarderOuMettreAJour(String nomGroupe, List<String> membres) {
        String moi = LocalDatabase.getUtilisateurCourant();
        if (moi == null) return;
        String membresCSV = String.join(",", membres);
        String sql = "INSERT INTO groupes(proprietaire, nom, membres) VALUES(?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE membres = VALUES(membres)";
        try (Connection c = LocalDatabase.getConnexion();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, moi);
            ps.setString(2, nomGroupe);
            ps.setString(3, membresCSV);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[DB] Erreur sauvegarde groupe : " + e.getMessage());
        }
    }

    public static void supprimer(String nomGroupe) {
        String moi = LocalDatabase.getUtilisateurCourant();
        if (moi == null) return;
        String sql = "DELETE FROM groupes WHERE proprietaire = ? AND nom = ?";
        try (Connection c = LocalDatabase.getConnexion();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, moi);
            ps.setString(2, nomGroupe);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[DB] Erreur suppression groupe : " + e.getMessage());
        }
    }

    public static List<String[]> listerGroupes() {
        String moi = LocalDatabase.getUtilisateurCourant();
        List<String[]> result = new ArrayList<>();
        if (moi == null) return result;
        String sql = "SELECT DISTINCT nom, membres FROM groupes WHERE proprietaire = ? OR FIND_IN_SET(?, membres) > 0 ORDER BY nom";
        try (Connection c = LocalDatabase.getConnexion();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, moi);
            ps.setString(2, moi);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new String[]{
                            rs.getString("nom"),
                            rs.getString("membres")
                    });
                }
            }
        } catch (SQLException e) {
            System.err.println("[DB] Erreur listing groupes : " + e.getMessage());
        }
        return result;
    }

    public static List<String> getMembres(String nomGroupe) {
        String moi = LocalDatabase.getUtilisateurCourant();
        if (moi == null) return new ArrayList<>();
        String sql = "SELECT membres FROM groupes WHERE proprietaire = ? AND nom = ?";
        try (Connection c = LocalDatabase.getConnexion();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, moi);
            ps.setString(2, nomGroupe);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String csv = rs.getString("membres");
                    if (csv == null || csv.isEmpty()) return new ArrayList<>();
                    return new ArrayList<>(Arrays.asList(csv.split(",")));
                }
            }
        } catch (SQLException e) {
            System.err.println("[DB] Erreur getMembres : " + e.getMessage());
        }
        return new ArrayList<>();
    }


    public static void sauvegarderMessage(String groupe, String expediteur, String contenu) {
        sauvegarderMessageAvecType(groupe, expediteur, contenu, "MESSAGE");
    }


    public static void sauvegarderMessageAvecType(String groupe, String expediteur, String contenu, String type) {
        String moi = LocalDatabase.getUtilisateurCourant();
        if (moi == null) return;
        String sql = "INSERT INTO messages_groupe(proprietaire, groupe, expediteur, contenu, type) "
                + "VALUES(?, ?, ?, ?, ?)";
        try (Connection c = LocalDatabase.getConnexion();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, moi);
            ps.setString(2, groupe);
            ps.setString(3, expediteur);
            ps.setString(4, contenu);
            ps.setString(5, type);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[DB] Erreur sauvegarde message groupe : " + e.getMessage());
        }
    }

    public static List<String[]> historique(String groupe) {
        String moi = LocalDatabase.getUtilisateurCourant();
        List<String[]> result = new ArrayList<>();
        if (moi == null) return result;
        String sql = "SELECT expediteur, contenu, date_envoi, type "
                + "FROM messages_groupe "
                + "WHERE proprietaire = ? AND groupe = ? "
                + "ORDER BY id ASC";
        try (Connection c = LocalDatabase.getConnexion();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, moi);
            ps.setString(2, groupe);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String type = rs.getString("type");
                    result.add(new String[]{
                            rs.getString("expediteur"),
                            rs.getString("contenu"),
                            rs.getString("date_envoi"),
                            type != null ? type : "MESSAGE"
                    });
                }
            }
        } catch (SQLException e) {
            System.err.println("[DB] Erreur historique groupe : " + e.getMessage());
        }
        return result;
    }
}