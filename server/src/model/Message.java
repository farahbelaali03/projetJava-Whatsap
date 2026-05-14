package model;

import java.io.Serializable;

/**
 * Classe Message échangée entre le serveur et les clients via ObjectStream.
 *
 * IMPORTANT : cette classe DOIT être strictement identique côté serveur
 * et côté client (même package, mêmes champs, même serialVersionUID).
 */
public class Message implements Serializable {

    private static final long serialVersionUID = 1L;

    private String expediteur;
    private String destinataire;
    private String contenu;
    private TypeMessage type;

    public Message(String expediteur, String destinataire,
                   String contenu, TypeMessage type) {
        this.expediteur = expediteur;
        this.destinataire = destinataire;
        this.contenu = contenu;
        this.type = type;
    }

    public String getExpediteur()    { return expediteur; }
    public String getDestinataire()  { return destinataire; }
    public String getContenu()       { return contenu; }
    public TypeMessage getType()     { return type; }

    public void setContenu(String contenu) { this.contenu = contenu; }

    @Override
    public String toString() {
        return "Message{" + type + " " + expediteur + " -> " + destinataire + "}";
    }
}
