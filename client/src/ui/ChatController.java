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

/**
 * Contrôleur principal du chat (V2).
 *
 * Nouveautés :
 *  - Correction bug "contact existe déjà" : vérification + proposition d'utilisateurs en ligne
 *  - Bouton appel audio seul
 *  - Onglet Groupes : créer, ajouter membre, supprimer membre, chat de groupe
 *  - Bouton réunion de groupe
 */
public class ChatController {

    // ── FXML Sidebar ───────────────────────────────────────────
    @FXML private Label      labelNomUtilisateur;
    @FXML private VBox       listeContacts;
    @FXML private TabPane    tabPane;

    // ── FXML Chat privé ────────────────────────────────────────
    @FXML private VBox       zoneMessages;
    @FXML private ScrollPane scrollMessages;
    @FXML private TextField  champMessage;
    @FXML private Button     boutonEnvoyer;
    @FXML private Button     boutonFichier;
    @FXML private Button     boutonAppelVideo;
    @FXML private Button     boutonAppelAudio;
    @FXML private Label      labelContactActif;
    @FXML private Label      labelStatutContact;

    // ── FXML Groupes ───────────────────────────────────────────
    @FXML private VBox       listeGroupes;
    @FXML private VBox       zoneMessagesGroupe;
    @FXML private ScrollPane scrollMessagesGroupe;
    @FXML private TextField  champMessageGroupe;
    @FXML private Label      labelGroupeActif;
    @FXML private Label      labelMembresGroupe;

    // ── État ───────────────────────────────────────────────────
    private String contactActif = null;
    private String groupeActif  = null;

    private final Set<String>              utilisateursEnLigne   = new HashSet<>();
    private final Map<String, List<Message>> messagesEnAttente   = new HashMap<>();
    private final Map<String, List<Message>> messagesGrpAttente  = new HashMap<>();

    private static final String[] COULEURS = {
            "#3C3489", "#712B13", "#0F6E56", "#0C447C",
            "#633806", "#1D6B3A", "#7B2D8B", "#8B4513"
    };

    // ── Initialisation ─────────────────────────────────────────

    @FXML
    public void initialize() {
        try {
            String nom = MainApp.client.getNomUtilisateur();
            labelNomUtilisateur.setText(nom != null ? nom : "?");

            champMessage.setOnAction(e -> envoyerMessage());
            if (champMessageGroupe != null)
                champMessageGroupe.setOnAction(e -> envoyerMessageGroupe());

            zoneMessages.heightProperty().addListener((obs, o, n) ->
                    scrollMessages.setVvalue(1.0));
            if (scrollMessagesGroupe != null && zoneMessagesGroupe != null)
                zoneMessagesGroupe.heightProperty().addListener((obs, o, n) ->
                        scrollMessagesGroupe.setVvalue(1.0));

            rafraichirListeContacts();
            // ✅ CORRECTION : try/catch pour ne pas crasher si MySQL absent
            try {
                rafraichirGroupes();
            } catch (Exception e) {
                System.err.println("[ChatController.initialize] Groupes non chargés : " + e.getMessage());
            }
            MainApp.client.demanderListeUtilisateurs();

        } catch (Exception e) {
            System.err.println("[ChatController.initialize] Erreur : " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ── Actions chat privé ─────────────────────────────────────

    @FXML
    public void envoyerMessage() {
        if (contactActif == null) return;
        String texte = champMessage.getText().trim();
        if (texte.isEmpty()) return;

        String heure = heure();
        ajouterBubble(zoneMessages, texte, true, MainApp.client.getNomUtilisateur(), heure);
        champMessage.clear();
        MainApp.client.envoyerMessage(contactActif, texte);
    }

    @FXML
    public void envoyerFichier() {
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
            ajouterBubbleFichier(zoneMessages, f.getName(), true, heure());
        } catch (IOException e) {
            afficherInfo("Erreur lecture fichier : " + e.getMessage());
        }
    }

    @FXML
    public void lancerAppelVideo() {
        if (contactActif == null) { afficherInfo("Sélectionnez d'abord un contact."); return; }
        if (!utilisateursEnLigne.contains(contactActif)) { afficherInfo(contactActif + " n'est pas en ligne."); return; }
        MainApp.client.demanderAppel(contactActif);
        afficherInfo("Appel vidéo envoyé à " + contactActif + "...");
    }

    @FXML
    public void lancerAppelAudio() {
        if (contactActif == null) { afficherInfo("Sélectionnez d'abord un contact."); return; }
        if (!utilisateursEnLigne.contains(contactActif)) { afficherInfo(contactActif + " n'est pas en ligne."); return; }
        MainApp.client.demanderAppelAudio(contactActif);
        afficherInfo("Appel audio envoyé à " + contactActif + "...");
    }

    @FXML
    public void seDeconnecter() {
        MainApp.client.deconnecter();
        MainApp.changerScene("login.fxml");
    }

    // ── Ajout / Suppression de contacts ───────────────────────

    @FXML
    public void ajouterContact() {
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
                if (ContactDAO.ajouter(nom))
                    rafraichirListeContacts();
                else
                    afficherInfo("Ce contact existe déjà dans votre liste.");
            });

            Alert lien = new Alert(Alert.AlertType.CONFIRMATION);
            lien.setTitle("Ou");
            lien.setHeaderText(null);
            lien.setContentText("Voulez-vous ajouter un contact par son nom ?");
            lien.getButtonTypes().setAll(
                    new ButtonType("Oui, saisir un nom", ButtonBar.ButtonData.YES),
                    new ButtonType("Non", ButtonBar.ButtonData.CANCEL_CLOSE));
            lien.showAndWait().ifPresent(rep -> {
                if (rep.getButtonData() == ButtonBar.ButtonData.YES)
                    ajouterContactParNom();
            });
        } else {
            ajouterContactParNom();
        }
    }

    private void ajouterContactParNom() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Ajouter un contact");
        dialog.setHeaderText("Entrez le nom d'utilisateur :");
        dialog.setContentText("Nom :");
        dialog.showAndWait().ifPresent(nom -> {
            nom = nom.trim();
            if (nom.isEmpty()) return;
            if (nom.equals(MainApp.client.getNomUtilisateur())) {
                afficherInfo("Vous ne pouvez pas vous ajouter vous-même.");
                return;
            }
            if (ContactDAO.existe(nom)) {
                afficherInfo("\"" + nom + "\" est déjà dans vos contacts.");
                return;
            }
            ContactDAO.ajouter(nom);
            rafraichirListeContacts();
            afficherInfo("Contact \"" + nom + "\" ajouté. Il apparaîtra en ligne dès qu'il se connecte.");
        });
    }

    // ── Actions groupes ────────────────────────────────────────

    @FXML
    public void creerGroupe() {
        TextInputDialog d = new TextInputDialog();
        d.setTitle("Créer un groupe");
        d.setHeaderText("Nom du nouveau groupe :");
        d.setContentText("Nom :");
        d.showAndWait().ifPresent(nom -> {
            nom = nom.trim();
            if (nom.isEmpty()) return;

            // ✅ CORRECTION : vérifier que MySQL est prêt avant toute opération
            if (database.LocalDatabase.getUtilisateurCourant() == null) {
                afficherInfo("⚠ La base de données n'est pas encore prête.\n"
                        + "Patientez un instant après la connexion puis réessayez.");
                return;
            }

            final String nomFinal = nom;
            System.out.println("[Groupe] Création : " + nomFinal);

            // 1. Envoyer GROUP_CREATE au serveur → il répondra GROUP_INFO
            MainApp.client.creerGroupe(nomFinal);

            // 2. ✅ CORRECTION : ArrayList mutable (List.of est immutable et plantait)
            List<String> membres = new ArrayList<>();
            membres.add(MainApp.client.getNomUtilisateur());
            try {
                GroupDAO.sauvegarderOuMettreAJour(nomFinal, membres);
                System.out.println("[Groupe] Sauvegarde locale réussie : " + nomFinal);
            } catch (Exception e) {
                System.err.println("[Groupe] Sauvegarde optimiste échouée (GROUP_INFO la fera) : "
                        + e.getMessage());
            }

            // 3. Rafraîchir l'UI
            rafraichirGroupes();

            // 4. Basculer sur l'onglet Groupes
            if (tabPane != null && tabPane.getTabs().size() >= 2) {
                tabPane.getSelectionModel().select(1);
            }

            // 5. ✅ CORRECTION : pas de vérification MySQL bloquante supplémentaire
            afficherInfo("Groupe \"" + nomFinal + "\" en cours de création...\n"
                    + "Il apparaîtra dans la liste dans un instant.\n"
                    + "Clic droit dessus pour ajouter des membres.");
        });
    }

    @FXML
    public void envoyerMessageGroupe() {
        if (groupeActif == null) return;
        String texte = champMessageGroupe.getText().trim();
        if (texte.isEmpty()) return;

        String heure = heure();
        ajouterBubble(zoneMessagesGroupe, texte, true, MainApp.client.getNomUtilisateur(), heure);
        champMessageGroupe.clear();
        MainApp.client.envoyerMessageGroupe(groupeActif, texte);
    }

    @FXML
    public void lancerReunionGroupe() {
        if (groupeActif == null) { afficherInfo("Sélectionnez d'abord un groupe."); return; }

        // Ouvrir la fenêtre SYNCHRONIQUEMENT avant d'envoyer GROUP_CALL_REQUEST
        if (MainApp.client.getFenetreReunionActive() == null) {
            final String groupe = groupeActif;
            try {
                javax.swing.SwingUtilities.invokeAndWait(() -> {
                    try {
                        GroupAudioCallWindow w = new GroupAudioCallWindow(MainApp.client, groupe);
                        MainApp.client.setFenetreReunionActive(w);
                        System.out.println("[Réunion] Fenêtre ouverte pour l'initiateur : " + groupe);
                    } catch (Exception ex) {
                        System.err.println("[Réunion] Erreur ouverture fenêtre initiateur : " + ex.getMessage());
                    }
                });
            } catch (Exception ex) {
                System.err.println("[Réunion] invokeAndWait erreur : " + ex.getMessage());
            }
        }

        MainApp.client.demarrerReunionGroupe(groupeActif);
        afficherInfo("Réunion lancée pour le groupe \"" + groupeActif + "\"");
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
            choix.showAndWait().ifPresent(membre -> {
                MainApp.client.ajouterMembreGroupe(nomGroupe, membre);
            });
        } else {
            TextInputDialog d = new TextInputDialog();
            d.setTitle("Ajouter un membre");
            d.setHeaderText("Entrez le nom d'utilisateur :");
            d.setContentText("Nom :");
            d.showAndWait().ifPresent(m -> {
                m = m.trim();
                if (!m.isEmpty()) MainApp.client.ajouterMembreGroupe(nomGroupe, m);
            });
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
        choix.showAndWait().ifPresent(m ->
                MainApp.client.retirerMembreGroupe(nomGroupe, m));
    }

    // ── Méthodes appelées par Client ───────────────────────────

    public void afficherMessage(Message message) {
        Platform.runLater(() -> {
            String exp = message.getExpediteur();
            if (!ContactDAO.existe(exp) && !exp.equals(MainApp.client.getNomUtilisateur())) {
                ContactDAO.ajouter(exp);
                rafraichirListeContacts();
            }
            String heure = heure();
            if (exp.equals(contactActif)) {
                ajouterBubble(zoneMessages, message.getContenu(), false, exp, heure);
            } else {
                messagesEnAttente.computeIfAbsent(exp, k -> new ArrayList<>()).add(message);
                rafraichirListeContacts();
            }
        });
    }

    public void afficherFichier(Message message) {
        Platform.runLater(() -> {
            String exp = message.getExpediteur();
            String[] parties = message.getContenu().split("\\|\\|", 2);
            String nomFichier = parties.length > 0 ? parties[0] : "fichier";
            if (parties.length == 2) sauvegarderFichierRecu(nomFichier, parties[1]);
            String heure = heure();
            if (exp.equals(contactActif)) {
                ajouterBubbleFichier(zoneMessages, nomFichier, false, heure);
            } else {
                messagesEnAttente.computeIfAbsent(exp, k -> new ArrayList<>()).add(message);
                rafraichirListeContacts();
            }
        });
    }

    public void afficherMessageGroupe(Message message) {
        Platform.runLater(() -> {
            String groupe = message.getDestinataire();
            String exp    = message.getExpediteur();
            String heure  = heure();

            // ✅ CORRECTION : mettre à jour le label membres en temps réel
            if (groupe.equals(groupeActif) && labelMembresGroupe != null) {
                List<String> membres = GroupDAO.getMembres(groupe);
                labelMembresGroupe.setText("Membres : " + String.join(", ", membres));
            }

            if (groupe.equals(groupeActif) && zoneMessagesGroupe != null) {
                ajouterBubble(zoneMessagesGroupe, message.getContenu(), false, exp, heure);
            } else {
                messagesGrpAttente.computeIfAbsent(groupe, k -> new ArrayList<>()).add(message);
                rafraichirGroupes();
            }
        });
    }

    public void mettreAJourListeUtilisateurs(String contenu) {
        Platform.runLater(() -> {
            utilisateursEnLigne.clear();
            if (contenu != null && !contenu.isBlank()) {
                for (String n : contenu.split(",")) {
                    String t = n.trim();
                    if (!t.isEmpty()) utilisateursEnLigne.add(t);
                }
            }
            rafraichirListeContacts();
            if (contactActif != null) actualiserStatutContact();
        });
    }

    // ✅ CORRECTION : try/catch pour ne pas crasher l'UI si MySQL absent
    public void rafraichirGroupes() {
        Platform.runLater(() -> {
            try {
                construireListeGroupes();
            } catch (Exception e) {
                System.err.println("[ChatController] rafraichirGroupes erreur : " + e.getMessage());
            }
        });
    }

    // Appel VIDÉO
    public void afficherDemandeAppel(Message message) {
        Platform.runLater(() -> {
            String appelant = message.getExpediteur();
            Alert a = new Alert(Alert.AlertType.CONFIRMATION);
            a.setTitle("Appel vidéo entrant");
            a.setHeaderText(appelant + " vous appelle en vidéo");
            a.setContentText("Accepter ?");
            ButtonType ok  = new ButtonType("Accepter", ButtonBar.ButtonData.YES);
            ButtonType non = new ButtonType("Refuser",  ButtonBar.ButtonData.NO);
            a.getButtonTypes().setAll(ok, non);
            a.showAndWait().ifPresent(rep -> {
                if (rep == ok) {
                    MainApp.client.accepterAppel(appelant);
                    ouvrirFenetreVideoCall(appelant);
                } else {
                    MainApp.client.refuserAppel(appelant);
                }
            });
        });
    }

    public void gererAcceptationAppel(Message message) {
        Platform.runLater(() -> ouvrirFenetreVideoCall(message.getExpediteur()));
    }

    public void gererRefusAppel(Message message) {
        Platform.runLater(() -> afficherInfo(message.getExpediteur() + " a refusé votre appel vidéo."));
    }

    // Appel AUDIO seul
    public void afficherDemandeAppelAudio(Message message) {
        Platform.runLater(() -> {
            String appelant = message.getExpediteur();
            Alert a = new Alert(Alert.AlertType.CONFIRMATION);
            a.setTitle("Appel audio entrant");
            a.setHeaderText(appelant + " vous appelle (audio)");
            a.setContentText("Accepter ?");
            ButtonType ok  = new ButtonType("Accepter", ButtonBar.ButtonData.YES);
            ButtonType non = new ButtonType("Refuser",  ButtonBar.ButtonData.NO);
            a.getButtonTypes().setAll(ok, non);
            a.showAndWait().ifPresent(rep -> {
                if (rep == ok) {
                    MainApp.client.accepterAppelAudio(appelant);
                    ouvrirFenetreAudioCall(appelant);
                } else {
                    MainApp.client.refuserAppelAudio(appelant);
                }
            });
        });
    }

    public void gererAcceptationAppelAudio(Message message) {
        Platform.runLater(() -> ouvrirFenetreAudioCall(message.getExpediteur()));
    }

    public void gererRefusAppelAudio(Message message) {
        Platform.runLater(() -> afficherInfo(message.getExpediteur() + " a refusé votre appel audio."));
    }

    // Réunion de groupe
    public void afficherDemandeReunion(Message message) {
        Platform.runLater(() -> {
            String groupe    = message.getDestinataire();
            String initiateur = message.getExpediteur();
            Alert a = new Alert(Alert.AlertType.CONFIRMATION);
            a.setTitle("Réunion de groupe");
            a.setHeaderText(initiateur + " démarre une réunion dans \"" + groupe + "\"");
            a.setContentText("Voulez-vous rejoindre ?");
            ButtonType ok  = new ButtonType("Rejoindre", ButtonBar.ButtonData.YES);
            ButtonType non = new ButtonType("Ignorer",   ButtonBar.ButtonData.NO);
            a.getButtonTypes().setAll(ok, non);
            a.showAndWait().ifPresent(rep -> {
                if (rep == ok) MainApp.client.rejoindreReunionGroupe(groupe);
            });
        });
    }

    public void membreARejoindreReunion(Message message) {
        Platform.runLater(() ->
                afficherInfo(message.getExpediteur() + " a rejoint la réunion."));
    }

    public void membreAQuitteReunion(Message message) {
        Platform.runLater(() ->
                afficherInfo(message.getExpediteur() + " a quitté la réunion."));
    }

    // ── Construction UI ────────────────────────────────────────

    private void rafraichirListeContacts() {
        listeContacts.getChildren().clear();

        Button btnAjouter = new Button("➕ Ajouter un contact");
        btnAjouter.setMaxWidth(Double.MAX_VALUE);
        btnAjouter.setStyle("-fx-background-color:#202C33; -fx-text-fill:#25D366; " +
                "-fx-font-size:12px; -fx-padding:10px;");
        btnAjouter.setOnAction(e -> ajouterContact());
        listeContacts.getChildren().add(btnAjouter);

        List<String> contacts = ContactDAO.lister();
        for (String enLigne : utilisateursEnLigne) {
            if (!enLigne.equals(MainApp.client.getNomUtilisateur()) && !contacts.contains(enLigne))
                contacts.add(enLigne);
        }
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
        btnCreer.setStyle("-fx-background-color:#202C33; -fx-text-fill:#25D366; " +
                "-fx-font-size:12px; -fx-padding:10px;");
        btnCreer.setOnAction(e -> creerGroupe());
        listeGroupes.getChildren().add(btnCreer);

        List<String[]> groupesLocaux = GroupDAO.listerGroupes();
        for (String[] g : groupesLocaux) {
            String nom     = g[0];
            String membres = g[1];
            listeGroupes.getChildren().add(creerLigneGroupe(nom, membres));
        }
    }

    private HBox creerLigneContact(String nom) {
        boolean enLigne = utilisateursEnLigne.contains(nom);
        boolean dansContacts = ContactDAO.existe(nom);

        Label avatar = creerAvatar(nom);

        Label labelNom = new Label(nom);
        labelNom.setFont(Font.font("Arial", FontWeight.BOLD, 13));
        labelNom.setStyle("-fx-text-fill:#E9EDEF;");

        Label statut = new Label(enLigne ? "● En ligne" : "○ Hors ligne");
        statut.setFont(Font.font("Arial", 11));
        statut.setStyle("-fx-text-fill:" + (enLigne ? "#25D366" : "#8696A0") + ";");

        VBox info = new VBox(2, labelNom, statut);

        int n = messagesEnAttente.containsKey(nom) ? messagesEnAttente.get(nom).size() : 0;
        Label badge = new Label(n > 0 ? String.valueOf(n) : "");
        badge.setVisible(n > 0);
        badge.setMinSize(20, 20);
        badge.setAlignment(Pos.CENTER);
        badge.setStyle("-fx-background-color:#25D366; -fx-background-radius:10px; " +
                "-fx-text-fill:white; -fx-font-size:10px; -fx-font-weight:bold;");

        HBox ligne = new HBox(10, avatar, info, new Pane(), badge);
        HBox.setHgrow(info, Priority.ALWAYS);
        ligne.setAlignment(Pos.CENTER_LEFT);
        ligne.setPadding(new Insets(10, 12, 10, 12));
        ligne.setStyle("-fx-cursor:hand;");
        ligne.setOnMouseEntered(e -> ligne.setStyle("-fx-background-color:#202C33; -fx-cursor:hand;"));
        ligne.setOnMouseExited(e  -> ligne.setStyle("-fx-background-color:transparent; -fx-cursor:hand;"));
        ligne.setOnMouseClicked(e -> {
            if (e.getButton() == javafx.scene.input.MouseButton.SECONDARY && dansContacts) {
                ContextMenu menu = new ContextMenu();
                MenuItem suppr = new MenuItem("Supprimer ce contact");
                suppr.setOnAction(ev -> {
                    ContactDAO.supprimer(nom);
                    if (nom.equals(contactActif)) contactActif = null;
                    rafraichirListeContacts();
                });
                menu.getItems().add(suppr);
                menu.show(ligne, e.getScreenX(), e.getScreenY());
            } else {
                ouvrirContact(nom);
            }
        });
        return ligne;
    }

    private HBox creerLigneGroupe(String nom, String membres) {
        Label avatar = new Label("G");
        avatar.setMinSize(42, 42);
        avatar.setMaxSize(42, 42);
        avatar.setAlignment(Pos.CENTER);
        avatar.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        avatar.setTextFill(Color.WHITE);
        avatar.setStyle("-fx-background-color:#7B2D8B; -fx-background-radius:21px;");

        Label labelNom = new Label(nom);
        labelNom.setFont(Font.font("Arial", FontWeight.BOLD, 13));
        labelNom.setStyle("-fx-text-fill:#E9EDEF;");

        int nbMembres = membres.isEmpty() ? 0 : membres.split(",").length;
        Label info2 = new Label(nbMembres + " membre(s)");
        info2.setFont(Font.font("Arial", 11));
        info2.setStyle("-fx-text-fill:#8696A0;");

        VBox info = new VBox(2, labelNom, info2);

        int n = messagesGrpAttente.containsKey(nom) ? messagesGrpAttente.get(nom).size() : 0;
        Label badge = new Label(n > 0 ? String.valueOf(n) : "");
        badge.setVisible(n > 0);
        badge.setMinSize(20, 20);
        badge.setAlignment(Pos.CENTER);
        badge.setStyle("-fx-background-color:#25D366; -fx-background-radius:10px; " +
                "-fx-text-fill:white; -fx-font-size:10px; -fx-font-weight:bold;");

        HBox ligne = new HBox(10, avatar, info, new Pane(), badge);
        HBox.setHgrow(info, Priority.ALWAYS);
        ligne.setAlignment(Pos.CENTER_LEFT);
        ligne.setPadding(new Insets(10, 12, 10, 12));
        ligne.setStyle("-fx-cursor:hand;");
        ligne.setOnMouseEntered(e -> ligne.setStyle("-fx-background-color:#202C33; -fx-cursor:hand;"));
        ligne.setOnMouseExited(e  -> ligne.setStyle("-fx-background-color:transparent; -fx-cursor:hand;"));

        ligne.setOnMouseClicked(e -> {
            if (e.getButton() == javafx.scene.input.MouseButton.SECONDARY) {
                ContextMenu menu = new ContextMenu();
                MenuItem addM = new MenuItem("➕ Ajouter un membre");
                addM.setOnAction(ev -> ajouterMembreAuGroupe(nom));
                MenuItem rmM = new MenuItem("➖ Retirer un membre");
                rmM.setOnAction(ev -> retirerMembreDuGroupe(nom));
                MenuItem quitter = new MenuItem("🚪 Quitter le groupe");
                quitter.setOnAction(ev -> {
                    MainApp.client.retirerMembreGroupe(nom, MainApp.client.getNomUtilisateur());
                    GroupDAO.supprimer(nom);
                    if (nom.equals(groupeActif)) groupeActif = null;
                    rafraichirGroupes();
                });
                menu.getItems().addAll(addM, rmM, new SeparatorMenuItem(), quitter);
                menu.show(ligne, e.getScreenX(), e.getScreenY());
            } else {
                ouvrirGroupe(nom);
            }
        });
        return ligne;
    }

    // ✅ CORRECTION PRINCIPALE : ouvrirContact remet groupeActif=null
    //    et bascule correctement les zones visible/managed
    private void ouvrirContact(String nom) {
        contactActif = nom;
        groupeActif  = null;

        // Afficher les labels du chat privé
        labelContactActif.setText(nom);
        actualiserStatutContact();

        // Masquer les labels groupe
        if (labelGroupeActif != null)  { labelGroupeActif.setVisible(false);  labelGroupeActif.setManaged(false); }
        if (labelMembresGroupe != null) { labelMembresGroupe.setVisible(false); labelMembresGroupe.setManaged(false); }

        // Afficher zone privée + sa barre de saisie
        scrollMessages.setVisible(true);
        scrollMessages.setManaged(true);
        javafx.scene.Node barrePrivee = champMessage.getParent();
        if (barrePrivee != null) { barrePrivee.setVisible(true); barrePrivee.setManaged(true); }

        // Cacher zone groupe + sa barre de saisie
        if (scrollMessagesGroupe != null) {
            scrollMessagesGroupe.setVisible(false);
            scrollMessagesGroupe.setManaged(false);
        }
        javafx.scene.Node barreGroupe = champMessageGroupe != null ? champMessageGroupe.getParent() : null;
        if (barreGroupe != null) { barreGroupe.setVisible(false); barreGroupe.setManaged(false); }

        // Charger les messages
        zoneMessages.getChildren().clear();
        List<Message> historique = MessageDAO.historiqueAvec(nom);
        for (Message m : historique) {
            boolean sortant = m.getExpediteur().equals(MainApp.client.getNomUtilisateur());
            ajouterBubble(zoneMessages, m.getContenu(), sortant, m.getExpediteur(), "");
        }
        List<Message> attente = messagesEnAttente.remove(nom);
        if (attente != null) {
            for (Message m : attente) {
                String h = heure();
                if (m.getType() == TypeMessage.FILE) {
                    String[] p = m.getContenu().split("\\|\\|", 2);
                    ajouterBubbleFichier(zoneMessages, p.length > 0 ? p[0] : "fichier", false, h);
                } else {
                    ajouterBubble(zoneMessages, m.getContenu(), false, m.getExpediteur(), h);
                }
            }
        }
        rafraichirListeContacts();
    }

    // ✅ CORRECTION PRINCIPALE : ouvrirGroupe rend visibles scrollMessagesGroupe,
    //    la barre de saisie groupe, et les labels — tout était caché par managed=false
    private void ouvrirGroupe(String nom) {
        groupeActif  = nom;
        contactActif = null;

        // Afficher les labels groupe
        if (labelGroupeActif != null) {
            labelGroupeActif.setText("Groupe : " + nom);
            labelGroupeActif.setVisible(true);
            labelGroupeActif.setManaged(true);
        }
        List<String> membres = GroupDAO.getMembres(nom);
        if (labelMembresGroupe != null) {
            labelMembresGroupe.setText("Membres : " + String.join(", ", membres));
            labelMembresGroupe.setVisible(true);
            labelMembresGroupe.setManaged(true);
        }
        // Masquer les labels du chat privé
        if (labelContactActif  != null) labelContactActif.setText("");
        if (labelStatutContact != null) labelStatutContact.setText("");

        // Cacher zone privée + sa barre de saisie
        scrollMessages.setVisible(false);
        scrollMessages.setManaged(false);
        javafx.scene.Node barrePrivee = champMessage.getParent();
        if (barrePrivee != null) { barrePrivee.setVisible(false); barrePrivee.setManaged(false); }

        // Afficher zone groupe + sa barre de saisie
        if (scrollMessagesGroupe != null) {
            scrollMessagesGroupe.setVisible(true);
            scrollMessagesGroupe.setManaged(true);
        }
        javafx.scene.Node barreGroupe = champMessageGroupe != null ? champMessageGroupe.getParent() : null;
        if (barreGroupe != null) { barreGroupe.setVisible(true); barreGroupe.setManaged(true); }

        // Désactiver boutons appel (pas applicables aux groupes)
        boutonAppelVideo.setDisable(true);
        boutonAppelAudio.setDisable(true);

        // Charger les messages
        if (zoneMessagesGroupe != null) {
            zoneMessagesGroupe.getChildren().clear();
            List<String[]> histo = GroupDAO.historique(nom);
            for (String[] row : histo) {
                boolean sortant = row[0].equals(MainApp.client.getNomUtilisateur());
                ajouterBubble(zoneMessagesGroupe, row[1], sortant, row[0], "");
            }
            List<Message> attente = messagesGrpAttente.remove(nom);
            if (attente != null) {
                for (Message m : attente)
                    ajouterBubble(zoneMessagesGroupe, m.getContenu(), false, m.getExpediteur(), heure());
            }
        }
        rafraichirGroupes();
    }

    private void actualiserStatutContact() {
        if (contactActif == null) return;
        boolean enLigne = utilisateursEnLigne.contains(contactActif);
        labelStatutContact.setText(enLigne ? "● En ligne" : "○ Hors ligne");
        labelStatutContact.setStyle("-fx-text-fill:" + (enLigne ? "#25D366" : "#8696A0") + ";");
        boutonAppelVideo.setDisable(!enLigne);
        boutonAppelAudio.setDisable(!enLigne);
        champMessage.setDisable(false);
        boutonEnvoyer.setDisable(false);
        boutonFichier.setDisable(false);
    }

    // ── Bulles de message ──────────────────────────────────────

    private void ajouterBubble(VBox zone, String contenu, boolean sortant, String exp, String heure) {
        String texte = (heure.isEmpty() ? "" : "[" + heure + "] ")
                + (sortant ? "" : exp + " : ") + contenu;
        if (sortant) texte += "  ✓✓";

        Label b = new Label(texte);
        b.setWrapText(true);
        b.setMaxWidth(420);
        b.setFont(Font.font("Arial", 13));
        b.setPadding(new Insets(7, 10, 7, 10));
        b.setStyle("-fx-background-color:" + (sortant ? "#005C4B" : "#202C33")
                + "; -fx-background-radius:8px; -fx-text-fill:#E9EDEF;");

        HBox r = new HBox(b);
        r.setAlignment(sortant ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        r.setPadding(new Insets(2, 20, 2, 20));
        zone.getChildren().add(r);
    }

    private void ajouterBubbleFichier(VBox zone, String nom, boolean sortant, String heure) {
        String texte = "📎 [" + heure + "] " + nom;
        if (sortant) texte += "  ✓✓";

        Label b = new Label(texte);
        b.setWrapText(true);
        b.setMaxWidth(420);
        b.setFont(Font.font("Arial", 13));
        b.setPadding(new Insets(7, 10, 7, 10));
        b.setStyle("-fx-background-color:" + (sortant ? "#005C4B" : "#1A3A4A")
                + "; -fx-background-radius:8px; -fx-text-fill:#E9EDEF; -fx-cursor:hand;");

        if (!sortant) b.setOnMouseClicked(e -> ouvrirFichierRecu(nom));

        HBox r = new HBox(b);
        r.setAlignment(sortant ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        r.setPadding(new Insets(2, 20, 2, 20));
        zone.getChildren().add(r);
    }

    // ── Ouverture fenêtres appel ───────────────────────────────

    private void ouvrirFenetreVideoCall(String interlocuteur) {
        System.out.println("[Appel] ouverture fenêtre VIDEO avec " + interlocuteur);
        javax.swing.SwingUtilities.invokeLater(() -> {
            try {
                VideoCallWindow w = new VideoCallWindow(MainApp.client, interlocuteur);
                MainApp.client.setFenetreAppelActive(w);
                System.out.println("[Appel] fenêtre VIDEO ouverte.");
            } catch (Exception ex) {
                System.err.println("[Appel] ÉCHEC ouverture fenêtre vidéo : " + ex.getMessage());
                ex.printStackTrace();
            }
        });
    }

    private void ouvrirFenetreAudioCall(String interlocuteur) {
        System.out.println("[Appel] ouverture fenêtre AUDIO avec " + interlocuteur);
        javax.swing.SwingUtilities.invokeLater(() -> {
            try {
                AudioCallWindow w = new AudioCallWindow(MainApp.client, interlocuteur);
                MainApp.client.setFenetreAudioActive(w);
                System.out.println("[Appel] fenêtre AUDIO ouverte.");
            } catch (Exception ex) {
                System.err.println("[Appel] ÉCHEC ouverture fenêtre audio : " + ex.getMessage());
                ex.printStackTrace();
            }
        });
    }

    // ── Utilitaires ────────────────────────────────────────────

    private Label creerAvatar(String nom) {
        Label avatar = new Label(initiales(nom));
        avatar.setMinSize(42, 42);
        avatar.setMaxSize(42, 42);
        avatar.setAlignment(Pos.CENTER);
        avatar.setFont(Font.font("Arial", FontWeight.BOLD, 13));
        avatar.setTextFill(Color.WHITE);
        avatar.setStyle("-fx-background-color:" + couleur(nom)
                + "; -fx-background-radius:21px;");
        return avatar;
    }

    private void sauvegarderFichierRecu(String nom, String b64) {
        try {
            byte[] octets = java.util.Base64.getDecoder().decode(b64);
            File dossier = new File("received_files");
            if (!dossier.exists()) dossier.mkdirs();
            Files.write(new File(dossier, nom).toPath(), octets);
        } catch (Exception e) {
            System.err.println("[Chat] Erreur sauvegarde fichier : " + e.getMessage());
        }
    }

    private void ouvrirFichierRecu(String nom) {
        try {
            File f = new File("received_files/" + nom);
            if (f.exists()) java.awt.Desktop.getDesktop().open(f);
            else afficherInfo("Fichier introuvable : " + nom);
        } catch (Exception e) {
            afficherInfo("Impossible d'ouvrir : " + e.getMessage());
        }
    }

    private void afficherInfo(String message) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle("Info");
        a.setHeaderText(null);
        a.setContentText(message);
        a.show();
    }

    private String heure() {
        return LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
    }

    private String initiales(String nom) {
        String[] p = nom.trim().split("\\s+");
        if (p.length == 1) return p[0].substring(0, Math.min(2, p[0].length())).toUpperCase();
        return ("" + p[0].charAt(0) + p[1].charAt(0)).toUpperCase();
    }

    private String couleur(String nom) {
        return COULEURS[Math.abs(nom.hashCode()) % COULEURS.length];
    }
}