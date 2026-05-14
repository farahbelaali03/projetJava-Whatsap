package server;

import database.UserDAO;
import model.Message;
import model.TypeMessage;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handler d'un client connecté au serveur.
 *
 * Routage des messages privés + gestion des groupes + appels audio/vidéo.
 * Les groupes sont stockés en mémoire côté serveur (Map nomGroupe -> membres).
 *
 * Version 2 : gestion groupes, appel audio seul, réunions de groupe.
 */
public class ClientHandler implements Runnable {

    /** Groupes en mémoire : nomGroupe -> ensemble de membres */
    public static final Map<String, Set<String>> groupes =
            new ConcurrentHashMap<>();

    /** Participants aux réunions actives : nomGroupe -> ensemble participants */
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

            // Messages / fichiers privés + appel vidéo + appel audio
            case MESSAGE:
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

            // Groupes
            case GROUP_CREATE:        handleGroupCreate(msg);       break;
            case GROUP_ADD_MEMBER:    handleGroupAddMember(msg);    break;
            case GROUP_REMOVE_MEMBER: handleGroupRemoveMember(msg); break;
            case GROUP_MESSAGE:       handleGroupMessage(msg);      break;

            // Réunions de groupe
            case GROUP_CALL_REQUEST:  handleGroupCallRequest(msg);  break;
            case GROUP_CALL_ACCEPT:   handleGroupCallAccept(msg);   break;
            case GROUP_CALL_END:      handleGroupCallEnd(msg);      break;
            case GROUP_AUDIO:
            case GROUP_VIDEO:         handleGroupMedia(msg);        break;

            default:
                System.out.println("[!] Type inconnu : " + msg.getType());
        }
    }

    // ── CONNEXION ──────────────────────────────────────────────

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
            // Envoyer les groupes dont l'utilisateur est membre
            envoyerGroupesUtilisateur(demandeur);
        } else {
            System.out.println("[KO] Login refusé : " + demandeur);
            envoyer(new Message("SERVER", demandeur, "LOGIN_FAIL", TypeMessage.CONNECT));
        }
    }

    private void handleDisconnect() {
        if (username != null) {
            // Retirer des réunions actives
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

    /** Envoie à un utilisateur qui vient de se connecter la liste de ses groupes. */
    private void envoyerGroupesUtilisateur(String user) {
        for (Map.Entry<String, Set<String>> entry : groupes.entrySet()) {
            if (entry.getValue().contains(user)) {
                // contenu : "nomGroupe::membre1,membre2,..."
                String info = entry.getKey() + "::" + String.join(",", entry.getValue());
                envoyer(new Message("SERVER", user, info, TypeMessage.GROUP_INFO));
            }
        }
    }

    // ── ROUTAGE PRIVÉ ──────────────────────────────────────────

    private void router(Message msg) {
        TypeMessage t = msg.getType();
        // Log pour les messages d'appel (pas le spam audio/vidéo)
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
                    || t == TypeMessage.CALL_REQUEST
                    || t == TypeMessage.AUDIO_CALL_REQUEST) {
                System.out.println("[!] Destinataire " + msg.getDestinataire()
                        + " HORS LIGNE — message " + t + " perdu de " + msg.getExpediteur());
            }
        }
    }

    // ── GROUPES ────────────────────────────────────────────────

    private void handleGroupCreate(Message msg) {
        String nomGroupe  = msg.getContenu().trim();
        String createur   = msg.getExpediteur();
        if (nomGroupe.isEmpty()) return;

        groupes.computeIfAbsent(nomGroupe, k -> ConcurrentHashMap.newKeySet()).add(createur);
        System.out.println("[Groupe] Créé : " + nomGroupe + " par " + createur);

        // Informer le créateur
        String info = nomGroupe + "::" + createur;
        envoyer(new Message("SERVER", createur, info, TypeMessage.GROUP_INFO));
    }

    private void handleGroupAddMember(Message msg) {
        // contenu = "nomGroupe::nouveauMembre"
        String[] parts = msg.getContenu().split("::", 2);
        if (parts.length < 2) return;
        String nomGroupe = parts[0].trim();
        String membre    = parts[1].trim();
        if (membre.isEmpty()) return;

        // Si le groupe n'est plus en RAM (redémarrage serveur), le recréer
        Set<String> membres = groupes.computeIfAbsent(nomGroupe,
                k -> ConcurrentHashMap.newKeySet());

        // S'assurer que le créateur (expéditeur) est bien membre
        membres.add(msg.getExpediteur());
        membres.add(membre);
        System.out.println("[Groupe] " + membre + " ajouté à " + nomGroupe
                + " (membres: " + membres + ")");

        // Notifier tous les membres connectés
        broadcastGroupInfo(nomGroupe, membres);
    }

    private void handleGroupRemoveMember(Message msg) {
        // contenu = "nomGroupe::membre"
        String[] parts = msg.getContenu().split("::", 2);
        if (parts.length < 2) return;
        String nomGroupe = parts[0].trim();
        String membre    = parts[1].trim();

        Set<String> membres = groupes.get(nomGroupe);
        if (membres == null) return;

        membres.remove(membre);
        System.out.println("[Groupe] " + membre + " retiré de " + nomGroupe);

        // Notifier le membre retiré (info vide = retiré)
        ClientHandler destH = Server.clientsConnectes.get(membre);
        if (destH != null)
            destH.envoyer(new Message("SERVER", membre, nomGroupe + "::", TypeMessage.GROUP_INFO));

        // Notifier les membres restants
        broadcastGroupInfo(nomGroupe, membres);
    }

    private void handleGroupMessage(Message msg) {
        // destinataire = nomGroupe
        String nomGroupe  = msg.getDestinataire();
        Set<String> membres = groupes.get(nomGroupe);
        if (membres == null) return;

        for (String membre : membres) {
            if (!membre.equals(msg.getExpediteur())) {
                ClientHandler h = Server.clientsConnectes.get(membre);
                if (h != null) h.envoyer(msg);
            }
        }
    }

    // ── RÉUNIONS DE GROUPE ─────────────────────────────────────

    private void handleGroupCallRequest(Message msg) {
        // Diffuser à tous les membres du groupe
        String nomGroupe    = msg.getDestinataire();
        String initiateur   = msg.getExpediteur();
        Set<String> membres = groupes.get(nomGroupe);
        if (membres == null) return;

        reunions.computeIfAbsent(nomGroupe, k -> ConcurrentHashMap.newKeySet()).add(initiateur);

        for (String membre : membres) {
            if (!membre.equals(initiateur)) {
                ClientHandler h = Server.clientsConnectes.get(membre);
                if (h != null) h.envoyer(msg);
            }
        }
    }

    private void handleGroupCallAccept(Message msg) {
        String nomGroupe = msg.getDestinataire();
        String joinant   = msg.getExpediteur();

        Set<String> participants = reunions.computeIfAbsent(nomGroupe, k -> ConcurrentHashMap.newKeySet());

        // 1) Envoyer au nouveau joinant un GROUP_CALL_ACCEPT pour chaque participant deja present
        ClientHandler joinantHandler = Server.clientsConnectes.get(joinant);
        for (String dejaPresent : new java.util.ArrayList<>(participants)) {
            if (joinantHandler != null) {
                Message notifExistant = new Message(dejaPresent, nomGroupe, "", TypeMessage.GROUP_CALL_ACCEPT);
                joinantHandler.envoyer(notifExistant);
            }
        }

        // 2) Ajouter le joinant apres avoir notifie (pour ne pas s'inclure lui-meme)
        participants.add(joinant);

        // 3) Notifier les autres participants que le joinant a rejoint
        for (String p : participants) {
            if (!p.equals(joinant)) {
                ClientHandler h = Server.clientsConnectes.get(p);
                if (h != null) h.envoyer(msg);
            }
        }
    }

    private void handleGroupCallEnd(Message msg) {
        String nomGroupe = msg.getDestinataire();
        String quittant  = msg.getExpediteur();

        Set<String> participants = reunions.get(nomGroupe);
        if (participants != null) {
            participants.remove(quittant);
            if (participants.isEmpty()) reunions.remove(nomGroupe);

            // Notifier les autres
            for (String p : participants) {
                ClientHandler h = Server.clientsConnectes.get(p);
                if (h != null) h.envoyer(msg);
            }
        }
    }

    private void handleGroupMedia(Message msg) {
        // Diffuser l'audio/vidéo à tous les participants de la réunion
        String nomGroupe     = msg.getDestinataire();
        Set<String> parts    = reunions.get(nomGroupe);
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

    // ── ENVOI ──────────────────────────────────────────────────

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