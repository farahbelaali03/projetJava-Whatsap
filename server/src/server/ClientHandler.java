package server;

import database.UserDAO;
import model.Message;
import model.TypeMessage;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ClientHandler implements Runnable {

    public static final Map<String, Set<String>> groupes =
            new ConcurrentHashMap<>();

    public static final Map<String, Set<String>> reunions =
            new ConcurrentHashMap<>();

    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream  in;
    private String username;

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try {
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in = new ObjectInputStream(socket.getInputStream());
            Message msg;
            while ((msg = (Message) in.readObject()) != null) {
                traiter(msg);
            }
        } catch (Exception e) {
            System.out.println("[-] " + (username != null ? username : "?")
                    + " : connexion terminée (" + e.getClass().getSimpleName() + ")");
        } finally {
            handleDisconnect();
        }
    }

    private void traiter(Message msg) {
        switch (msg.getType()) {
            case CONNECT:    handleConnect(msg);    break;
            case DISCONNECT: handleDisconnect();    break;
            case GET_USERS:  broadcastUserList();   break;

            case MESSAGE:
            case VOICE_MESSAGE:
            case FILE:
            case CALL_REQUEST:
            case CALL_ACCEPT:
            case CALL_REJECT:
            case CALL_END:
            case AUDIO:
            case VIDEO:
            case AUDIO_CALL_REQUEST:
            case AUDIO_CALL_ACCEPT:
            case AUDIO_CALL_REJECT:
            case AUDIO_CALL_END:
            case AUDIO_ONLY:
                router(msg);
                break;

            case GROUP_CREATE:        handleGroupCreate(msg);       break;
            case GROUP_ADD_MEMBER:    handleGroupAddMember(msg);    break;
            case GROUP_REMOVE_MEMBER: handleGroupRemoveMember(msg); break;
            case GROUP_MESSAGE:       handleGroupMessage(msg);      break;

            case GROUP_CALL_REQUEST:  handleGroupCallRequest(msg);  break;
            case GROUP_CALL_ACCEPT:   handleGroupCallAccept(msg);   break;
            case GROUP_CALL_END:      handleGroupCallEnd(msg);      break;
            case GROUP_AUDIO:
            case GROUP_VIDEO:         handleGroupMedia(msg);        break;

            default:
                System.out.println("[!] Type inconnu : " + msg.getType());
        }
    }

    private void handleConnect(Message msg) {
        String demandeur  = msg.getExpediteur();
        String motDePasse = msg.getContenu();

        boolean ok = UserDAO.verifierLogin(demandeur, motDePasse);
        if (!ok && !UserDAO.utilisateurExiste(demandeur)) {
            UserDAO.creerUtilisateur(demandeur, motDePasse);
            ok = true;
            System.out.println("[i] Compte créé : " + demandeur);
        }

        if (ok) {
            this.username = demandeur;
            Server.clientsConnectes.put(demandeur, this);
            System.out.println("[OK] " + demandeur + " connecté.");
            envoyer(new Message("SERVER", demandeur, "LOGIN_OK", TypeMessage.CONNECT));
            broadcastUserList();
            envoyerGroupesUtilisateur(demandeur);

            final String userEnAttente = demandeur;
            final ClientHandler handlerRef = this;
            List<Message> enAttente = database.MessageEnAttenteDAO.recuperer(demandeur);
            if (!enAttente.isEmpty()) {
                new Thread(() -> {
                    try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
                    System.out.println("[File] Envoi de " + enAttente.size() + " message(s) en attente à " + userEnAttente);
                    for (Message m : enAttente) {
                        handlerRef.envoyer(m);
                    }
                    database.MessageEnAttenteDAO.supprimer(userEnAttente);
                }).start();
            }
        } else {
            System.out.println("[KO] Login refusé : " + demandeur);
            envoyer(new Message("SERVER", demandeur, "LOGIN_FAIL", TypeMessage.CONNECT));
        }
    }

    private void handleDisconnect() {
        if (username != null) {
            for (Map.Entry<String, Set<String>> entry : reunions.entrySet()) {
                entry.getValue().remove(username);
                if (entry.getValue().isEmpty()) reunions.remove(entry.getKey());
            }
            Server.clientsConnectes.remove(username);
            System.out.println("[-] " + username + " déconnecté.");
            broadcastUserList();
            username = null;
        }
        try { if (socket != null && !socket.isClosed()) socket.close(); }
        catch (Exception ignored) {}
    }

    private void broadcastUserList() {
        List<String> liste = new ArrayList<>(Server.clientsConnectes.keySet());
        String contenu = String.join(",", liste);
        Message msg = new Message("SERVER", "ALL", contenu, TypeMessage.GET_USERS);
        for (ClientHandler h : Server.clientsConnectes.values()) {
            h.envoyer(msg);
        }
    }

    private void envoyerGroupesUtilisateur(String user) {
        for (Map.Entry<String, Set<String>> entry : groupes.entrySet()) {
            if (entry.getValue().contains(user)) {
                String info = entry.getKey() + "::" + String.join(",", entry.getValue());
                envoyer(new Message("SERVER", user, info, TypeMessage.GROUP_INFO));
                System.out.println("[Groupe] Envoyé à " + user + " : " + info);
            }
        }
    }

    private void router(Message msg) {
        TypeMessage t = msg.getType();
        if (t != TypeMessage.VIDEO && t != TypeMessage.AUDIO
                && t != TypeMessage.AUDIO_ONLY) {
            System.out.println("[Routage] " + t + " : " + msg.getExpediteur()
                    + " -> " + msg.getDestinataire());
        }
        ClientHandler dest = Server.clientsConnectes.get(msg.getDestinataire());
        if (dest != null) {
            dest.envoyer(msg);
        } else {
            if (t == TypeMessage.MESSAGE || t == TypeMessage.FILE
                    || t == TypeMessage.VOICE_MESSAGE) {
                database.MessageEnAttenteDAO.sauvegarder(msg);
                System.out.println("[File] Message sauvegardé pour "
                        + msg.getDestinataire() + " (hors ligne)");
            } else if (t == TypeMessage.CALL_REQUEST || t == TypeMessage.AUDIO_CALL_REQUEST) {
                System.out.println("[!] " + msg.getDestinataire()
                        + " HORS LIGNE — appel " + t + " perdu");
            }
        }
    }

    private void handleGroupCreate(Message msg) {
        String nomGroupe = msg.getContenu().trim();
        String createur  = msg.getExpediteur();
        if (nomGroupe.isEmpty()) return;

        groupes.computeIfAbsent(nomGroupe, k -> ConcurrentHashMap.newKeySet()).add(createur);
        database.GroupDAO.sauvegarder(nomGroupe, groupes.get(nomGroupe));
        System.out.println("[Groupe] Créé : " + nomGroupe + " par " + createur);

        String info = nomGroupe + "::" + createur;
        envoyer(new Message("SERVER", createur, info, TypeMessage.GROUP_INFO));
    }

    private void handleGroupAddMember(Message msg) {
        String[] parts = msg.getContenu().split("::", 2);
        if (parts.length < 2) return;
        String nomGroupe = parts[0].trim();
        String membre    = parts[1].trim();
        if (membre.isEmpty()) return;

        Set<String> membres = groupes.computeIfAbsent(nomGroupe,
                k -> ConcurrentHashMap.newKeySet());
        membres.add(msg.getExpediteur());
        membres.add(membre);
        database.GroupDAO.sauvegarder(nomGroupe, membres);

        System.out.println("[Groupe] " + membre + " ajouté à " + nomGroupe
                + " (membres: " + membres + ")");
        broadcastGroupInfo(nomGroupe, membres);
    }

    private void handleGroupRemoveMember(Message msg) {
        String[] parts = msg.getContenu().split("::", 2);
        if (parts.length < 2) return;
        String nomGroupe = parts[0].trim();
        String membre    = parts[1].trim();

        Set<String> membres = groupes.get(nomGroupe);
        if (membres == null) return;

        membres.remove(membre);
        database.GroupDAO.sauvegarder(nomGroupe, membres);

        System.out.println("[Groupe] " + membre + " retiré de " + nomGroupe);

        ClientHandler destH = Server.clientsConnectes.get(membre);
        if (destH != null)
            destH.envoyer(new Message("SERVER", membre, nomGroupe + "::", TypeMessage.GROUP_INFO));

        broadcastGroupInfo(nomGroupe, membres);
    }

    private void handleGroupMessage(Message msg) {
        String nomGroupe = msg.getDestinataire();
        Set<String> membres = groupes.get(nomGroupe);
        if (membres == null) {
            System.out.println("[!] GROUP_MESSAGE : groupe '" + nomGroupe + "' introuvable !");
            return;
        }
        for (String membre : membres) {
            if (!membre.equals(msg.getExpediteur())) {
                ClientHandler h = Server.clientsConnectes.get(membre);
                if (h != null) h.envoyer(msg);
            }
        }
    }

    private void handleGroupCallRequest(Message msg) {
        String nomGroupe  = msg.getDestinataire();
        String initiateur = msg.getExpediteur();
        Set<String> membres = groupes.get(nomGroupe);

        System.out.println("[Réunion] GROUP_CALL_REQUEST de " + initiateur
                + " vers groupe '" + nomGroupe + "' membres=" + membres);

        if (membres == null) {
            System.out.println("[!] Groupe '" + nomGroupe + "' introuvable sur le serveur !");
            return;
        }

        reunions.computeIfAbsent(nomGroupe, k -> ConcurrentHashMap.newKeySet()).add(initiateur);

        int envoyes = 0;
        for (String membre : membres) {
            if (!membre.equals(initiateur)) {
                ClientHandler h = Server.clientsConnectes.get(membre);
                if (h != null) {
                    h.envoyer(msg);
                    envoyes++;
                    System.out.println("[Réunion] Invitation envoyée à " + membre);
                } else {
                    System.out.println("[Réunion] " + membre + " hors ligne - invitation perdue");
                }
            }
        }
        System.out.println("[Réunion] Total invitations envoyées : " + envoyes);
    }

    private void handleGroupCallAccept(Message msg) {
        String nomGroupe = msg.getDestinataire();
        String joinant   = msg.getExpediteur();

        Set<String> dejaDans = reunions.getOrDefault(nomGroupe, ConcurrentHashMap.newKeySet());

        for (String p : dejaDans) {
            if (!p.equals(joinant)) {
                ClientHandler h = Server.clientsConnectes.get(joinant);
                if (h != null) {
                    Message dejaPresent = new Message(p, nomGroupe, "", TypeMessage.GROUP_CALL_ACCEPT);
                    h.envoyer(dejaPresent);
                    System.out.println("[Réunion] Notifié " + joinant + " que " + p + " est déjà là");
                }
            }
        }

        reunions.computeIfAbsent(nomGroupe, k -> ConcurrentHashMap.newKeySet()).add(joinant);
        System.out.println("[Réunion] " + joinant + " a rejoint " + nomGroupe);

        Set<String> participants = reunions.get(nomGroupe);
        if (participants != null) {
            for (String p : participants) {
                if (!p.equals(joinant)) {
                    ClientHandler h = Server.clientsConnectes.get(p);
                    if (h != null) h.envoyer(msg);
                }
            }
        }
    }

    private void handleGroupCallEnd(Message msg) {
        String nomGroupe = msg.getDestinataire();
        String quittant  = msg.getExpediteur();

        System.out.println("[Réunion] " + quittant + " a quitté " + nomGroupe);

        Set<String> participants = reunions.get(nomGroupe);
        if (participants != null) {
            participants.remove(quittant);
            if (participants.isEmpty()) reunions.remove(nomGroupe);
            for (String p : participants) {
                ClientHandler h = Server.clientsConnectes.get(p);
                if (h != null) h.envoyer(msg);
            }
        }
    }

    private void handleGroupMedia(Message msg) {
        String nomGroupe  = msg.getDestinataire();
        Set<String> parts = reunions.get(nomGroupe);
        if (parts == null) return;

        for (String p : parts) {
            if (!p.equals(msg.getExpediteur())) {
                ClientHandler h = Server.clientsConnectes.get(p);
                if (h != null) h.envoyer(msg);
            }
        }
    }

    private void broadcastGroupInfo(String nomGroupe, Set<String> membres) {
        String contenu = nomGroupe + "::" + String.join(",", membres);
        Message infoMsg = new Message("SERVER", "ALL", contenu, TypeMessage.GROUP_INFO);
        for (String membre : membres) {
            ClientHandler h = Server.clientsConnectes.get(membre);
            if (h != null) h.envoyer(infoMsg);
        }
    }

    public synchronized void envoyer(Message msg) {
        try {
            out.writeObject(msg);
            out.flush();
            out.reset();
        } catch (Exception e) {
            System.err.println("Erreur envoi vers "
                    + (username != null ? username : "?") + " : " + e.getMessage());
        }
    }
}