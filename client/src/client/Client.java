package client;

import database.LocalDatabase;
import database.MessageDAO;
import database.GroupDAO;
import model.Message;
import model.TypeMessage;
import ui.AudioCallWindow;
import ui.GroupAudioCallWindow;
import ui.GroupVideoCallWindow;
import ui.MainApp;
import ui.VideoCallWindow;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.Arrays;
import java.util.List;

public class Client {

    private String adresseServeur = "localhost";
    private int    port           = 5000;

    private Socket               socket;
    private ObjectOutputStream   fluxSortie;
    private ObjectInputStream    fluxEntree;

    private String  nomUtilisateur;
    private boolean connecte = false;

    private VideoCallWindow      fenetreAppelActive;
    private AudioCallWindow      fenetreAudioActive;
    private GroupAudioCallWindow fenetreReunionActive;
    private GroupVideoCallWindow fenetreReunionVideoActive;

    public Client() {}

    public void configurerServeur(String ip, int port) {
        if (ip != null && !ip.isBlank()) this.adresseServeur = ip.trim();
        if (port > 0)                    this.port = port;
    }

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

    public void envoyerMessage(String destinataire, String contenu) {
        Message m = new Message(nomUtilisateur, destinataire, contenu, TypeMessage.MESSAGE);
        envoyer(m);
        MessageDAO.sauvegarder(m);
    }

    public void demanderAppel(String dest)       { envoyer(new Message(nomUtilisateur, dest, "", TypeMessage.CALL_REQUEST)); }
    public void accepterAppel(String dest)       { envoyer(new Message(nomUtilisateur, dest, "", TypeMessage.CALL_ACCEPT)); }
    public void refuserAppel(String dest)        { envoyer(new Message(nomUtilisateur, dest, "", TypeMessage.CALL_REJECT)); }
    public void terminerAppel(String dest)       { envoyer(new Message(nomUtilisateur, dest, "", TypeMessage.CALL_END)); }

    public void demanderAppelAudio(String dest)  { envoyer(new Message(nomUtilisateur, dest, "", TypeMessage.AUDIO_CALL_REQUEST)); }
    public void accepterAppelAudio(String dest)  { envoyer(new Message(nomUtilisateur, dest, "", TypeMessage.AUDIO_CALL_ACCEPT)); }
    public void refuserAppelAudio(String dest)   { envoyer(new Message(nomUtilisateur, dest, "", TypeMessage.AUDIO_CALL_REJECT)); }
    public void terminerAppelAudio(String dest)  { envoyer(new Message(nomUtilisateur, dest, "", TypeMessage.AUDIO_CALL_END)); }

    public void creerGroupe(String nom)          { envoyer(new Message(nomUtilisateur, "SERVER", nom, TypeMessage.GROUP_CREATE)); }
    public void ajouterMembreGroupe(String g, String m) { envoyer(new Message(nomUtilisateur, "SERVER", g + "::" + m, TypeMessage.GROUP_ADD_MEMBER)); }
    public void retirerMembreGroupe(String g, String m) { envoyer(new Message(nomUtilisateur, "SERVER", g + "::" + m, TypeMessage.GROUP_REMOVE_MEMBER)); }

    public void envoyerMessageGroupe(String g, String c) {
        envoyer(new Message(nomUtilisateur, g, c, TypeMessage.GROUP_MESSAGE));
        GroupDAO.sauvegarderMessage(g, nomUtilisateur, c);
    }

    public void demarrerReunionGroupe(String groupe) {
        envoyer(new Message(nomUtilisateur, groupe, "", TypeMessage.GROUP_CALL_REQUEST));
    }

    public void demarrerReunionGroupeVideo(String groupe) {
        envoyer(new Message(nomUtilisateur, groupe, "VIDEO", TypeMessage.GROUP_CALL_REQUEST));
    }

    public void rejoindreReunionGroupe(String groupe) { envoyer(new Message(nomUtilisateur, groupe, "", TypeMessage.GROUP_CALL_ACCEPT)); }
    public void quitterReunionGroupe(String groupe)   { envoyer(new Message(nomUtilisateur, groupe, "", TypeMessage.GROUP_CALL_END)); }
    public void demanderListeUtilisateurs()           { envoyer(new Message(nomUtilisateur, "SERVER", "", TypeMessage.GET_USERS)); }

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

    public void setFenetreAppelActive(VideoCallWindow w)             { this.fenetreAppelActive = w; }
    public VideoCallWindow getFenetreAppelActive()                    { return fenetreAppelActive; }
    public void setFenetreAudioActive(AudioCallWindow w)             { this.fenetreAudioActive = w; }
    public AudioCallWindow getFenetreAudioActive()                    { return fenetreAudioActive; }
    public void setFenetreReunionActive(GroupAudioCallWindow w)      { this.fenetreReunionActive = w; }
    public GroupAudioCallWindow getFenetreReunionActive()             { return fenetreReunionActive; }
    public void setFenetreReunionVideoActive(GroupVideoCallWindow w) { this.fenetreReunionVideoActive = w; }
    public GroupVideoCallWindow getFenetreReunionVideoActive()        { return fenetreReunionVideoActive; }
    public void setFenetreVideoReunionActive(GroupVideoCallWindow w) { this.fenetreReunionVideoActive = w; }

    private void demarrerEcoute() {
        Thread t = new Thread(() -> {
            try {
                Message m;
                while (connecte && (m = (Message) fluxEntree.readObject()) != null)
                    traiterMessageRecu(m);
            } catch (Exception e) {
                if (connecte) { System.err.println("[Client] Connexion perdue : " + e.getMessage()); connecte = false; }
            }
        });
        t.setDaemon(true);
        t.setName("ClientListener");
        t.start();
    }

    private void traiterMessageRecu(Message m) {
        TypeMessage t = m.getType();
        if (t != TypeMessage.VIDEO && t != TypeMessage.AUDIO
                && t != TypeMessage.AUDIO_ONLY && t != TypeMessage.GROUP_AUDIO
                && t != TypeMessage.GROUP_VIDEO) {
            System.out.println("[Client RX] " + t + " de " + m.getExpediteur());
        }

        switch (m.getType()) {
            case CONNECT:       gererReponseConnexion(m); break;
            case MESSAGE:
                MessageDAO.sauvegarder(m);
                if (MainApp.getControleurChat() != null) MainApp.getControleurChat().afficherMessage(m);
                break;
            case VOICE_MESSAGE:
                if (MainApp.getControleurChat() != null) MainApp.getControleurChat().afficherMessageVocal(m);
                break;
            case FILE:
                if (MainApp.getControleurChat() != null) MainApp.getControleurChat().afficherFichier(m);
                break;
            case GET_USERS:
                if (MainApp.getControleurChat() != null) MainApp.getControleurChat().mettreAJourListeUtilisateurs(m.getContenu());
                break;
            case CALL_REQUEST:
                if (MainApp.getControleurChat() != null) MainApp.getControleurChat().afficherDemandeAppel(m);
                break;
            case CALL_ACCEPT:
                if (MainApp.getControleurChat() != null) MainApp.getControleurChat().gererAcceptationAppel(m);
                break;
            case CALL_REJECT:
                if (MainApp.getControleurChat() != null) MainApp.getControleurChat().gererRefusAppel(m);
                break;
            case CALL_END:
                if (fenetreAppelActive != null) { fenetreAppelActive.terminerAppelExterieur(); fenetreAppelActive = null; }
                break;
            case VIDEO:
                if (fenetreAppelActive != null) fenetreAppelActive.afficherFrameDistante(m);
                break;
            case AUDIO:
                if (fenetreAppelActive != null) fenetreAppelActive.jouerAudioDistant(m);
                break;
            case AUDIO_CALL_REQUEST:
                if (MainApp.getControleurChat() != null) MainApp.getControleurChat().afficherDemandeAppelAudio(m);
                break;
            case AUDIO_CALL_ACCEPT:
                if (MainApp.getControleurChat() != null) MainApp.getControleurChat().gererAcceptationAppelAudio(m);
                break;
            case AUDIO_CALL_REJECT:
                if (MainApp.getControleurChat() != null) MainApp.getControleurChat().gererRefusAppelAudio(m);
                break;
            case AUDIO_CALL_END:
                if (fenetreAudioActive != null) { fenetreAudioActive.terminerAppelExterieur(); fenetreAudioActive = null; }
                break;
            case AUDIO_ONLY:
                if (fenetreAudioActive != null) fenetreAudioActive.jouerAudio(m);
                break;
            case GROUP_INFO:
                traiterInfoGroupe(m);
                break;
            case GROUP_MESSAGE:
                if (MainApp.getControleurChat() != null) MainApp.getControleurChat().afficherMessageGroupe(m);
                break;
            case GROUP_CALL_REQUEST:
                if (MainApp.getControleurChat() != null) MainApp.getControleurChat().afficherDemandeReunion(m);
                break;
            case GROUP_CALL_ACCEPT:
                if (fenetreReunionActive != null) {
                    final String membre = m.getExpediteur();
                    javax.swing.SwingUtilities.invokeLater(() -> fenetreReunionActive.membreRejoint(membre));
                } else if (fenetreReunionVideoActive != null) {
                    final String membre = m.getExpediteur();
                    javax.swing.SwingUtilities.invokeLater(() -> fenetreReunionVideoActive.membreRejoint(membre));
                }
                if (MainApp.getControleurChat() != null) MainApp.getControleurChat().membreARejoindreReunion(m);
                break;
            case GROUP_CALL_END:
                if (fenetreReunionActive != null) fenetreReunionActive.membreParti(m.getExpediteur());
                if (fenetreReunionVideoActive != null) fenetreReunionVideoActive.membreParti(m.getExpediteur());
                if (MainApp.getControleurChat() != null) MainApp.getControleurChat().membreAQuitteReunion(m);
                break;
            case GROUP_AUDIO:
                if (fenetreReunionActive != null) fenetreReunionActive.jouerAudio(m);
                else if (fenetreReunionVideoActive != null) fenetreReunionVideoActive.jouerAudio(m);
                else if (fenetreAudioActive != null) fenetreAudioActive.jouerAudio(m);
                break;
            case GROUP_VIDEO:
                if (fenetreReunionVideoActive != null) fenetreReunionVideoActive.afficherFrameDistante(m);
                break;
            case DISCONNECT:
            default:
                break;
        }
    }

    private void traiterInfoGroupe(Message m) {
        String[] parts = m.getContenu().split("::", 2);
        if (parts.length < 2) return;
        String nomGroupe  = parts[0].trim();
        String membresStr = parts[1].trim();
        if (membresStr.isEmpty()) {
            GroupDAO.supprimer(nomGroupe);
        } else {
            List<String> membres = Arrays.asList(membresStr.split(","));
            GroupDAO.sauvegarderOuMettreAJour(nomGroupe, membres);
        }
        if (MainApp.getControleurChat() != null) MainApp.getControleurChat().rafraichirGroupes();
    }

    private void gererReponseConnexion(Message message) {
        String contenu = message.getContenu();
        if ("LOGIN_OK".equals(contenu)) {
            System.out.println("[Client] Login accepté.");
            if (MainApp.getControleurLogin() != null) MainApp.getControleurLogin().surConnexionReussie();
        } else if ("LOGIN_FAIL".equals(contenu)) {
            System.out.println("[Client] Login refusé.");
            connecte = false;
            if (MainApp.getControleurLogin() != null) MainApp.getControleurLogin().surEchecConnexion();
        }
    }
}