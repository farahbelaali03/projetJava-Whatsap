package server;

import database.UserDAO;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;

public class Server {

    public static final int PORT = 5000;

    public static final ConcurrentHashMap<String, ClientHandler> clientsConnectes
            = new ConcurrentHashMap<>();

    public static void main(String[] args) {

        UserDAO.initialiserBase();
        database.GroupDAO.initialiserTable();
        database.MessageEnAttenteDAO.initialiserTable();
        ClientHandler.groupes.putAll(database.GroupDAO.chargerTousLesGroupes());

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