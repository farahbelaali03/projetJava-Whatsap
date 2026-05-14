package ui;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;

/**
 * Contrôleur de la fenêtre de connexion (login.fxml).
 *
 * Flow :
 *   1. L'utilisateur entre nom + mot de passe (+ optionnellement IP du serveur)
 *   2. Click sur "Se connecter" → ouverture du socket dans un thread
 *   3. Envoi du Message CONNECT
 *   4. À la réception de LOGIN_OK, le Client appelle surConnexionReussie()
 *      qui change la scène vers chat.fxml
 *   5. À la réception de LOGIN_FAIL → surEchecConnexion()
 */
public class LoginController {

    @FXML private TextField     champNomUtilisateur;
    @FXML private PasswordField champMotDePasse;
    @FXML private TextField     champIp;
    @FXML private TextField     champPort;
    @FXML private Button        boutonConnecter;
    @FXML private Label         labelErreur;
    @FXML private ProgressIndicator indicateurChargement;

    @FXML
    public void initialize() {
        labelErreur.setVisible(false);
        indicateurChargement.setVisible(false);
        // Entrée dans le champ mot de passe → connexion
        champMotDePasse.setOnAction(e -> seConnecter());
    }

    @FXML
    public void seConnecter() {
        String nom = champNomUtilisateur.getText().trim();
        String mdp = champMotDePasse.getText();
        String ip  = champIp != null   ? champIp.getText().trim()   : "localhost";
        String prt = champPort != null ? champPort.getText().trim() : "5000";

        if (nom.isEmpty() || mdp.isEmpty()) {
            afficherErreur("Veuillez remplir tous les champs.");
            return;
        }

        int port;
        try { port = Integer.parseInt(prt); }
        catch (NumberFormatException e) {
            afficherErreur("Port invalide.");
            return;
        }

        // Désactiver l'UI pendant la tentative
        boutonConnecter.setDisable(true);
        indicateurChargement.setVisible(true);
        labelErreur.setVisible(false);

        // Lancer la connexion dans un thread (ne pas bloquer l'UI)
        Thread t = new Thread(() -> {
            MainApp.client.configurerServeur(ip, port);
            boolean tcpOk = MainApp.client.connecter(nom, mdp);

            if (!tcpOk) {
                // Le socket n'a pas pu s'ouvrir : pas la peine d'attendre
                Platform.runLater(() -> {
                    indicateurChargement.setVisible(false);
                    boutonConnecter.setDisable(false);
                    afficherErreur("Impossible de joindre le serveur " + ip + ":" + port
                            + ".\nVérifie qu'il est démarré.");
                });
            }
            // Si TCP OK : on attend la réponse LOGIN_OK / LOGIN_FAIL du serveur,
            // qui sera traitée par Client → surConnexionReussie() / surEchecConnexion()
        });
        t.setDaemon(true);
        t.setName("LoginThread");
        t.start();
    }

    /** Appelé par Client quand le serveur a accepté le login. */
    public void surConnexionReussie() {
        Platform.runLater(() -> {
            indicateurChargement.setVisible(false);
            MainApp.changerScene("chat.fxml");
        });
    }

    /** Appelé par Client quand le serveur a refusé le login. */
    public void surEchecConnexion() {
        Platform.runLater(() -> {
            indicateurChargement.setVisible(false);
            boutonConnecter.setDisable(false);
            afficherErreur("Nom d'utilisateur ou mot de passe incorrect.");
        });
    }

    private void afficherErreur(String message) {
        labelErreur.setText(message);
        labelErreur.setVisible(true);
    }
}
