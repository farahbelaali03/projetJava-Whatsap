package ui;

import client.Client;
import media.AudioReceiver;
import media.AudioSender;
import media.VideoReceiver;
import media.VideoSender;
import model.Message;
import model.TypeMessage;

import javax.swing.*;
import java.awt.*;

/**
 * Fenêtre d'appel vidéo — layout corrigé.
 *
 * CORRECTIONS :
 *  - GridLayout(1,2) strict : exactement 2 zones égales, pas de divider parasite
 *  - JLabel sans taille fixe : s'adapte à la taille de la fenêtre
 *  - Labels créés AVANT demarrerMedia() pour que VideoSender les reçoive
 */
public class VideoCallWindow extends JFrame {

    private JLabel  labelVideoLocal;
    private JLabel  labelVideoDistant;
    private JButton boutonRaccrocher;
    private JButton boutonMute;

    private VideoSender   videoSender;
    private VideoReceiver videoReceiver;
    private AudioSender   audioSender;
    private AudioReceiver audioReceiver;

    private final Client client;
    private final String interlocuteur;
    private boolean      muet    = false;
    private boolean      termine = false;

    public VideoCallWindow(Client client, String interlocuteur) {
        this.client        = client;
        this.interlocuteur = interlocuteur;
        initialiserUI();
        demarrerMedia();
    }

    // ── API réseau ─────────────────────────────────────────────

    public void afficherFrameDistante(Message msg) {
        if (videoReceiver != null) videoReceiver.recevoirFrame(msg);
    }

    public void jouerAudioDistant(Message msg) {
        if (audioReceiver != null) audioReceiver.recevoirChunk(msg);
    }

    public void terminerAppelExterieur() {
        if (termine) return;
        termine = true;
        SwingUtilities.invokeLater(() -> {
            JOptionPane.showMessageDialog(this,
                    interlocuteur + " a raccroché.",
                    "Appel terminé",
                    JOptionPane.INFORMATION_MESSAGE);
            arreterMedia();
            client.setFenetreAppelActive(null);
            dispose();
        });
    }

    // ── Construction UI ────────────────────────────────────────

    private void initialiserUI() {
        setTitle("Appel vidéo avec " + interlocuteur);
        setSize(960, 580);
        setMinimumSize(new Dimension(640, 420));
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setLocationRelativeTo(null);
        setResizable(true);

        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(new Color(18, 18, 18));
        setContentPane(root);

        // Barre du haut
        JLabel statut = new JLabel("Appel en cours...", SwingConstants.CENTER);
        statut.setForeground(Color.WHITE);
        statut.setFont(new Font("Arial", Font.PLAIN, 13));
        statut.setBackground(new Color(18, 18, 18));
        statut.setOpaque(true);
        statut.setBorder(BorderFactory.createEmptyBorder(10, 0, 8, 0));
        root.add(statut, BorderLayout.NORTH);

        // Zone vidéos : GridLayout(1,2) — exactement 2 cellules égales
        JPanel zoneVideos = new JPanel(new GridLayout(1, 2, 6, 0));
        zoneVideos.setBackground(new Color(18, 18, 18));
        zoneVideos.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

        // Créer les labels AVANT demarrerMedia()
        labelVideoDistant = creerLabelVideo("En attente vidéo...");
        labelVideoLocal   = creerLabelVideo("Chargement caméra...");

        zoneVideos.add(creerPanneauVideo(labelVideoDistant, interlocuteur));
        zoneVideos.add(creerPanneauVideo(labelVideoLocal,
                client.getNomUtilisateur() + " (vous)"));

        root.add(zoneVideos,     BorderLayout.CENTER);
        root.add(creerBoutons(), BorderLayout.SOUTH);

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) { raccrocher(); }
        });

        setVisible(true);
    }

    private JLabel creerLabelVideo(String texte) {
        JLabel lbl = new JLabel(
                "<html><center><font color='#555555'>" + texte + "</font></center></html>",
                SwingConstants.CENTER);
        lbl.setBackground(Color.BLACK);
        lbl.setOpaque(true);
        // PAS de setPreferredSize → BorderLayout.CENTER remplit tout l'espace
        return lbl;
    }

    private JPanel creerPanneauVideo(JLabel label, String nom) {
        JPanel p = new JPanel(new BorderLayout(0, 0));
        p.setBackground(new Color(28, 28, 28));
        p.setBorder(BorderFactory.createLineBorder(new Color(55, 55, 55), 1));

        JLabel labelNom = new JLabel(nom, SwingConstants.CENTER);
        labelNom.setForeground(Color.WHITE);
        labelNom.setFont(new Font("Arial", Font.BOLD, 13));
        labelNom.setBackground(new Color(38, 38, 38));
        labelNom.setOpaque(true);
        labelNom.setPreferredSize(new Dimension(0, 30));

        p.add(label,    BorderLayout.CENTER);
        p.add(labelNom, BorderLayout.SOUTH);
        return p;
    }

    private JPanel creerBoutons() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.CENTER, 24, 12));
        p.setBackground(new Color(22, 22, 22));
        p.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(50, 50, 50)));

        boutonMute = new JButton("🎤 Muet");
        boutonMute.setBackground(new Color(70, 70, 70));
        boutonMute.setForeground(Color.WHITE);
        boutonMute.setFont(new Font("Arial", Font.BOLD, 14));
        boutonMute.setFocusPainted(false);
        boutonMute.setPreferredSize(new Dimension(150, 46));
        boutonMute.addActionListener(e -> basculerMute());

        boutonRaccrocher = new JButton("📵 Raccrocher");
        boutonRaccrocher.setBackground(new Color(210, 48, 48));
        boutonRaccrocher.setForeground(Color.WHITE);
        boutonRaccrocher.setFont(new Font("Arial", Font.BOLD, 14));
        boutonRaccrocher.setFocusPainted(false);
        boutonRaccrocher.setPreferredSize(new Dimension(170, 46));
        boutonRaccrocher.addActionListener(e -> raccrocher());

        p.add(boutonMute);
        p.add(boutonRaccrocher);
        return p;
    }

    // ── Logique ────────────────────────────────────────────────

    private void demarrerMedia() {
        videoSender   = new VideoSender(client, interlocuteur, labelVideoLocal);
        videoReceiver = new VideoReceiver(labelVideoDistant);
        audioSender   = new AudioSender(client, interlocuteur);
        audioReceiver = new AudioReceiver();
        videoSender.demarrer();
        videoReceiver.demarrer();
        audioSender.demarrer();
        audioReceiver.demarrer();
    }

    private void arreterMedia() {
        if (videoSender   != null) videoSender.arreter();
        if (videoReceiver != null) videoReceiver.arreter();
        if (audioSender   != null) audioSender.arreter();
        if (audioReceiver != null) audioReceiver.arreter();
    }

    private void raccrocher() {
        if (termine) return;
        termine = true;
        client.envoyer(new Message(
                client.getNomUtilisateur(), interlocuteur, "", TypeMessage.CALL_END));
        arreterMedia();
        client.setFenetreAppelActive(null);
        SwingUtilities.invokeLater(this::dispose);
    }

    private void basculerMute() {
        muet = !muet;
        if (muet) {
            audioSender.arreter();
            boutonMute.setText("🔇 Réactiver");
            boutonMute.setBackground(new Color(180, 100, 0));
        } else {
            audioSender.demarrer();
            boutonMute.setText("🎤 Muet");
            boutonMute.setBackground(new Color(70, 70, 70));
        }
    }
}