package ui;

import database.ContactDAO;
import database.GroupDAO;
import database.MessageDAO;
import model.Message;
import model.TypeMessage;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class ChatController {

    @FXML private Label      labelNomUtilisateur;
    @FXML private VBox       listeContacts;
    @FXML private TabPane    tabPane;
    @FXML private VBox       zoneMessages;
    @FXML private ScrollPane scrollMessages;
    @FXML private TextField  champMessage;
    @FXML private Button     boutonEnvoyer;
    @FXML private Button     boutonFichier;
    @FXML private Button     boutonVocal;
    @FXML private Button     boutonReunion;
    @FXML private Button     boutonReunionVideo;
    @FXML private Label      labelInfo;
    @FXML private Button     boutonAppelVideo;
    @FXML private Button     boutonAppelAudio;
    @FXML private Label      labelContactActif;
    @FXML private Label      labelStatutContact;
    @FXML private VBox       listeGroupes;
    @FXML private VBox       zoneMessagesGroupe;
    @FXML private ScrollPane scrollMessagesGroupe;
    @FXML private TextField  champMessageGroupe;
    @FXML private Button     boutonFichierGroupe;
    @FXML private Button     boutonVocalGroupe;
    @FXML private Label      labelGroupeActif;
    @FXML private Label      labelMembresGroupe;

    private String contactActif = null;
    private String groupeActif  = null;
    private final Set<String>                utilisateursEnLigne = new HashSet<>();
    private final Map<String, List<Message>> messagesEnAttente   = new HashMap<>();
    private media.VoiceRecorder voiceRecorder = null;
    private boolean enregistrement = false;
    private media.VoiceRecorder voiceRecorderGroupe = null;
    private boolean enregistrementGroupe = false;
    private final Map<String, List<Message>> messagesGrpAttente  = new HashMap<>();

    private static final String[] COULEURS = {
            "#3C3489", "#712B13", "#0F6E56", "#0C447C",
            "#633806", "#1D6B3A", "#7B2D8B", "#8B4513"
    };

    private static final String BUBBLE_SENT     = "#D9FDD3";
    private static final String BUBBLE_RECEIVED = "#FFFFFF";
    private static final String TEXT_SENT       = "#111B21";
    private static final String TEXT_RECEIVED   = "#111B21";
    private static final String TEXT_META       = "#667781";
    private static final String HOVER_BG        = "#F5F6F6";
    private static final String CONTACT_TEXT    = "#111B21";
    private static final String CONTACT_SUB     = "#667781";
    private static final String BTN_ADD_BG      = "#F0F2F5";
    private static final String BTN_ADD_TEXT    = "#00A884";
    private static final String BADGE_BG        = "#00A884";
    private static final String GROUP_AVATAR    = "#6B7B8D";
    private static final String FILE_RECV_BG    = "#F0F2F5";
    private static final String SENDER_COLOR    = "#00A884";

    @FXML
    public void initialize() {
        try {
            String nom = MainApp.client.getNomUtilisateur();
            labelNomUtilisateur.setText(nom != null ? nom : "?");
            champMessage.setOnAction(e -> envoyerMessage());
            if (champMessageGroupe != null)
                champMessageGroupe.setOnAction(e -> envoyerMessageGroupe());
            zoneMessages.heightProperty().addListener((obs, o, n) -> scrollMessages.setVvalue(1.0));
            zoneMessages.prefWidthProperty().bind(scrollMessages.widthProperty().subtract(20));
            zoneMessagesGroupe.prefWidthProperty().bind(scrollMessagesGroupe.widthProperty().subtract(20));
            if (scrollMessagesGroupe != null && zoneMessagesGroupe != null)
                zoneMessagesGroupe.heightProperty().addListener((obs, o, n) -> scrollMessagesGroupe.setVvalue(1.0));
            rafraichirListeContacts();
            try { rafraichirGroupes(); } catch (Exception e) {
                System.err.println("[ChatController.initialize] Groupes non chargés : " + e.getMessage());
            }
            MainApp.client.demanderListeUtilisateurs();
        } catch (Exception e) {
            System.err.println("[ChatController.initialize] Erreur : " + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML public void envoyerMessage() {
        if (contactActif == null) return;
        String texte = champMessage.getText().trim();
        if (texte.isEmpty()) return;
        String heure = heure();
        ajouterBubble(zoneMessages, texte, true, MainApp.client.getNomUtilisateur(), heure);
        champMessage.clear();
        MainApp.client.envoyerMessage(contactActif, texte);
    }

    @FXML public void envoyerFichier() {
        if (contactActif == null) { afficherInfo("Sélectionnez d'abord un contact."); return; }
        FileChooser fc = new FileChooser();
        fc.setTitle("Choisir un fichier à envoyer");
        File f = fc.showOpenDialog(null);
        if (f == null) return;
        try {
            byte[] octets = Files.readAllBytes(f.toPath());
            String b64    = java.util.Base64.getEncoder().encodeToString(octets);
            String contenu = f.getName() + "||" + b64;
            Message m = new Message(MainApp.client.getNomUtilisateur(), contactActif, contenu, TypeMessage.FILE);
            MainApp.client.envoyer(m);
            MessageDAO.sauvegarderFichier(MainApp.client.getNomUtilisateur(), contactActif, f.getName(), true);
            ajouterBubbleFichier(zoneMessages, f.getName(), true, heure());
        } catch (IOException e) { afficherInfo("Erreur lecture fichier : " + e.getMessage()); }
    }

    @FXML public void lancerAppelVideo() {
        if (contactActif == null) { afficherInfo("Sélectionnez d'abord un contact."); return; }
        MainApp.client.demanderAppel(contactActif);
        afficherInfo("Appel vidéo envoyé à " + contactActif + "...");
    }

    @FXML public void lancerAppelAudio() {
        if (contactActif == null) { afficherInfo("Sélectionnez d'abord un contact."); return; }
        MainApp.client.demanderAppelAudio(contactActif);
        afficherInfo("Appel audio envoyé à " + contactActif + "...");
    }

    @FXML public void seDeconnecter() {
        MainApp.client.deconnecter();
        MainApp.changerScene("login.fxml");
    }

    @FXML public void ajouterContact() {
        List<String> suggestions = new ArrayList<>();
        for (String u : utilisateursEnLigne) {
            if (!u.equals(MainApp.client.getNomUtilisateur()) && !ContactDAO.existe(u))
                suggestions.add(u);
        }
        if (!suggestions.isEmpty()) {
            ChoiceDialog<String> choix = new ChoiceDialog<>(suggestions.get(0), suggestions);
            choix.setTitle("Ajouter un contact");
            choix.setHeaderText("Utilisateurs en ligne disponibles :");
            choix.setContentText("Choisir :");
            choix.showAndWait().ifPresent(nom -> {
                if (ContactDAO.ajouter(nom)) rafraichirListeContacts();
                else afficherInfo("Ce contact existe déjà dans votre liste.");
            });
            Alert lien = new Alert(Alert.AlertType.CONFIRMATION);
            lien.setTitle("Ou");
            lien.setHeaderText(null);
            lien.setContentText("Voulez-vous ajouter un contact par son nom ?");
            lien.getButtonTypes().setAll(
                    new ButtonType("Oui, saisir un nom", ButtonBar.ButtonData.YES),
                    new ButtonType("Non", ButtonBar.ButtonData.CANCEL_CLOSE));
            lien.showAndWait().ifPresent(rep -> {
                if (rep.getButtonData() == ButtonBar.ButtonData.YES) ajouterContactParNom();
            });
        } else { ajouterContactParNom(); }
    }

    private void ajouterContactParNom() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Ajouter un contact");
        dialog.setHeaderText("Entrez le nom d'utilisateur :");
        dialog.setContentText("Nom :");
        dialog.showAndWait().ifPresent(nom -> {
            nom = nom.trim();
            if (nom.isEmpty()) return;
            if (nom.equals(MainApp.client.getNomUtilisateur())) { afficherInfo("Vous ne pouvez pas vous ajouter vous-même."); return; }
            if (ContactDAO.existe(nom)) { afficherInfo("\"" + nom + "\" est déjà dans vos contacts."); return; }
            ContactDAO.ajouter(nom);
            rafraichirListeContacts();
            afficherInfo("Contact \"" + nom + "\" ajouté.");
        });
    }

    @FXML public void creerGroupe() {
        TextInputDialog d = new TextInputDialog();
        d.setTitle("Créer un groupe");
        d.setHeaderText("Nom du nouveau groupe :");
        d.setContentText("Nom :");
        d.showAndWait().ifPresent(nom -> {
            nom = nom.trim();
            if (nom.isEmpty()) return;
            if (database.LocalDatabase.getUtilisateurCourant() == null) {
                afficherInfo("⚠ La base de données n'est pas encore prête.");
                return;
            }
            final String nomFinal = nom;
            MainApp.client.creerGroupe(nomFinal);
            List<String> membres = new ArrayList<>();
            membres.add(MainApp.client.getNomUtilisateur());
            try { GroupDAO.sauvegarderOuMettreAJour(nomFinal, membres); } catch (Exception e) {}
            rafraichirGroupes();
            if (tabPane != null && tabPane.getTabs().size() >= 2) tabPane.getSelectionModel().select(1);
            afficherInfo("Groupe \"" + nomFinal + "\" en cours de création...");
        });
    }

    @FXML public void envoyerMessageGroupe() {
        if (groupeActif == null) return;
        String texte = champMessageGroupe.getText().trim();
        if (texte.isEmpty()) return;
        String heure = heure();
        ajouterBubble(zoneMessagesGroupe, texte, true, MainApp.client.getNomUtilisateur(), heure);
        champMessageGroupe.clear();
        try { GroupDAO.sauvegarderMessage(groupeActif, MainApp.client.getNomUtilisateur(), texte); } catch (Exception ex) {}
        MainApp.client.envoyerMessageGroupe(groupeActif, texte);
    }

    // Réunion AUDIO
    @FXML public void lancerReunionGroupe() {
        System.out.println("[DEBUG] groupeActif = '" + groupeActif + "'");
        System.out.println("[DEBUG] utilisateur = '" + MainApp.client.getNomUtilisateur() + "'");
        if (groupeActif == null) { afficherInfo("Sélectionnez d'abord un groupe."); return; }
        final String groupe = groupeActif;
        MainApp.client.demarrerReunionGroupe(groupe);
        afficherInfo("Réunion audio lancée pour \"" + groupe + "\"");
        javax.swing.SwingUtilities.invokeLater(() -> {
            try {
                GroupAudioCallWindow w = new GroupAudioCallWindow(MainApp.client, groupe);
                MainApp.client.setFenetreReunionActive(w);
            } catch (Exception e) { System.err.println("[ReunionAudio] Erreur : " + e.getMessage()); }
        });
    }

    // Réunion VIDÉO
    @FXML public void lancerReunionVideoGroupe() {
        System.out.println("[DEBUG VIDEO] groupeActif = '" + groupeActif + "'");
        System.out.println("[DEBUG VIDEO] utilisateur = '" + MainApp.client.getNomUtilisateur() + "'");
        if (groupeActif == null) { afficherInfo("Sélectionnez d'abord un groupe."); return; }
        final String groupe = groupeActif;
        MainApp.client.demarrerReunionGroupeVideo(groupe);
        afficherInfo("Réunion vidéo lancée pour \"" + groupe + "\"");
        javax.swing.SwingUtilities.invokeLater(() -> {
            try {
                GroupVideoCallWindow w = new GroupVideoCallWindow(MainApp.client, groupe);
                MainApp.client.setFenetreReunionVideoActive(w);
            } catch (Exception e) { System.err.println("[ReunionVideo] Erreur : " + e.getMessage()); }
        });
    }

    @FXML public void envoyerFichierGroupe() {
        if (groupeActif == null) { afficherInfo("Sélectionnez d'abord un groupe."); return; }
        FileChooser fc = new FileChooser();
        fc.setTitle("Choisir un fichier à envoyer au groupe");
        File f = fc.showOpenDialog(null);
        if (f == null) return;
        try {
            byte[] octets = Files.readAllBytes(f.toPath());
            String b64    = java.util.Base64.getEncoder().encodeToString(octets);
            String contenu = "FILE:" + f.getName() + "||" + b64;
            Message m = new Message(MainApp.client.getNomUtilisateur(), groupeActif, contenu, TypeMessage.GROUP_MESSAGE);
            MainApp.client.envoyer(m);
            GroupDAO.sauvegarderMessageAvecType(groupeActif, MainApp.client.getNomUtilisateur(), f.getName(), "FILE");
            ajouterBubbleFichier(zoneMessagesGroupe, f.getName(), true, heure());
        } catch (IOException e) { afficherInfo("Erreur lecture fichier : " + e.getMessage()); }
    }

    @FXML public void enregistrerVocalGroupe() {
        if (!enregistrementGroupe) {
            try {
                voiceRecorderGroupe = new media.VoiceRecorder();
                voiceRecorderGroupe.startRecording();
                enregistrementGroupe = true;
                if (boutonVocalGroupe != null) {
                    boutonVocalGroupe.setGraphic(null);
                    boutonVocalGroupe.setText("⏹");
                    boutonVocalGroupe.setStyle("-fx-background-color:transparent; -fx-font-size:18px; -fx-cursor:hand; -fx-text-fill:#E8534A;");
                }
                afficherInfo("Enregistrement en cours... Cliquer ⏹ pour envoyer");
            } catch (Exception e) { afficherInfo("Impossible d'accéder au microphone : " + e.getMessage()); }
        } else {
            enregistrementGroupe = false;
            if (boutonVocalGroupe != null) {
                boutonVocalGroupe.setText("");
                boutonVocalGroupe.setStyle("-fx-background-color:transparent; -fx-cursor:hand;");
                org.kordamp.ikonli.javafx.FontIcon mic = new org.kordamp.ikonli.javafx.FontIcon("fas-microphone");
                mic.setIconSize(20);
                mic.setIconColor(javafx.scene.paint.Color.web("#54656F"));
                boutonVocalGroupe.setGraphic(mic);
            }
            if (voiceRecorderGroupe != null) {
                String b64 = voiceRecorderGroupe.stopRecording();
                if (b64 != null && groupeActif != null) {
                    String chemin = null;
                    try {
                        byte[] wav = java.util.Base64.getDecoder().decode(b64);
                        java.io.File dir = new java.io.File("received_files");
                        if (!dir.exists()) dir.mkdirs();
                        java.io.File wf = new java.io.File(dir, "voice_grp_sent_" + System.currentTimeMillis() + ".wav");
                        java.nio.file.Files.write(wf.toPath(), wav);
                        chemin = wf.getAbsolutePath();
                    } catch (Exception ex) {}
                    Message m = new Message(MainApp.client.getNomUtilisateur(), groupeActif, "VOICE:" + b64, TypeMessage.GROUP_MESSAGE);
                    MainApp.client.envoyer(m);
                    if (chemin != null) GroupDAO.sauvegarderMessageAvecType(groupeActif, MainApp.client.getNomUtilisateur(), chemin, "VOICE_MESSAGE");
                    ajouterBubbleVocaleAvecFichier(zoneMessagesGroupe, "0:??", true, heure(), chemin);
                    afficherInfo("Message vocal envoyé au groupe ✓");
                }
                voiceRecorderGroupe = null;
            }
        }
    }

    private void ajouterMembreAuGroupe(String nomGroupe) {
        List<String> disponibles = new ArrayList<>(utilisateursEnLigne);
        List<String> deja = GroupDAO.getMembres(nomGroupe);
        disponibles.removeAll(deja);
        disponibles.remove(MainApp.client.getNomUtilisateur());
        if (!disponibles.isEmpty()) {
            ChoiceDialog<String> choix = new ChoiceDialog<>(disponibles.get(0), disponibles);
            choix.setTitle("Ajouter un membre");
            choix.setHeaderText("Utilisateurs en ligne :");
            choix.setContentText("Membre à ajouter :");
            choix.showAndWait().ifPresent(membre -> MainApp.client.ajouterMembreGroupe(nomGroupe, membre));
        } else {
            TextInputDialog d = new TextInputDialog();
            d.setTitle("Ajouter un membre");
            d.setHeaderText("Entrez le nom d'utilisateur :");
            d.setContentText("Nom :");
            d.showAndWait().ifPresent(mm -> { String mt = mm.trim(); if (!mt.isEmpty()) MainApp.client.ajouterMembreGroupe(nomGroupe, mt); });
        }
    }

    private void retirerMembreDuGroupe(String nomGroupe) {
        List<String> membres = GroupDAO.getMembres(nomGroupe);
        membres.remove(MainApp.client.getNomUtilisateur());
        if (membres.isEmpty()) { afficherInfo("Aucun autre membre à retirer."); return; }
        ChoiceDialog<String> choix = new ChoiceDialog<>(membres.get(0), membres);
        choix.setTitle("Retirer un membre");
        choix.setHeaderText("Retirer du groupe \"" + nomGroupe + "\" :");
        choix.setContentText("Membre :");
        choix.showAndWait().ifPresent(mm -> MainApp.client.retirerMembreGroupe(nomGroupe, mm));
    }

    public void afficherMessage(Message message) {
        Platform.runLater(() -> {
            String exp = message.getExpediteur();
            if (!ContactDAO.existe(exp) && !exp.equals(MainApp.client.getNomUtilisateur())) {
                ContactDAO.ajouter(exp);
            }
            String heure = heure();
            if (exp.equals(contactActif)) {
                ajouterBubble(zoneMessages, message.getContenu(), false, exp, heure);
            } else {
                messagesEnAttente.computeIfAbsent(exp, k -> new ArrayList<>()).add(message);
                // Petit délai pour laisser tous les messages arriver avant de rafraîchir
                // Comme ça le badge affiche le bon nombre de messages
                javafx.animation.PauseTransition pause =
                        new javafx.animation.PauseTransition(javafx.util.Duration.millis(500));
                pause.setOnFinished(e -> rafraichirListeContacts());
                pause.play();
            }
        });
    }

    public void afficherFichier(Message message) {
        Platform.runLater(() -> {
            String exp = message.getExpediteur();
            String[] parties = message.getContenu().split("\\|\\|", 2);
            String nomFichier = parties.length > 0 ? parties[0] : "fichier";
            if (parties.length == 2) sauvegarderFichierRecu(nomFichier, parties[1]);
            MessageDAO.sauvegarderFichier(exp, MainApp.client.getNomUtilisateur(), nomFichier, false);
            String heure = heure();
            if (exp.equals(contactActif)) ajouterBubbleFichier(zoneMessages, nomFichier, false, heure);
            else { messagesEnAttente.computeIfAbsent(exp, k -> new ArrayList<>()).add(message); rafraichirListeContacts(); }
        });
    }

    public void afficherMessageGroupe(Message message) {
        Platform.runLater(() -> {
            String groupe  = message.getDestinataire();
            String exp     = message.getExpediteur();
            String contenu = message.getContenu();
            String heure   = heure();
            if (groupe.equals(groupeActif) && labelMembresGroupe != null) {
                List<String> membres = GroupDAO.getMembres(groupe);
                labelMembresGroupe.setText("Membres : " + String.join(", ", membres));
            }
            if (groupe.equals(groupeActif) && zoneMessagesGroupe != null) {
                if (!contenu.startsWith("FILE:") && !contenu.startsWith("VOICE:"))
                    try { GroupDAO.sauvegarderMessage(groupe, exp, contenu); } catch (Exception ex) {}
                if (contenu.startsWith("FILE:")) {
                    String reste = contenu.substring(5);
                    String[] parties = reste.split("\\|\\|", 2);
                    String nomFichier = parties.length > 0 ? parties[0] : "fichier";
                    if (parties.length == 2) sauvegarderFichierRecu(nomFichier, parties[1]);
                    GroupDAO.sauvegarderMessageAvecType(groupe, exp, nomFichier, "FILE");
                    ajouterBubbleFichierGroupe(zoneMessagesGroupe, nomFichier, false, heure, exp);
                } else if (contenu.startsWith("VOICE:")) {
                    try {
                        byte[] wav = java.util.Base64.getDecoder().decode(contenu.substring(6));
                        java.io.File dir = new java.io.File("received_files");
                        if (!dir.exists()) dir.mkdirs();
                        java.io.File wf = new java.io.File(dir, "voice_grp_" + exp + "_" + System.currentTimeMillis() + ".wav");
                        java.nio.file.Files.write(wf.toPath(), wav);
                        GroupDAO.sauvegarderMessageAvecType(groupe, exp, wf.getAbsolutePath(), "VOICE_MESSAGE");
                        ajouterBubbleVocaleGroupe(zoneMessagesGroupe, false, heure, wf.getAbsolutePath(), exp);
                    } catch (Exception ex) { System.err.println("[Voice Groupe] " + ex.getMessage()); }
                } else ajouterBubble(zoneMessagesGroupe, contenu, false, exp, heure);
            } else { messagesGrpAttente.computeIfAbsent(groupe, k -> new ArrayList<>()).add(message); rafraichirGroupes(); }
        });
    }

    public void mettreAJourListeUtilisateurs(String contenu) {
        Platform.runLater(() -> {
            utilisateursEnLigne.clear();
            if (contenu != null && !contenu.isBlank())
                for (String n : contenu.split(",")) { String t = n.trim(); if (!t.isEmpty()) utilisateursEnLigne.add(t); }
            rafraichirListeContacts();
            if (contactActif != null) actualiserStatutContact();
        });
    }

    public void rafraichirGroupes() {
        Platform.runLater(() -> { try { construireListeGroupes(); } catch (Exception e) {} });
    }

    public void afficherDemandeAppel(Message message) {
        Platform.runLater(() -> {
            String appelant = message.getExpediteur();
            Alert a = new Alert(Alert.AlertType.CONFIRMATION);
            a.setTitle("Appel vidéo entrant");
            a.setHeaderText(appelant + " vous appelle en vidéo");
            a.setContentText("Accepter ?");
            ButtonType ok = new ButtonType("Accepter", ButtonBar.ButtonData.YES);
            ButtonType non = new ButtonType("Refuser", ButtonBar.ButtonData.NO);
            a.getButtonTypes().setAll(ok, non);
            a.showAndWait().ifPresent(rep -> { if (rep == ok) { MainApp.client.accepterAppel(appelant); ouvrirFenetreVideoCall(appelant); } else MainApp.client.refuserAppel(appelant); });
        });
    }

    public void gererAcceptationAppel(Message message) { Platform.runLater(() -> ouvrirFenetreVideoCall(message.getExpediteur())); }
    public void gererRefusAppel(Message message) { Platform.runLater(() -> afficherInfo(message.getExpediteur() + " a refusé votre appel vidéo.")); }

    public void afficherDemandeAppelAudio(Message message) {
        Platform.runLater(() -> {
            String appelant = message.getExpediteur();
            Alert a = new Alert(Alert.AlertType.CONFIRMATION);
            a.setTitle("Appel audio entrant");
            a.setHeaderText(appelant + " vous appelle (audio)");
            a.setContentText("Accepter ?");
            ButtonType ok = new ButtonType("Accepter", ButtonBar.ButtonData.YES);
            ButtonType non = new ButtonType("Refuser", ButtonBar.ButtonData.NO);
            a.getButtonTypes().setAll(ok, non);
            a.showAndWait().ifPresent(rep -> { if (rep == ok) { MainApp.client.accepterAppelAudio(appelant); ouvrirFenetreAudioCall(appelant); } else MainApp.client.refuserAppelAudio(appelant); });
        });
    }

    public void gererAcceptationAppelAudio(Message message) { Platform.runLater(() -> ouvrirFenetreAudioCall(message.getExpediteur())); }
    public void gererRefusAppelAudio(Message message) { Platform.runLater(() -> afficherInfo(message.getExpediteur() + " a refusé votre appel audio.")); }


    public void afficherDemandeReunion(Message message) {
        Platform.runLater(() -> {
            String groupe = message.getDestinataire();
            String initiateur = message.getExpediteur();
            boolean isVideo = "VIDEO".equals(message.getContenu());
            Alert a = new Alert(Alert.AlertType.CONFIRMATION);
            a.setTitle("Réunion de groupe");
            a.setHeaderText(initiateur + " démarre une réunion " + (isVideo ? "vidéo" : "audio") + " dans \"" + groupe + "\"");
            a.setContentText("Voulez-vous rejoindre ?");
            ButtonType ok = new ButtonType("Rejoindre", ButtonBar.ButtonData.YES);
            ButtonType non = new ButtonType("Ignorer", ButtonBar.ButtonData.NO);
            a.getButtonTypes().setAll(ok, non);
            a.showAndWait().ifPresent(rep -> {
                if (rep == ok) {
                    if (isVideo) {
                        javax.swing.SwingUtilities.invokeLater(() -> {
                            try {

                                GroupVideoCallWindow w = new GroupVideoCallWindow(MainApp.client, groupe);
                                MainApp.client.setFenetreReunionVideoActive(w);

                                MainApp.client.rejoindreReunionGroupe(groupe);
                            } catch (Exception e) { System.err.println("[ReunionVideo] Erreur : " + e.getMessage()); }
                        });
                    } else {
                        javax.swing.SwingUtilities.invokeLater(() -> {
                            try {

                                GroupAudioCallWindow w = new GroupAudioCallWindow(MainApp.client, groupe);
                                MainApp.client.setFenetreReunionActive(w);

                                MainApp.client.rejoindreReunionGroupe(groupe);
                            } catch (Exception e) { System.err.println("[ReunionAudio] Erreur : " + e.getMessage()); }
                        });
                    }
                }
            });
        });
    }

    public void membreARejoindreReunion(Message message) { Platform.runLater(() -> afficherInfo(message.getExpediteur() + " a rejoint la réunion.")); }
    public void membreAQuitteReunion(Message message) { Platform.runLater(() -> afficherInfo(message.getExpediteur() + " a quitté la réunion.")); }

    private void rafraichirListeContacts() {
        listeContacts.getChildren().clear();
        Button btnAjouter = new Button("➕ Ajouter un contact");
        btnAjouter.setMaxWidth(Double.MAX_VALUE);
        btnAjouter.setStyle("-fx-background-color:" + BTN_ADD_BG + "; -fx-text-fill:" + BTN_ADD_TEXT + "; " +
                "-fx-font-weight:bold; -fx-background-radius:8px; -fx-cursor:hand; -fx-font-size:12px; -fx-padding:10px;");
        btnAjouter.setOnAction(e -> ajouterContact());
        listeContacts.getChildren().add(btnAjouter);
        List<String> contacts = ContactDAO.lister();
        for (String enLigne : utilisateursEnLigne)
            if (!enLigne.equals(MainApp.client.getNomUtilisateur()) && !contacts.contains(enLigne)) contacts.add(enLigne);
        for (String nom : contacts) {
            if (nom.equals(MainApp.client.getNomUtilisateur())) continue;
            listeContacts.getChildren().add(creerLigneContact(nom));
        }
    }

    private void construireListeGroupes() {
        if (listeGroupes == null) return;
        listeGroupes.getChildren().clear();
        Button btnCreer = new Button("➕ Créer un groupe");
        btnCreer.setMaxWidth(Double.MAX_VALUE);
        btnCreer.setStyle("-fx-background-color:" + BTN_ADD_BG + "; -fx-text-fill:" + BTN_ADD_TEXT + "; " +
                "-fx-font-weight:bold; -fx-background-radius:8px; -fx-cursor:hand; -fx-font-size:12px; -fx-padding:10px;");
        btnCreer.setOnAction(e -> creerGroupe());
        listeGroupes.getChildren().add(btnCreer);
        List<String[]> groupesLocaux = GroupDAO.listerGroupes();
        for (String[] g : groupesLocaux) listeGroupes.getChildren().add(creerLigneGroupe(g[0], g[1]));
    }

    private HBox creerLigneContact(String nom) {
        boolean enLigne = utilisateursEnLigne.contains(nom);
        boolean dansContacts = ContactDAO.existe(nom);
        Label avatar = creerAvatar(nom);
        Label labelNom = new Label(nom);
        labelNom.setFont(Font.font("Arial", FontWeight.BOLD, 13));
        labelNom.setStyle("-fx-text-fill:" + CONTACT_TEXT + ";");
        Label statut = new Label(enLigne ? "● En ligne" : "○ Hors ligne");
        statut.setFont(Font.font("Arial", 11));
        statut.setStyle("-fx-text-fill:" + (enLigne ? "#25D366" : CONTACT_SUB) + ";");
        VBox info = new VBox(2, labelNom, statut);
        int n = messagesEnAttente.containsKey(nom) ? messagesEnAttente.get(nom).size() : 0;
        Label badge = new Label(n > 0 ? String.valueOf(n) : "");
        badge.setVisible(n > 0);
        badge.setMinSize(20, 20);
        badge.setAlignment(Pos.CENTER);
        badge.setStyle("-fx-background-color:" + BADGE_BG + "; -fx-background-radius:10px; -fx-text-fill:white; -fx-font-size:10px; -fx-font-weight:bold;");
        HBox ligne = new HBox(10, avatar, info, new Pane(), badge);
        HBox.setHgrow(info, Priority.ALWAYS);
        ligne.setAlignment(Pos.CENTER_LEFT);
        ligne.setPadding(new Insets(10, 12, 10, 12));
        ligne.setStyle("-fx-cursor:hand;");
        ligne.setOnMouseEntered(e -> ligne.setStyle("-fx-background-color:" + HOVER_BG + "; -fx-cursor:hand;"));
        ligne.setOnMouseExited(e  -> ligne.setStyle("-fx-background-color:transparent; -fx-cursor:hand;"));
        ligne.setOnMouseClicked(e -> {
            if (e.getButton() == javafx.scene.input.MouseButton.SECONDARY && dansContacts) {
                ContextMenu menu = new ContextMenu();
                MenuItem suppr = new MenuItem("Supprimer ce contact");
                suppr.setOnAction(ev -> { ContactDAO.supprimer(nom); if (nom.equals(contactActif)) contactActif = null; rafraichirListeContacts(); });
                menu.getItems().add(suppr);
                menu.show(ligne, e.getScreenX(), e.getScreenY());
            } else ouvrirContact(nom);
        });
        return ligne;
    }

    private HBox creerLigneGroupe(String nom, String membres) {
        Label avatar = new Label("G");
        avatar.setMinSize(42, 42); avatar.setMaxSize(42, 42);
        avatar.setAlignment(Pos.CENTER);
        avatar.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        avatar.setTextFill(Color.WHITE);
        avatar.setStyle("-fx-background-color:" + GROUP_AVATAR + "; -fx-background-radius:21px;");
        Label labelNom = new Label(nom);
        labelNom.setFont(Font.font("Arial", FontWeight.BOLD, 13));
        labelNom.setStyle("-fx-text-fill:" + CONTACT_TEXT + ";");
        int nbMembres = membres.isEmpty() ? 0 : membres.split(",").length;
        Label info2 = new Label(nbMembres + " membre(s)");
        info2.setFont(Font.font("Arial", 11));
        info2.setStyle("-fx-text-fill:" + CONTACT_SUB + ";");
        VBox info = new VBox(2, labelNom, info2);
        int n = messagesGrpAttente.containsKey(nom) ? messagesGrpAttente.get(nom).size() : 0;
        Label badge = new Label(n > 0 ? String.valueOf(n) : "");
        badge.setVisible(n > 0); badge.setMinSize(20, 20); badge.setAlignment(Pos.CENTER);
        badge.setStyle("-fx-background-color:" + BADGE_BG + "; -fx-background-radius:10px; -fx-text-fill:white; -fx-font-size:10px; -fx-font-weight:bold;");
        HBox ligne = new HBox(10, avatar, info, new Pane(), badge);
        HBox.setHgrow(info, Priority.ALWAYS);
        ligne.setAlignment(Pos.CENTER_LEFT);
        ligne.setPadding(new Insets(10, 12, 10, 12));
        ligne.setStyle("-fx-cursor:hand;");
        ligne.setOnMouseEntered(e -> ligne.setStyle("-fx-background-color:" + HOVER_BG + "; -fx-cursor:hand;"));
        ligne.setOnMouseExited(e  -> ligne.setStyle("-fx-background-color:transparent; -fx-cursor:hand;"));
        ligne.setOnMouseClicked(e -> {
            if (e.getButton() == javafx.scene.input.MouseButton.SECONDARY) {
                ContextMenu menu = new ContextMenu();
                MenuItem addM = new MenuItem("➕ Ajouter un membre"); addM.setOnAction(ev -> ajouterMembreAuGroupe(nom));
                MenuItem rmM = new MenuItem("➖ Retirer un membre"); rmM.setOnAction(ev -> retirerMembreDuGroupe(nom));
                MenuItem quitter = new MenuItem("🚪 Quitter le groupe");
                quitter.setOnAction(ev -> { MainApp.client.retirerMembreGroupe(nom, MainApp.client.getNomUtilisateur()); GroupDAO.supprimer(nom); if (nom.equals(groupeActif)) groupeActif = null; rafraichirGroupes(); });
                menu.getItems().addAll(addM, rmM, new SeparatorMenuItem(), quitter);
                menu.show(ligne, e.getScreenX(), e.getScreenY());
            } else ouvrirGroupe(nom);
        });
        return ligne;
    }

    private void ouvrirContact(String nom) {
        contactActif = nom; groupeActif = null;
        labelContactActif.setText(nom);
        actualiserStatutContact();
        if (labelGroupeActif != null)  { labelGroupeActif.setVisible(false);  labelGroupeActif.setManaged(false); }
        if (labelMembresGroupe != null) { labelMembresGroupe.setVisible(false); labelMembresGroupe.setManaged(false); }
        if (boutonAppelAudio != null) { boutonAppelAudio.setVisible(true); boutonAppelAudio.setManaged(true); }
        if (boutonAppelVideo != null) { boutonAppelVideo.setVisible(true); boutonAppelVideo.setManaged(true); }
        if (boutonReunion != null) { boutonReunion.setVisible(false); boutonReunion.setManaged(false); }
        if (boutonReunionVideo != null) { boutonReunionVideo.setVisible(false); boutonReunionVideo.setManaged(false); }
        scrollMessages.setVisible(true); scrollMessages.setManaged(true);
        javafx.scene.Node barrePrivee = champMessage.getParent();
        if (barrePrivee != null) { barrePrivee.setVisible(true); barrePrivee.setManaged(true); }
        if (scrollMessagesGroupe != null) { scrollMessagesGroupe.setVisible(false); scrollMessagesGroupe.setManaged(false); }
        javafx.scene.Node barreGroupe = champMessageGroupe != null ? champMessageGroupe.getParent() : null;
        if (barreGroupe != null) { barreGroupe.setVisible(false); barreGroupe.setManaged(false); }
        zoneMessages.getChildren().clear();
        List<Message> historique = MessageDAO.historiqueAvec(nom);
        for (Message m : historique) {
            boolean sortant = m.getExpediteur().equals(MainApp.client.getNomUtilisateur());
            if (m.getType() == TypeMessage.VOICE_MESSAGE) {
                ajouterBubbleVocaleAvecFichier(zoneMessages, "0:??", sortant, "", m.getContenu());
            } else if (m.getType() == TypeMessage.FILE) {
                ajouterBubbleFichier(zoneMessages, m.getContenu(), sortant, "");
            } else {
                ajouterBubble(zoneMessages, m.getContenu(), sortant, m.getExpediteur(), "");
            }
        }
        List<Message> attente = messagesEnAttente.remove(nom);
        if (attente != null) {
            for (Message m : attente) {
                String h = heure();
                if (m.getType() == TypeMessage.FILE) {
                    String[] p = m.getContenu().split("\\|\\|", 2);
                    ajouterBubbleFichier(zoneMessages, p.length > 0 ? p[0] : "fichier", false, h);
                } else if (m.getType() == TypeMessage.VOICE_MESSAGE) {
                    try {
                        byte[] wav = java.util.Base64.getDecoder().decode(m.getContenu());
                        java.io.File dir = new java.io.File("received_files");
                        if (!dir.exists()) dir.mkdirs();
                        java.io.File wf = new java.io.File(dir, "voice_" + m.getExpediteur() + "_" + System.currentTimeMillis() + ".wav");
                        java.nio.file.Files.write(wf.toPath(), wav);
                        ajouterBubbleVocaleAvecFichier(zoneMessages, "0:??", false, h, wf.getAbsolutePath());
                    } catch (Exception ex) {}
                } else ajouterBubble(zoneMessages, m.getContenu(), false, m.getExpediteur(), h);
            }
        }
        rafraichirListeContacts();
    }

    private void ouvrirGroupe(String nom) {
        groupeActif = nom; contactActif = null;
        if (boutonAppelAudio != null) { boutonAppelAudio.setVisible(false); boutonAppelAudio.setManaged(false); }
        if (boutonAppelVideo != null) { boutonAppelVideo.setVisible(false); boutonAppelVideo.setManaged(false); }
        if (boutonReunion != null) { boutonReunion.setVisible(true); boutonReunion.setManaged(true); }
        if (boutonReunionVideo != null) { boutonReunionVideo.setVisible(true); boutonReunionVideo.setManaged(true); }
        if (labelGroupeActif != null) { labelGroupeActif.setText("Groupe : " + nom); labelGroupeActif.setVisible(true); labelGroupeActif.setManaged(true); }
        List<String> membres = GroupDAO.getMembres(nom);
        if (labelMembresGroupe != null) { labelMembresGroupe.setText("Membres : " + String.join(", ", membres)); labelMembresGroupe.setVisible(true); labelMembresGroupe.setManaged(true); }
        if (labelContactActif  != null) labelContactActif.setText("");
        if (labelStatutContact != null) labelStatutContact.setText("");
        scrollMessages.setVisible(false); scrollMessages.setManaged(false);
        javafx.scene.Node barrePrivee = champMessage.getParent();
        if (barrePrivee != null) { barrePrivee.setVisible(false); barrePrivee.setManaged(false); }
        if (scrollMessagesGroupe != null) { scrollMessagesGroupe.setVisible(true); scrollMessagesGroupe.setManaged(true); }
        javafx.scene.Node barreGroupe = champMessageGroupe != null ? champMessageGroupe.getParent() : null;
        if (barreGroupe != null) { barreGroupe.setVisible(true); barreGroupe.setManaged(true); }
        boutonAppelVideo.setDisable(true); boutonAppelAudio.setDisable(true);
        if (zoneMessagesGroupe != null) {
            zoneMessagesGroupe.getChildren().clear();
            List<String[]> histo = GroupDAO.historique(nom);
            for (String[] row : histo) {
                boolean sortant = row[0].equals(MainApp.client.getNomUtilisateur());
                String type = row.length > 3 ? row[3] : "MESSAGE";
                if ("VOICE_MESSAGE".equals(type)) {
                    if (sortant) ajouterBubbleVocaleAvecFichier(zoneMessagesGroupe, "0:??", true, "", row[1]);
                    else ajouterBubbleVocaleGroupe(zoneMessagesGroupe, false, "", row[1], row[0]);
                } else if ("FILE".equals(type)) {
                    if (sortant) ajouterBubbleFichier(zoneMessagesGroupe, row[1], true, "");
                    else ajouterBubbleFichierGroupe(zoneMessagesGroupe, row[1], false, "", row[0]);
                } else {
                    ajouterBubble(zoneMessagesGroupe, row[1], sortant, row[0], "");
                }
            }
            List<Message> attente = messagesGrpAttente.remove(nom);
            if (attente != null) {
                for (Message m : attente) {
                    String cont = m.getContenu(); String exp2 = m.getExpediteur(); String h2 = heure();
                    if (cont.startsWith("FILE:")) {
                        String reste = cont.substring(5); String[] parties = reste.split("\\|\\|", 2);
                        String nomF = parties.length > 0 ? parties[0] : "fichier";
                        if (parties.length == 2) sauvegarderFichierRecu(nomF, parties[1]);
                        ajouterBubbleFichierGroupe(zoneMessagesGroupe, nomF, false, h2, exp2);
                    } else if (cont.startsWith("VOICE:")) {
                        try {
                            byte[] wav = java.util.Base64.getDecoder().decode(cont.substring(6));
                            java.io.File dir = new java.io.File("received_files");
                            if (!dir.exists()) dir.mkdirs();
                            java.io.File wf = new java.io.File(dir, "voice_grp_" + exp2 + "_" + System.currentTimeMillis() + ".wav");
                            java.nio.file.Files.write(wf.toPath(), wav);
                            ajouterBubbleVocaleGroupe(zoneMessagesGroupe, false, h2, wf.getAbsolutePath(), exp2);
                        } catch (Exception ex) {}
                    } else ajouterBubble(zoneMessagesGroupe, cont, false, exp2, h2);
                }
            }
        }
        rafraichirGroupes();
    }

    private void actualiserStatutContact() {
        if (contactActif == null) return;
        boolean enLigne = utilisateursEnLigne.contains(contactActif);
        labelStatutContact.setText(enLigne ? "● En ligne" : "○ Hors ligne");
        labelStatutContact.setStyle("-fx-text-fill:" + (enLigne ? "#25D366" : "#8696A0") + ";");
        boutonAppelVideo.setDisable(false); boutonAppelAudio.setDisable(false);
        champMessage.setDisable(false); boutonEnvoyer.setDisable(false); boutonFichier.setDisable(false);
        if (boutonVocal != null) boutonVocal.setDisable(false);
    }

    @FXML public void enregistrerVocal() {
        if (contactActif == null && groupeActif == null) { afficherInfo("Sélectionnez d'abord un contact."); return; }
        if (!enregistrement) {
            try {
                voiceRecorder = new media.VoiceRecorder();
                voiceRecorder.startRecording();
                enregistrement = true;
                boutonVocal.setGraphic(null);
                boutonVocal.setText("⏹");
                boutonVocal.setStyle("-fx-background-color:transparent; -fx-font-size:18px; -fx-cursor:hand; -fx-text-fill:#E8534A;");
                afficherInfo("Enregistrement en cours... Cliquer ⏹ pour envoyer");
            } catch (Exception e) { afficherInfo("Impossible d'accéder au microphone : " + e.getMessage()); }
        } else {
            enregistrement = false;
            boutonVocal.setText("");
            boutonVocal.setStyle("-fx-background-color:transparent; -fx-cursor:hand;");
            org.kordamp.ikonli.javafx.FontIcon mic = new org.kordamp.ikonli.javafx.FontIcon("fas-microphone");
            mic.setIconSize(20); mic.setIconColor(javafx.scene.paint.Color.web("#54656F"));
            boutonVocal.setGraphic(mic);
            if (voiceRecorder != null) {
                String b64 = voiceRecorder.stopRecording();
                if (b64 != null && contactActif != null) {
                    String chemin = null;
                    try {
                        byte[] wav = java.util.Base64.getDecoder().decode(b64);
                        java.io.File dir = new java.io.File("received_files");
                        if (!dir.exists()) dir.mkdirs();
                        java.io.File f = new java.io.File(dir, "voice_sent_" + System.currentTimeMillis() + ".wav");
                        java.nio.file.Files.write(f.toPath(), wav);
                        chemin = f.getAbsolutePath();
                    } catch (Exception ex) {}
                    Message m = new Message(MainApp.client.getNomUtilisateur(), contactActif, b64, TypeMessage.VOICE_MESSAGE);
                    MainApp.client.envoyer(m);
                    if (chemin != null) MessageDAO.sauvegarderVocal(MainApp.client.getNomUtilisateur(), contactActif, chemin);
                    ajouterBubbleVocaleAvecFichier(zoneMessages, "0:??", true, heure(), chemin);
                    afficherInfo("Message vocal envoyé ✓");
                }
                voiceRecorder = null;
            }
        }
    }

    private void ajouterBubbleVocale(VBox zone, String duree, boolean sortant, String heure) {
        ajouterBubbleVocaleAvecFichier(zone, duree, sortant, heure, null);
    }

    private String calculerDuree(String chemin) {
        if (chemin == null) return "0:00";
        try {
            java.io.File f = new java.io.File(chemin);
            if (!f.exists()) return "0:00";
            javax.sound.sampled.AudioInputStream ais = javax.sound.sampled.AudioSystem.getAudioInputStream(f);
            javax.sound.sampled.AudioFormat fmt = ais.getFormat();
            double secs = ais.getFrameLength() / fmt.getFrameRate();
            ais.close();
            return String.format("%d:%02d", (int)(secs/60), (int)(secs%60));
        } catch (Exception e) { return "0:00"; }
    }

    private void ajouterBubbleVocaleAvecFichier(VBox zone, String dureeIgnoree, boolean sortant, String heure, String cheminFichier) {
        final String chemin = cheminFichier;
        String duree = calculerDuree(chemin);
        Button playBtn = new Button("▶");
        playBtn.setStyle(
                "-fx-background-color:" + (sortant ? "#25D366" : "#B0BEC5") + ";" +
                        "-fx-text-fill:" + (sortant ? "white" : "#37474F") + "; -fx-background-radius:14px;" +
                        "-fx-min-width:28px; -fx-min-height:28px; -fx-cursor:hand; -fx-font-size:11px;"
        );
        playBtn.setOnAction(ev -> {
            if (chemin == null || !new java.io.File(chemin).exists()) { afficherInfo("Fichier audio introuvable."); return; }
            try {
                javax.sound.sampled.AudioInputStream as = javax.sound.sampled.AudioSystem.getAudioInputStream(new java.io.File(chemin));
                javax.sound.sampled.Clip clip = javax.sound.sampled.AudioSystem.getClip();
                clip.open(as); clip.start();
                playBtn.setText("⏸");
                clip.addLineListener(event -> { if (event.getType() == javax.sound.sampled.LineEvent.Type.STOP) Platform.runLater(() -> playBtn.setText("▶")); });
            } catch (Exception ex) { afficherInfo("Erreur lecture audio."); }
        });
        HBox wave = new HBox(2); wave.setAlignment(Pos.CENTER);
        for (int h : new int[]{6, 13, 9, 17, 8, 15, 11, 6, 10, 13}) {
            javafx.scene.shape.Rectangle bar = new javafx.scene.shape.Rectangle(3, h);
            bar.setArcWidth(2); bar.setArcHeight(2);
            bar.setFill(javafx.scene.paint.Color.web("#8696A0"));
            wave.getChildren().add(bar);
        }
        Label dur = new Label(duree);
        dur.setFont(Font.font("Arial", 10));
        dur.setStyle("-fx-text-fill:#8696A0;");
        HBox voiceRow = new HBox(8, playBtn, wave, dur);
        voiceRow.setAlignment(Pos.CENTER_LEFT);
        Label time = new Label(heure + (sortant ? "  ✓✓" : ""));
        time.setFont(Font.font("Arial", 10));
        time.setStyle("-fx-text-fill:#8696A0;");
        VBox bubble = new VBox(6, voiceRow, time);
        bubble.setPadding(new Insets(8, 12, 6, 12));
        bubble.setStyle(
                "-fx-background-color:" + (sortant ? BUBBLE_SENT : BUBBLE_RECEIVED) + ";" +
                        "-fx-background-radius:" + (sortant ? "14 14 4 14" : "14 14 14 4") + ";"
        );
        bubble.setMaxWidth(260);
        HBox row = new HBox(bubble);
        row.setAlignment(sortant ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        row.setPadding(new Insets(2, 16, 2, 16));
        zone.getChildren().add(row);
    }

    public void afficherMessageVocal(Message message) {
        Platform.runLater(() -> {
            String exp = message.getExpediteur();
            try {
                byte[] wav = java.util.Base64.getDecoder().decode(message.getContenu());
                java.io.File dir = new java.io.File("received_files");
                if (!dir.exists()) dir.mkdirs();
                java.io.File wf = new java.io.File(dir, "voice_" + exp + "_" + System.currentTimeMillis() + ".wav");
                java.nio.file.Files.write(wf.toPath(), wav);
                MessageDAO.sauvegarderVocal(exp, MainApp.client.getNomUtilisateur(), wf.getAbsolutePath());
                if (exp.equals(contactActif)) ajouterBubbleVocaleAvecFichier(zoneMessages, "0:??", false, heure(), wf.getAbsolutePath());
                else { messagesEnAttente.computeIfAbsent(exp, k -> new java.util.ArrayList<>()).add(message); rafraichirListeContacts(); }
            } catch (Exception e) { System.err.println("[Voice] Erreur réception : " + e.getMessage()); }
        });
    }

    private void ajouterBubble(VBox zone, String contenu, boolean sortant, String exp, String heure) {
        VBox bubbleContent = new VBox(2);
        if (!sortant && !exp.isEmpty()) {
            Label sender = new Label(exp);
            sender.setFont(Font.font("Arial", FontWeight.BOLD, 11));
            sender.setStyle("-fx-text-fill:" + SENDER_COLOR + "; -fx-font-size:11px;");
            bubbleContent.getChildren().add(sender);
        }
        Label text = new Label(contenu);
        text.setWrapText(true); text.setMaxWidth(440); text.setMinWidth(40);
        text.setFont(Font.font("Arial", 13));
        text.setStyle("-fx-text-fill:" + (sortant ? TEXT_SENT : TEXT_RECEIVED) + ";");
        bubbleContent.getChildren().add(text);
        HBox meta = new HBox(4); meta.setAlignment(Pos.CENTER_RIGHT); meta.setMaxWidth(Double.MAX_VALUE);
        if (!heure.isEmpty()) {
            Label time = new Label(heure); time.setFont(Font.font("Arial", 10));
            time.setStyle("-fx-text-fill:" + TEXT_META + ";");
            meta.getChildren().add(time);
        }
        if (sortant) { Label tick = new Label("✓✓"); tick.setFont(Font.font("Arial", 10)); tick.setStyle("-fx-text-fill:" + TEXT_META + ";"); meta.getChildren().add(tick); }
        bubbleContent.getChildren().add(meta);
        bubbleContent.setStyle(
                "-fx-background-color:" + (sortant ? BUBBLE_SENT : BUBBLE_RECEIVED) + ";" +
                        "-fx-background-radius:" + (sortant ? "14 14 4 14" : "14 14 14 4") + ";" +
                        "-fx-padding: 8 12 6 12;"
        );
        bubbleContent.setPadding(new Insets(0)); bubbleContent.setMaxWidth(460); bubbleContent.setMinWidth(60);
        HBox row = new HBox(bubbleContent);
        row.setAlignment(sortant ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        row.setPadding(new Insets(2, 16, 2, 16)); row.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(row, Priority.ALWAYS);
        javafx.animation.FadeTransition ft = new javafx.animation.FadeTransition(javafx.util.Duration.millis(120), row);
        ft.setFromValue(0.0); ft.setToValue(1.0);
        zone.getChildren().add(row); ft.play();
    }

    private void ajouterBubbleFichier(VBox zone, String nom, boolean sortant, String heure) {
        Label icon = new Label("📄"); icon.setFont(Font.font("Arial", 20));
        Label fileName = new Label(nom); fileName.setFont(Font.font("Arial", FontWeight.BOLD, 12));
        fileName.setStyle("-fx-text-fill:" + (sortant ? TEXT_SENT : TEXT_RECEIVED) + ";");
        fileName.setWrapText(true); fileName.setMaxWidth(240);
        Label fileSub = new Label("Fichier · Cliquer pour ouvrir"); fileSub.setFont(Font.font("Arial", 10));
        fileSub.setStyle("-fx-text-fill:" + TEXT_META + ";");
        VBox fileInfo = new VBox(2, fileName, fileSub);
        HBox fileRow  = new HBox(10, icon, fileInfo); fileRow.setAlignment(Pos.CENTER_LEFT);
        Label time = new Label(heure + (sortant ? "  ✓✓" : "")); time.setFont(Font.font("Arial", 10));
        time.setStyle("-fx-text-fill:" + TEXT_META + ";");
        VBox bubble = new VBox(6, fileRow, time);
        bubble.setPadding(new Insets(8, 12, 6, 12));
        bubble.setStyle(
                "-fx-background-color:" + (sortant ? BUBBLE_SENT : FILE_RECV_BG) + ";" +
                        "-fx-background-radius:" + (sortant ? "14px 14px 4px 14px" : "14px 14px 14px 4px") + ";" +
                        "-fx-cursor:hand;"
        );
        bubble.setMaxWidth(320);
        if (!sortant) bubble.setOnMouseClicked(e -> ouvrirFichierRecu(nom));
        HBox row = new HBox(bubble);
        row.setAlignment(sortant ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        row.setPadding(new Insets(2, 16, 2, 16));
        zone.getChildren().add(row);
    }

    private void ouvrirFenetreVideoCall(String interlocuteur) {
        javax.swing.SwingUtilities.invokeLater(() -> {
            try { VideoCallWindow w = new VideoCallWindow(MainApp.client, interlocuteur); MainApp.client.setFenetreAppelActive(w); }
            catch (Exception ex) { System.err.println("[Appel] ÉCHEC vidéo : " + ex.getMessage()); }
        });
    }

    private void ouvrirFenetreAudioCall(String interlocuteur) {
        javax.swing.SwingUtilities.invokeLater(() -> {
            try { AudioCallWindow w = new AudioCallWindow(MainApp.client, interlocuteur); MainApp.client.setFenetreAudioActive(w); }
            catch (Exception ex) { System.err.println("[Appel] ÉCHEC audio : " + ex.getMessage()); }
        });
    }

    private Label creerAvatar(String nom) {
        Label avatar = new Label(initiales(nom));
        avatar.setMinSize(42, 42); avatar.setMaxSize(42, 42);
        avatar.setAlignment(Pos.CENTER);
        avatar.setFont(Font.font("Arial", FontWeight.BOLD, 13));
        avatar.setTextFill(Color.WHITE);
        avatar.setStyle("-fx-background-color:" + couleur(nom) + "; -fx-background-radius:21px;");
        return avatar;
    }

    private void sauvegarderFichierRecu(String nom, String b64) {
        try {
            byte[] octets = java.util.Base64.getDecoder().decode(b64);
            File dossier = new File("received_files");
            if (!dossier.exists()) dossier.mkdirs();
            Files.write(new File(dossier, nom).toPath(), octets);
        } catch (Exception e) {}
    }

    private void ouvrirFichierRecu(String nom) {
        try {
            File f = new File("received_files/" + nom);
            if (f.exists()) java.awt.Desktop.getDesktop().open(f);
            else afficherInfo("Fichier introuvable : " + nom);
        } catch (Exception e) { afficherInfo("Impossible d'ouvrir : " + e.getMessage()); }
    }

    private void afficherInfo(String message) {
        Platform.runLater(() -> {
            if (labelInfo == null) { System.out.println("[Info] " + message); return; }
            labelInfo.setText(message); labelInfo.setVisible(true); labelInfo.setManaged(true);
            javafx.animation.PauseTransition pause = new javafx.animation.PauseTransition(javafx.util.Duration.seconds(3));
            pause.setOnFinished(e -> { labelInfo.setVisible(false); labelInfo.setManaged(false); });
            pause.play();
        });
    }

    private String heure() { return LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")); }
    private String initiales(String nom) {
        String[] p = nom.trim().split("\\s+");
        if (p.length == 1) return p[0].substring(0, Math.min(2, p[0].length())).toUpperCase();
        return ("" + p[0].charAt(0) + p[1].charAt(0)).toUpperCase();
    }
    private String couleur(String nom) { return COULEURS[Math.abs(nom.hashCode()) % COULEURS.length]; }


    private void ajouterBubbleFichierGroupe(VBox zone, String nom, boolean sortant, String heure, String expediteur) {
        VBox bubble = new VBox(4);
        if (!sortant && expediteur != null && !expediteur.isEmpty()) {
            Label sender = new Label(expediteur);
            sender.setFont(Font.font("Arial", FontWeight.BOLD, 11));
            sender.setStyle("-fx-text-fill:" + SENDER_COLOR + "; -fx-font-size:11px;");
            bubble.getChildren().add(sender);
        }
        Label icon = new Label("📄"); icon.setFont(Font.font("Arial", 20));
        Label fileName = new Label(nom); fileName.setFont(Font.font("Arial", FontWeight.BOLD, 12));
        fileName.setStyle("-fx-text-fill:" + (sortant ? TEXT_SENT : TEXT_RECEIVED) + ";");
        fileName.setWrapText(true); fileName.setMaxWidth(240);
        Label fileSub = new Label("Fichier · Cliquer pour ouvrir"); fileSub.setFont(Font.font("Arial", 10));
        fileSub.setStyle("-fx-text-fill:" + TEXT_META + ";");
        VBox fileInfo = new VBox(2, fileName, fileSub);
        HBox fileRow = new HBox(10, icon, fileInfo); fileRow.setAlignment(Pos.CENTER_LEFT);
        Label time = new Label(heure + (sortant ? "  ✓✓" : "")); time.setFont(Font.font("Arial", 10));
        time.setStyle("-fx-text-fill:" + TEXT_META + ";");
        bubble.getChildren().addAll(fileRow, time);
        bubble.setPadding(new Insets(8, 12, 6, 12));
        bubble.setStyle("-fx-background-color:" + (sortant ? BUBBLE_SENT : FILE_RECV_BG) + ";" +
                "-fx-background-radius:" + (sortant ? "14px 14px 4px 14px" : "14px 14px 14px 4px") + "; -fx-cursor:hand;");
        bubble.setMaxWidth(320);
        if (!sortant) bubble.setOnMouseClicked(e -> ouvrirFichierRecu(nom));
        HBox row = new HBox(bubble);
        row.setAlignment(sortant ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        row.setPadding(new Insets(2, 16, 2, 16));
        zone.getChildren().add(row);
    }


    private void ajouterBubbleVocaleGroupe(VBox zone, boolean sortant, String heure, String cheminFichier, String expediteur) {
        final String chemin = cheminFichier;
        String duree = calculerDuree(chemin);
        VBox bubble = new VBox(6);
        if (!sortant && expediteur != null && !expediteur.isEmpty()) {
            Label sender = new Label(expediteur);
            sender.setFont(Font.font("Arial", FontWeight.BOLD, 11));
            sender.setStyle("-fx-text-fill:" + SENDER_COLOR + "; -fx-font-size:11px;");
            bubble.getChildren().add(sender);
        }
        Button playBtn = new Button("▶");
        playBtn.setStyle("-fx-background-color:" + (sortant ? "#25D366" : "#B0BEC5") + ";" +
                "-fx-text-fill:" + (sortant ? "white" : "#37474F") + "; -fx-background-radius:14px;" +
                "-fx-min-width:28px; -fx-min-height:28px; -fx-cursor:hand; -fx-font-size:11px;");
        playBtn.setOnAction(ev -> {
            if (chemin == null || !new java.io.File(chemin).exists()) { afficherInfo("Fichier audio introuvable."); return; }
            try {
                javax.sound.sampled.AudioInputStream as = javax.sound.sampled.AudioSystem.getAudioInputStream(new java.io.File(chemin));
                javax.sound.sampled.Clip clip = javax.sound.sampled.AudioSystem.getClip();
                clip.open(as); clip.start();
                playBtn.setText("⏸");
                clip.addLineListener(event -> { if (event.getType() == javax.sound.sampled.LineEvent.Type.STOP) Platform.runLater(() -> playBtn.setText("▶")); });
            } catch (Exception ex) { afficherInfo("Erreur lecture audio."); }
        });
        HBox wave = new HBox(2); wave.setAlignment(Pos.CENTER);
        for (int h : new int[]{6, 13, 9, 17, 8, 15, 11, 6, 10, 13}) {
            javafx.scene.shape.Rectangle bar = new javafx.scene.shape.Rectangle(3, h);
            bar.setArcWidth(2); bar.setArcHeight(2);
            bar.setFill(javafx.scene.paint.Color.web("#8696A0"));
            wave.getChildren().add(bar);
        }
        Label dur = new Label(duree); dur.setFont(Font.font("Arial", 10));
        dur.setStyle("-fx-text-fill:#8696A0;");
        HBox voiceRow = new HBox(8, playBtn, wave, dur); voiceRow.setAlignment(Pos.CENTER_LEFT);
        Label time = new Label(heure + (sortant ? "  ✓✓" : "")); time.setFont(Font.font("Arial", 10));
        time.setStyle("-fx-text-fill:#8696A0;");
        bubble.getChildren().addAll(voiceRow, time);
        bubble.setPadding(new Insets(8, 12, 6, 12));
        bubble.setStyle("-fx-background-color:" + (sortant ? BUBBLE_SENT : BUBBLE_RECEIVED) + ";" +
                "-fx-background-radius:" + (sortant ? "14 14 4 14" : "14 14 14 4") + ";");
        bubble.setMaxWidth(280);
        HBox row = new HBox(bubble);
        row.setAlignment(sortant ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        row.setPadding(new Insets(2, 16, 2, 16));
        zone.getChildren().add(row);
    }

}