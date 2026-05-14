package database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Gère la base MySQL côté CLIENT.
 *
 * Contrairement à SQLite (1 fichier .db par utilisateur), MySQL utilise
 * UNE seule base partagée avec des tables qui contiennent une colonne
 * "proprietaire" pour isoler les données de chaque utilisateur.
 *
 * Base : messagerie_client_db
 *
 * ── Setup MySQL à faire UNE SEULE FOIS ──────────────────────────────────────
 *   CREATE DATABASE IF NOT EXISTS messagerie_client_db CHARACTER SET utf8mb4;
 *   USE messagerie_client_db;
 *
 *   CREATE TABLE IF NOT EXISTS contacts (
 *       id          INT AUTO_INCREMENT PRIMARY KEY,
 *       proprietaire VARCHAR(50) NOT NULL,
 *       nom          VARCHAR(50) NOT NULL,
 *       date_ajout   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 *       UNIQUE KEY uq_contact (proprietaire, nom)
 *   ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
 *
 *   CREATE TABLE IF NOT EXISTS messages (
 *       id           INT AUTO_INCREMENT PRIMARY KEY,
 *       proprietaire VARCHAR(50) NOT NULL,
 *       expediteur   VARCHAR(50) NOT NULL,
 *       destinataire VARCHAR(50) NOT NULL,
 *       contenu      TEXT        NOT NULL,
 *       date_envoi   TIMESTAMP DEFAULT CURRENT_TIMESTAMP
 *   ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
 *
 *   CREATE TABLE IF NOT EXISTS groupes (
 *       id           INT AUTO_INCREMENT PRIMARY KEY,
 *       proprietaire VARCHAR(50) NOT NULL,
 *       nom          VARCHAR(100) NOT NULL,
 *       membres      TEXT NOT NULL DEFAULT '',
 *       date_creation TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 *       UNIQUE KEY uq_groupe (proprietaire, nom)
 *   ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
 *
 *   CREATE TABLE IF NOT EXISTS messages_groupe (
 *       id           INT AUTO_INCREMENT PRIMARY KEY,
 *       proprietaire VARCHAR(50) NOT NULL,
 *       groupe       VARCHAR(100) NOT NULL,
 *       expediteur   VARCHAR(50) NOT NULL,
 *       contenu      TEXT NOT NULL,
 *       date_envoi   TIMESTAMP DEFAULT CURRENT_TIMESTAMP
 *   ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
 * ────────────────────────────────────────────────────────────────────────────
 */
public class LocalDatabase {

    // ── Paramètres de connexion MySQL ──────────────────────────
    private static final String HOST     = "localhost";
    private static final String PORT_DB  = "3306";
    private static final String DATABASE = "messagerie_client_db";
    private static final String USER     = "root";       // ← changer si besoin
    private static final String PASSWORD = "";           // ← changer si besoin

    private static final String URL =
            "jdbc:mysql://" + HOST + ":" + PORT_DB + "/" + DATABASE
            + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";

    private static String utilisateurCourant;

    // ── Initialisation ─────────────────────────────────────────

    /**
     * À appeler juste après un login réussi.
     * Crée les tables si elles n'existent pas.
     */
    public static synchronized void initialiser(String nomUtilisateur) {
        utilisateurCourant = nomUtilisateur;

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            System.err.println("[DB] Driver MySQL introuvable dans client/lib/");
            return;
        }

        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
             Statement st = conn.createStatement()) {

            st.execute(
                "CREATE TABLE IF NOT EXISTS contacts ("
                + "  id           INT AUTO_INCREMENT PRIMARY KEY,"
                + "  proprietaire VARCHAR(50) NOT NULL,"
                + "  nom          VARCHAR(50) NOT NULL,"
                + "  date_ajout   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,"
                + "  UNIQUE KEY uq_contact (proprietaire, nom)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );

            st.execute(
                "CREATE TABLE IF NOT EXISTS messages ("
                + "  id           INT AUTO_INCREMENT PRIMARY KEY,"
                + "  proprietaire VARCHAR(50) NOT NULL,"
                + "  expediteur   VARCHAR(50) NOT NULL,"
                + "  destinataire VARCHAR(50) NOT NULL,"
                + "  contenu      TEXT NOT NULL,"
                + "  date_envoi   TIMESTAMP DEFAULT CURRENT_TIMESTAMP"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );

            st.execute(
                "CREATE TABLE IF NOT EXISTS groupes ("
                + "  id           INT AUTO_INCREMENT PRIMARY KEY,"
                + "  proprietaire VARCHAR(50) NOT NULL,"
                + "  nom          VARCHAR(100) NOT NULL,"
                + "  membres      TEXT NOT NULL DEFAULT '',"
                + "  date_creation TIMESTAMP DEFAULT CURRENT_TIMESTAMP,"
                + "  UNIQUE KEY uq_groupe (proprietaire, nom)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );

            st.execute(
                "CREATE TABLE IF NOT EXISTS messages_groupe ("
                + "  id           INT AUTO_INCREMENT PRIMARY KEY,"
                + "  proprietaire VARCHAR(50) NOT NULL,"
                + "  groupe       VARCHAR(100) NOT NULL,"
                + "  expediteur   VARCHAR(50) NOT NULL,"
                + "  contenu      TEXT NOT NULL,"
                + "  date_envoi   TIMESTAMP DEFAULT CURRENT_TIMESTAMP"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );

            System.out.println("[DB] MySQL client prêt pour : " + nomUtilisateur);

        } catch (SQLException e) {
            System.err.println("[DB] Erreur init MySQL client : " + e.getMessage());
        }
    }

    public static Connection getConnexion() throws SQLException {
        if (utilisateurCourant == null)
            throw new SQLException("Base non initialisée. Appeler initialiser() d'abord.");
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    public static String getUtilisateurCourant() { return utilisateurCourant; }
}
