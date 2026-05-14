package model;

/**
 * Tous les types de messages échangés entre client et serveur.
 * IMPORTANT : doit être identique côté serveur et côté client.
 *
 * v3 ajouts :
 *   - AUDIO_CALL_REQUEST/ACCEPT/REJECT/END/ONLY : appel audio seul
 *   - GROUP_CREATE/ADD_MEMBER/REMOVE_MEMBER/MESSAGE/INFO : gestion groupes
 *   - GROUP_CALL_REQUEST/ACCEPT/END/AUDIO/VIDEO : réunion de groupe
 */
public enum TypeMessage {
    // Messagerie privée
    MESSAGE,
    FILE,

    // Connexion
    CONNECT,
    DISCONNECT,

    // Liste utilisateurs
    GET_USERS,

    // Appel VIDÉO
    CALL_REQUEST,
    CALL_ACCEPT,
    CALL_REJECT,
    CALL_END,
    AUDIO,
    VIDEO,

    // Appel AUDIO seul (nouveau)
    AUDIO_CALL_REQUEST,
    AUDIO_CALL_ACCEPT,
    AUDIO_CALL_REJECT,
    AUDIO_CALL_END,
    AUDIO_ONLY,

    // Groupes (Version 2)
    GROUP_CREATE,
    GROUP_ADD_MEMBER,
    GROUP_REMOVE_MEMBER,
    GROUP_MESSAGE,
    GROUP_INFO,

    // Réunion de groupe (Version 2)
    GROUP_CALL_REQUEST,
    GROUP_CALL_ACCEPT,
    GROUP_CALL_END,
    GROUP_AUDIO,
    GROUP_VIDEO
}
