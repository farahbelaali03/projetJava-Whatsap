-- ============================================================
--  SETUP MYSQL — Application Messagerie WhatsApp
--  À exécuter UNE SEULE FOIS dans MySQL Workbench ou phpMyAdmin
-- ============================================================

-- 1. BASE DU SERVEUR (authentification)
-- ─────────────────────────────────────
CREATE DATABASE IF NOT EXISTS messagerie_db
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE messagerie_db;

CREATE TABLE IF NOT EXISTS users (
    username VARCHAR(50)  PRIMARY KEY,
    password VARCHAR(255) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- 2. BASE DU CLIENT (contacts, messages, groupes)
-- ─────────────────────────────────────────────────
CREATE DATABASE IF NOT EXISTS messagerie_client_db
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE messagerie_client_db;

-- Contacts de chaque utilisateur
CREATE TABLE IF NOT EXISTS contacts (
    id           INT AUTO_INCREMENT PRIMARY KEY,
    proprietaire VARCHAR(50)  NOT NULL,
    nom          VARCHAR(50)  NOT NULL,
    date_ajout   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_contact (proprietaire, nom)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Historique des messages privés
CREATE TABLE IF NOT EXISTS messages (
    id           INT AUTO_INCREMENT PRIMARY KEY,
    proprietaire VARCHAR(50) NOT NULL,
    expediteur   VARCHAR(50) NOT NULL,
    destinataire VARCHAR(50) NOT NULL,
    contenu      TEXT        NOT NULL,
    date_envoi   TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Groupes WhatsApp
CREATE TABLE IF NOT EXISTS groupes (
    id            INT AUTO_INCREMENT PRIMARY KEY,
    proprietaire  VARCHAR(50)  NOT NULL,
    nom           VARCHAR(100) NOT NULL,
    membres       TEXT NOT NULL DEFAULT '',
    date_creation TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_groupe (proprietaire, nom)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Historique des messages de groupe
CREATE TABLE IF NOT EXISTS messages_groupe (
    id           INT AUTO_INCREMENT PRIMARY KEY,
    proprietaire VARCHAR(50)  NOT NULL,
    groupe       VARCHAR(100) NOT NULL,
    expediteur   VARCHAR(50)  NOT NULL,
    contenu      TEXT NOT NULL,
    date_envoi   TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
--  VÉRIFICATION
-- ============================================================
SELECT 'messagerie_db OK'        AS statut FROM messagerie_db.users        LIMIT 0;
SELECT 'contacts OK'             AS statut FROM messagerie_client_db.contacts        LIMIT 0;
SELECT 'messages OK'             AS statut FROM messagerie_client_db.messages        LIMIT 0;
SELECT 'groupes OK'              AS statut FROM messagerie_client_db.groupes         LIMIT 0;
SELECT 'messages_groupe OK'      AS statut FROM messagerie_client_db.messages_groupe LIMIT 0;
