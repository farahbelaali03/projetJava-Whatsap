package client;

import database.LocalDatabase;
import database.MessageDAO;
import database.GroupDAO;
import model.Message;
import model.TypeMessage;
import ui.AudioCallWindow;
import ui.GroupAudioCallWindow;
import ui.MainApp;
import ui.VideoCallWindow;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.Arrays;
import java.util.List;

/**
 * Couche réseau côté client.
 *
 * Version 2 : gestion des groupes, appel audio seul, réunions de groupe.
 */
public class Client {

    private String adresseServeur = "localhost";
    private int    port           = 5000;

    private Socket               socket;
    private ObjectOutputStream   fluxSortie;
    private ObjectInputStream    fluxEntree;

    private String  nomUtilisateur;
    private boolean connecte = false;

    /** Fenêtre d'appel VIDÉO active. */
    private VideoCallWindow fenetreAppelActive;

    /** Fenêtre d'appel AUDIO seul active. */
    private AudioCallWindow fenetreAudioActive;

    /** Fenêtre de RÉUNION DE GROUPE active. */
    private GroupAudioCallWindow fenetreReunionActive;

    public Client() {}

    // ── Configuration ──────────────────────────────────────────

    public void configurerServeur(String ip, int port) {
        if (ip != null && !ip.isBlank()) this.adresseServeur = ip.trim();
        if (port > 0)                    this.port = port;
    }

    // ── Connexion ──────────────────────────────────────────────

    public boolean connecter(String nomUtilisateur, String motDePasse) {
        try {
            this.nomUtilisateur = nomUtilisateur;
            socket = new Socket(adresseServeur, port);

            fluxSortie = new ObjectOutputStream(socket.getOutputStream());
            fluxSortie.flush();
            fluxEntree = new ObjectInputStream(socket.getInputStream());

            connecte = true;
            LocalDatabase.initialiser(nomUtilisateur);
            demarrerEcoute();
            envoyer(new Message(nomUtilisateur, "SERVER", motDePasse, TypeMessage.CONNECT));
            return true;
        } catch (IOException e) {
            System.err.println("[Client] Échec connexion TCP : " + e.getMessage());
            connecte = false;
            return false;
        }
    }

    public void deconnecter() {
        if (!connecte) return;
        try { envoyer(new Message(nomUtilisateur, "SERVER", "", TypeMessage.DISCONNECT)); }
        catch (Exception ignored) {}
        connecte = false;
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}
    }

    // ── API publique ────────────────────────────────────────────

    public void envoyerMessage(String destinataire, String contenu) {
        Message m = new Message(nomUtilisateur, destinataire, contenu, TypeMessage.MESSAGE);
        envoyer(m);
        MessageDAO.sauvegarder(m);
    }

    // ── Appels VIDÉO ───────────────────────────────────────────
    public void demanderAppel(String dest)  {
        System.out.println("[Client TX] CALL_REQUEST -> " + dest);
        envoyer(new Message(nomUtilisateur, dest, "", TypeMessage.CALL_REQUEST));
    }
    public void accepterAppel(String dest)  {
        System.out.println("[Client TX] CALL_ACCEPT -> " + dest);
        envoyer(new Message(nomUtilisateur, dest, "", TypeMessage.CALL_ACCEPT));
    }
    public void refuserAppel(String dest)   { envoyer(new Message(nomUtilisateur, dest, "", TypeMessage.CALL_REJECT)); }
    public void terminerAppel(String dest)  { envoyer(new Message(nomUtilisateur, dest, "", TypeMessage.CALL_END)); }

    // ── Appels AUDIO seul ──────────────────────────────────────
    public void demanderAppelAudio(String dest)  {
        System.out.println("[Client TX] AUDIO_CALL_REQUEST -> " + dest);
        envoyer(new Message(nomUtilisateur, dest, "", TypeMessage.AUDIO_CALL_REQUEST));
    }
    public void accepterAppelAudio(String dest)  {
        System.out.println("[Client TX] AUDIO_CALL_ACCEPT -> " + dest);
        envoyer(new Message(nomUtilisateur, dest, "", TypeMessage.AUDIO_CALL_ACCEPT));
    }
    public void refuserAppelAudio(String dest)   { envoyer(new Message(nomUtilisateur, dest, "", TypeMessage.AUDIO_CALL_REJECT)); }
    public void terminerAppelAudio(String dest)  { envoyer(new Message(nomUtilisateur, dest, "", TypeMessage.AUDIO_CALL_END)); }

    // ── Groupes ────────────────────────────────────────────────
    public void creerGroupe(String nom) {
        envoyer(new Message(nomUtilisateur, "SERVER", nom, TypeMessage.GROUP_CREATE));
    }
    public void ajouterMembreGroupe(String groupe, String membre) {
        envoyer(new Message(nomUtilisateur, "SERVER", groupe + "::" + membre, TypeMessage.GROUP_ADD_MEMBER));
    }
    public void retirerMembreGroupe(String groupe, String membre) {
        envoyer(new Message(nomUtilisateur, "SERVER", groupe + "::" + membre, TypeMessage.GROUP_REMOVE_MEMBER));
    }
    public void envoyerMessageGroupe(String groupe, String contenu) {
        envoyer(new Message(nomUtilisateur, groupe, contenu, TypeMessage.GROUP_MESSAGE));
        GroupDAO.sauvegarderMessage(groupe, nomUtilisateur, contenu);
    }

    // ── Réunions de groupe ─────────────────────────────────────
    public void demarrerReunionGroupe(String groupe) {
        envoyer(new Message(nomUtilisateur, groupe, "", TypeMessage.GROUP_CALL_REQUEST));
    }
    public void rejoindreReunionGroupe(String groupe) {
        // Ouvrir la fenêtre SYNCHRONIQUEMENT sur l'EDT, puis envoyer ACCEPT
        if (fenetreReunionActive == null) {
            try {
                javax.swing.SwingUtilities.invokeAndWait(() -> {
                    try {
                        GroupAudioCallWindow w = new GroupAudioCallWindow(this, groupe);
                        setFenetreReunionActive(w);
                        System.out.println("[Réunion] Fenêtre ouverte pour le membre rejoignant : " + groupe);
                    } catch (Exception ex) {
                        System.err.println("[Réunion] Erreur ouverture fenêtre membre : " + ex.getMessage());
                    }
                });
            } catch (Exception ex) {
                System.err.println("[Réunion] invokeAndWait erreur : " + ex.getMessage());
            }
        }
        // Envoyer ACCEPT après que la fenêtre soit bien ouverte
        envoyer(new Message(nomUtilisateur, groupe, "", TypeMessage.GROUP_CALL_ACCEPT));
    }
    public void quitterReunionGroupe(String groupe) {
        envoyer(new Message(nomUtilisateur, groupe, "", TypeMessage.GROUP_CALL_END));
    }

    public void demanderListeUtilisateurs() {
        envoyer(new Message(nomUtilisateur, "SERVER", "", TypeMessage.GET_USERS));
    }

    public synchronized void envoyer(Message message) {
        try {
            fluxSortie.writeObject(message);
            fluxSortie.flush();
            fluxSortie.reset();
        } catch (IOException e) {
            System.err.println("[Client] Erreur envoi : " + e.getMessage());
        }
    }

    public String  getNomUtilisateur() { return nomUtilisateur; }
    public boolean estConnecte()       { return connecte; }

    public void setFenetreAppelActive(VideoCallWindow w) { this.fenetreAppelActive = w; }
    public VideoCallWindow getFenetreAppelActive()       { return fenetreAppelActive; }

    public void setFenetreAudioActive(AudioCallWindow w) { this.fenetreAudioActive = w; }
    public AudioCallWindow getFenetreAudioActive()       { return fenetreAudioActive; }

    public void setFenetreReunionActive(GroupAudioCallWindow w) { this.fenetreReunionActive = w; }
    public GroupAudioCallWindow getFenetreReunionActive()       { return fenetreReunionActive; }

    // ── Écoute ─────────────────────────────────────────────────

    private void demarrerEcoute() {
        Thread t = new Thread(() -> {
            try {
                Message m;
                while (connecte && (m = (Message) fluxEntree.readObject()) != null) {
                    traiterMessageRecu(m);
                }
            } catch (Exception e) {
                if (connecte) {
                    System.err.println("[Client] Connexion perdue : " + e.getMessage());
                    connecte = false;
                }
            }
        });
        t.setDaemon(true);
        t.setName("ClientListener");
        t.start();
    }

    private void traiterMessageRecu(Message m) {
        // Log diagnostic pour les messages d'appel uniquement (pas le spam vidéo/audio)
        TypeMessage t = m.getType();
        if (t != TypeMessage.VIDEO && t != TypeMessage.AUDIO
                && t != TypeMessage.AUDIO_ONLY && t != TypeMessage.GROUP_AUDIO
                && t != TypeMessage.GROUP_VIDEO) {
            System.out.println("[Client RX] " + t + " de " + m.getExpediteur());
        }

        switch (m.getType()) {

            case CONNECT:
                gererReponseConnexion(m);
                break;

            case MESSAGE:
                MessageDAO.sauvegarder(m);
                if (MainApp.getControleurChat() != null)
                    MainApp.getControleurChat().afficherMessage(m);
                break;

            case FILE:
                if (MainApp.getControleurChat() != null)
                    MainApp.getControleurChat().afficherFichier(m);
                break;

            case GET_USERS:
                if (MainApp.getControleurChat() != null)
                    MainApp.getControleurChat().mettreAJourListeUtilisateurs(m.getContenu());
                break;

            // ── Appel VIDÉO ────────────────────────────────────
            case CALL_REQUEST:
                if (MainApp.getControleurChat() != null)
                    MainApp.getControleurChat().afficherDemandeAppel(m);
                break;

            case CALL_ACCEPT:
                if (MainApp.getControleurChat() != null)
                    MainApp.getControleurChat().gererAcceptationAppel(m);
                break;

            case CALL_REJECT:
                if (MainApp.getControleurChat() != null)
                    MainApp.getControleurChat().gererRefusAppel(m);
                break;

            case CALL_END:
                if (fenetreAppelActive != null) {
                    fenetreAppelActive.terminerAppelExterieur();
                    fenetreAppelActive = null;
                }
                break;

            case VIDEO:
                if (fenetreAppelActive != null)
                    fenetreAppelActive.afficherFrameDistante(m);
                break;

            case AUDIO:
                if (fenetreAppelActive != null)
                    fenetreAppelActive.jouerAudioDistant(m);
                break;

            // ── Appel AUDIO seul ───────────────────────────────
            case AUDIO_CALL_REQUEST:
                if (MainApp.getControleurChat() != null)
                    MainApp.getControleurChat().afficherDemandeAppelAudio(m);
                break;

            case AUDIO_CALL_ACCEPT:
                if (MainApp.getControleurChat() != null)
                    MainApp.getControleurChat().gererAcceptationAppelAudio(m);
                break;

            case AUDIO_CALL_REJECT:
                if (MainApp.getControleurChat() != null)
                    MainApp.getControleurChat().gererRefusAppelAudio(m);
                break;

            case AUDIO_CALL_END:
                if (fenetreAudioActive != null) {
                    fenetreAudioActive.terminerAppelExterieur();
                    fenetreAudioActive = null;
                }
                break;

            case AUDIO_ONLY:
                if (fenetreAudioActive != null)
                    fenetreAudioActive.jouerAudio(m);
                break;

            // ── Groupes ────────────────────────────────────────
            case GROUP_INFO:
                traiterInfoGroupe(m);
                break;

            case GROUP_MESSAGE:
                GroupDAO.sauvegarderMessage(m.getDestinataire(), m.getExpediteur(), m.getContenu());
                if (MainApp.getControleurChat() != null)
                    MainApp.getControleurChat().afficherMessageGroupe(m);
                break;

            // ── Réunions de groupe ─────────────────────────────
            case GROUP_CALL_REQUEST:
                // Un autre membre du groupe nous invite à rejoindre la réunion
                if (MainApp.getControleurChat() != null)
                    MainApp.getControleurChat().afficherDemandeReunion(m);
                break;

            case GROUP_CALL_ACCEPT:
                // Un membre a rejoint la réunion. Notifier la fenêtre si elle est ouverte.
                if (fenetreReunionActive != null) {
                    final GroupAudioCallWindow fenetreAccept = fenetreReunionActive;
                    final String nouveauMembre = m.getExpediteur();
                    // Ne pas ajouter soi-même
                    if (!nouveauMembre.equals(nomUtilisateur)) {
                        javax.swing.SwingUtilities.invokeLater(() ->
                                fenetreAccept.membreRejoint(nouveauMembre));
                    }
                }
                if (MainApp.getControleurChat() != null)
                    MainApp.getControleurChat().membreARejoindreReunion(m);
                break;

            case GROUP_CALL_END:
                // Un participant a quitté la réunion
                if (fenetreReunionActive != null)
                    fenetreReunionActive.membreParti(m.getExpediteur());
                if (MainApp.getControleurChat() != null)
                    MainApp.getControleurChat().membreAQuitteReunion(m);
                break;

            case GROUP_AUDIO:
                // Flux audio de la réunion de groupe
                if (fenetreReunionActive != null)
                    fenetreReunionActive.jouerAudio(m);
                else if (fenetreAudioActive != null)
                    fenetreAudioActive.jouerAudio(m);
                break;

            case DISCONNECT:
            default:
                break;
        }
    }

    private void traiterInfoGroupe(Message m) {
        String[] parts = m.getContenu().split("::", 2);
        if (parts.length < 2) {
            System.err.println("[Client] GROUP_INFO malformé : " + m.getContenu());
            return;
        }
        String nomGroupe  = parts[0].trim();
        String membresStr = parts[1].trim();

        System.out.println("[Client] GROUP_INFO reçu : groupe='" + nomGroupe
                + "' membres='" + membresStr + "'");

        if (membresStr.isEmpty()) {
            // Retiré du groupe
            GroupDAO.supprimer(nomGroupe);
            System.out.println("[Client] Retiré du groupe : " + nomGroupe);
        } else {
            List<String> membres = Arrays.asList(membresStr.split(","));
            GroupDAO.sauvegarderOuMettreAJour(nomGroupe, membres);
            System.out.println("[Client] Groupe sauvegardé : " + nomGroupe
                    + " avec " + membres.size() + " membre(s)");
        }

        if (MainApp.getControleurChat() != null)
            MainApp.getControleurChat().rafraichirGroupes();
    }

    private void gererReponseConnexion(Message message) {
        String contenu = message.getContenu();
        if ("LOGIN_OK".equals(contenu)) {
            System.out.println("[Client] Login accepté.");
            if (MainApp.getControleurLogin() != null)
                MainApp.getControleurLogin().surConnexionReussie();
        } else if ("LOGIN_FAIL".equals(contenu)) {
            System.out.println("[Client] Login refusé.");
            connecte = false;
            if (MainApp.getControleurLogin() != null)
                MainApp.getControleurLogin().surEchecConnexion();
        }
    }
}