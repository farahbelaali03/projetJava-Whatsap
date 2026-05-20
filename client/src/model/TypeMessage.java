package model;

public enum TypeMessage {
    // Messagerie privée
    MESSAGE,
    FILE,
    VOICE_MESSAGE,

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

    // Appel AUDIO seul
    AUDIO_CALL_REQUEST,
    AUDIO_CALL_ACCEPT,
    AUDIO_CALL_REJECT,
    AUDIO_CALL_END,
    AUDIO_ONLY,

    // Groupes
    GROUP_CREATE,
    GROUP_ADD_MEMBER,
    GROUP_REMOVE_MEMBER,
    GROUP_MESSAGE,
    GROUP_INFO,

    // Réunion de groupe
    GROUP_CALL_REQUEST,
    GROUP_CALL_ACCEPT,
    GROUP_CALL_END,
    GROUP_AUDIO,
    GROUP_VIDEO
}