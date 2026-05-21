package ui;

import client.Client;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.stage.Stage;

import java.io.File;
import java.net.URL;


public class MainApp extends Application {


    public static final Client client = new Client();

    private static ChatController  controleurChat;
    private static LoginController controleurLogin;
    private static Stage           fenetrePrincipale;

    @Override
    public void start(Stage stage) {
        fenetrePrincipale = stage;
        stage.setTitle("WhatsApp");
        stage.setMaximized(true);
        changerScene("login.fxml");
        stage.setOnCloseRequest(e -> {
            client.deconnecter();
            Platform.exit();
            System.exit(0);
        });
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }


    public static void changerScene(String nomFxml) {
        try {
            URL fxmlUrl = trouverFxml(nomFxml);
            if (fxmlUrl == null) {
                throw new IllegalStateException("FXML introuvable : " + nomFxml
                        + ". Vérifie que le dossier fxml est marqué comme 'Resources Root' dans IntelliJ.");
            }

            FXMLLoader loader = new FXMLLoader(fxmlUrl);
            Parent racine = loader.load();

            // Identifier le contrôleur chargé
            Object ctrl = loader.getController();
            if (ctrl instanceof ChatController) {
                controleurChat  = (ChatController) ctrl;
                controleurLogin = null;
            } else if (ctrl instanceof LoginController) {
                controleurLogin = (LoginController) ctrl;
                controleurChat  = null;
            }

            fenetrePrincipale.setScene(new Scene(racine));

        } catch (Exception e) {
            e.printStackTrace();
            // Affichage de l'erreur dans la fenêtre pour qu'on la voie
            String msg = "Erreur chargement UI : " + e.getMessage();
            fenetrePrincipale.setScene(new Scene(new Label(msg), 700, 200));
        }
    }

    private static URL trouverFxml(String nomFxml) throws Exception {
        URL u = MainApp.class.getResource("/ui/fxml/" + nomFxml);
        if (u != null) return u;

        u = MainApp.class.getResource("/" + nomFxml);
        if (u != null) return u;

        File f = new File("src/ui/fxml/" + nomFxml);
        if (f.exists()) return f.toURI().toURL();

        f = new File("client/src/ui/fxml/" + nomFxml);
        if (f.exists()) return f.toURI().toURL();

        return null;
    }

    public static ChatController  getControleurChat()  { return controleurChat; }
    public static LoginController getControleurLogin() { return controleurLogin; }
    public static Stage           getFenetrePrincipale(){ return fenetrePrincipale; }
}