package database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Gestion des contacts en MySQL.
 *
 * Chaque opération filtre par "proprietaire" (= utilisateur connecté)
 * pour isoler les contacts de chaque utilisateur dans la même table MySQL.
 *
 * Différence SQLite → MySQL :
 *   SQLite : INSERT OR IGNORE  →  MySQL : INSERT IGNORE
 *   SQLite : INTEGER PRIMARY KEY AUTOINCREMENT → MySQL : INT AUTO_INCREMENT PRIMARY KEY
 */
public class ContactDAO {

    public static boolean ajouter(String nomContact) {
        String moi = LocalDatabase.getUtilisateurCourant();
        if (moi == null) return false;

        // INSERT IGNORE évite l'erreur si le contact existe déjà (contrainte UNIQUE)
        String sql = "INSERT IGNORE INTO contacts(proprietaire, nom) VALUES(?, ?)";
        try (Connection c = LocalDatabase.getConnexion();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, moi);
            ps.setString(2, nomContact);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[DB] Erreur ajout contact : " + e.getMessage());
            return false;
        }
    }

    public static boolean supprimer(String nomContact) {
        String moi = LocalDatabase.getUtilisateurCourant();
        if (moi == null) return false;

        String sql = "DELETE FROM contacts WHERE proprietaire = ? AND nom = ?";
        try (Connection c = LocalDatabase.getConnexion();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, moi);
            ps.setString(2, nomContact);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[DB] Erreur suppression contact : " + e.getMessage());
            return false;
        }
    }

    public static List<String> lister() {
        String moi = LocalDatabase.getUtilisateurCourant();
        List<String> contacts = new ArrayList<>();
        if (moi == null) return contacts;

        String sql = "SELECT nom FROM contacts WHERE proprietaire = ? ORDER BY nom";
        try (Connection c = LocalDatabase.getConnexion();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, moi);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) contacts.add(rs.getString("nom"));
            }
        } catch (SQLException e) {
            System.err.println("[DB] Erreur listing contacts : " + e.getMessage());
        }
        return contacts;
    }

    public static boolean existe(String nomContact) {
        String moi = LocalDatabase.getUtilisateurCourant();
        if (moi == null) return false;

        String sql = "SELECT 1 FROM contacts WHERE proprietaire = ? AND nom = ?";
        try (Connection c = LocalDatabase.getConnexion();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, moi);
            ps.setString(2, nomContact);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            return false;
        }
    }
}
