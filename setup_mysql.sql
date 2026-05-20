CREATE DATABASE IF NOT EXISTS messagerie_db
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE messagerie_db;

CREATE TABLE IF NOT EXISTS users (
                                     username VARCHAR(50)  PRIMARY KEY,
    password VARCHAR(255) NOT NULL
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS messages_en_attente (
                                                   id           INT AUTO_INCREMENT PRIMARY KEY,
                                                   expediteur   VARCHAR(100) NOT NULL,
    destinataire VARCHAR(100) NOT NULL,
    contenu      MEDIUMTEXT,
    type         VARCHAR(30)  NOT NULL,
    date_envoi   TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS groupes_serveur (
                                               nom     VARCHAR(100) PRIMARY KEY,
    membres TEXT NOT NULL
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE DATABASE IF NOT EXISTS messagerie_client_db
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE messagerie_client_db;

CREATE TABLE IF NOT EXISTS contacts (
                                        id           INT AUTO_INCREMENT PRIMARY KEY,
                                        proprietaire VARCHAR(50)  NOT NULL,
    nom          VARCHAR(50)  NOT NULL,
    date_ajout   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_contact (proprietaire, nom)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS messages (
                                        id           INT AUTO_INCREMENT PRIMARY KEY,
                                        proprietaire VARCHAR(50) NOT NULL,
    expediteur   VARCHAR(50) NOT NULL,
    destinataire VARCHAR(50) NOT NULL,
    contenu      TEXT        NOT NULL,
    type         VARCHAR(20) DEFAULT 'MESSAGE',
    date_envoi   TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS groupes (
                                       id            INT AUTO_INCREMENT PRIMARY KEY,
                                       proprietaire  VARCHAR(50)  NOT NULL,
    nom           VARCHAR(100) NOT NULL,
    membres       TEXT NOT NULL DEFAULT '',
    date_creation TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_groupe (proprietaire, nom)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS messages_groupe (
                                               id           INT AUTO_INCREMENT PRIMARY KEY,
                                               proprietaire VARCHAR(50)  NOT NULL,
    groupe       VARCHAR(100) NOT NULL,
    expediteur   VARCHAR(50)  NOT NULL,
    contenu      MEDIUMTEXT   NOT NULL,
    type         VARCHAR(20) DEFAULT 'MESSAGE',
    date_envoi   TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE messages
    ADD COLUMN IF NOT EXISTS type VARCHAR(20) DEFAULT 'MESSAGE' AFTER contenu;

ALTER TABLE messages_groupe
    ADD COLUMN IF NOT EXISTS type VARCHAR(20) DEFAULT 'MESSAGE' AFTER contenu;

ALTER TABLE messages_groupe
    MODIFY COLUMN contenu MEDIUMTEXT NOT NULL;

SELECT 'messagerie_db OK'          AS statut FROM messagerie_db.users                    LIMIT 0;
SELECT 'messages_en_attente OK'    AS statut FROM messagerie_db.messages_en_attente      LIMIT 0;
SELECT 'groupes_serveur OK'        AS statut FROM messagerie_db.groupes_serveur          LIMIT 0;
SELECT 'contacts OK'               AS statut FROM messagerie_client_db.contacts          LIMIT 0;
SELECT 'messages OK'               AS statut FROM messagerie_client_db.messages          LIMIT 0;
SELECT 'groupes OK'                AS statut FROM messagerie_client_db.groupes           LIMIT 0;
SELECT 'messages_groupe OK'        AS statut FROM messagerie_client_db.messages_groupe   LIMIT 0;git add server/src/database/GroupDAO.java
