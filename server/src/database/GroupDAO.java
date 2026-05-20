package database;

import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GroupDAO {

    private static final String HOST     = "localhost";
    private static final String PORT_DB  = "3306";
    private static final String DATABASE = "messagerie_db";
    private static final String USER     = "root";
    private static final String PASSWORD = "";

    private static final String URL =
            "jdbc:mysql://" + HOST + ":" + PORT_DB + "/" + DATABASE
                    + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
                    + "&createDatabaseIfNotExist=true";

    private static Connection getConnexion() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    public static void initialiserTable() {
        String sql = "CREATE TABLE IF NOT EXISTS groupes_serveur (" +
                "nom VARCHAR(100) PRIMARY KEY, " +
                "membres TEXT NOT NULL)";
        try (Connection c = getConnexion(); Statement s = c.createStatement()) {
            Class.forName("com.mysql.cj.jdbc.Driver");
            s.execute(sql);
            System.out.println("[DB] Table groupes_serveur prête.");
        } catch (Exception e) {
            System.err.println("[DB] Erreur init groupes_serveur : " + e.getMessage());
        }
    }

    public static void sauvegarder(String nom, Set<String> membres) {
        String sql = "INSERT INTO groupes_serveur (nom, membres) VALUES (?, ?) " +
                "ON DUPLICATE KEY UPDATE membres = ?";
        String membresStr = String.join(",", membres);
        try (Connection c = getConnexion(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, nom);
            ps.setString(2, membresStr);
            ps.setString(3, membresStr);
            ps.executeUpdate();
        } catch (Exception e) {
            System.err.println("[DB] Erreur sauvegarde groupe : " + e.getMessage());
        }
    }

    public static Map<String, Set<String>> chargerTousLesGroupes() {
        Map<String, Set<String>> result = new ConcurrentHashMap<>();
        String sql = "SELECT nom, membres FROM groupes_serveur";
        try (Connection c = getConnexion();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(sql)) {
            while (rs.next()) {
                String nom = rs.getString("nom");
                String[] membres = rs.getString("membres").split(",");
                Set<String> set = ConcurrentHashMap.newKeySet();
                for (String m : membres) {
                    if (!m.trim().isEmpty()) set.add(m.trim());
                }
                result.put(nom, set);
            }
            System.out.println("[DB] " + result.size() + " groupe(s) chargé(s) depuis MySQL.");
        } catch (Exception e) {
            System.err.println("[DB] Erreur chargement groupes : " + e.getMessage());
        }
        return result;
    }

    public static void supprimer(String nom) {
        try (Connection c = getConnexion();
             PreparedStatement ps = c.prepareStatement("DELETE FROM groupes_serveur WHERE nom = ?")) {
            ps.setString(1, nom);
            ps.executeUpdate();
        } catch (Exception e) {
            System.err.println("[DB] Erreur suppression groupe : " + e.getMessage());
        }
    }
}