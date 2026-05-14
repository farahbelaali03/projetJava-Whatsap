package server;

import database.UserDAO;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Serveur central de l'application.
 *
 * Rôle :
 *   - Accepter les connexions TCP des clients
 *   - Authentifier (via la petite base users.db locale au serveur)
 *   - Router les messages, fichiers, frames audio/vidéo entre clients
 *
 * Le serveur ne stocke PAS l'historique des messages ni les contacts :
 * chaque client gère ça dans sa propre base locale (côté client).
 */
public class Server {

    public static final int PORT = 5000;

    /** Map (username -> handler) des clients actuellement connectés. */
    public static final ConcurrentHashMap<String, ClientHandler> clientsConnectes
            = new ConcurrentHashMap<>();

    public static void main(String[] args) {

        // Initialiser la base users.db (création si elle n'existe pas)
        UserDAO.initialiserBase();

        System.out.println("====================================");
        System.out.println("  Serveur WhatsApp - démarrage");
        System.out.println("  Port : " + PORT);
        System.out.println("====================================");

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {

            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("[+] Nouveau client : "
                        + socket.getInetAddress().getHostAddress());

                ClientHandler handler = new ClientHandler(socket);
                Thread thread = new Thread(handler);
                thread.setDaemon(false);
                thread.start();
            }

        } catch (Exception e) {
            System.err.println("Erreur fatale serveur : " + e.getMessage());
            e.printStackTrace();
        }
    }
}
