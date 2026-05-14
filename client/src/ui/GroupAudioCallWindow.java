package ui;

import client.Client;
import media.AudioReceiver;
import media.AudioSender;
import model.Message;
import model.TypeMessage;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Fenêtre de RÉUNION DE GROUPE (audio uniquement).
 *
 * Différences avec AudioCallWindow (appel 1-à-1) :
 *  - Le flux audio est envoyé avec GROUP_AUDIO vers le groupe (pas un utilisateur)
 *  - Affiche la liste des participants présents
 *  - Raccroche avec GROUP_CALL_END
 *  - Peut recevoir des flux de plusieurs personnes simultanément
 */
public class GroupAudioCallWindow extends JFrame {

    // ── UI ─────────────────────────────────────────────────────
    private JLabel   labelStatut;
    private JLabel   labelTimer;
    private JButton  boutonMute;
    private JButton  boutonRaccrocher;
    private JPanel   panneauParticipants;
    private DefaultListModel<String> modelParticipants;
    private JList<String>            listeParticipants;

    // ── Média ──────────────────────────────────────────────────
    private AudioSender   audioSender;
    private AudioReceiver audioReceiver;

    // ── État ───────────────────────────────────────────────────
    private final Client client;
    private final String nomGroupe;
    private boolean      muet    = false;
    private boolean      termine = false;

    private Timer swingTimer;
    private int   secondes = 0;

    private final List<String> participants = new ArrayList<>();

    public GroupAudioCallWindow(Client client, String nomGroupe) {
        this.client    = client;
        this.nomGroupe = nomGroupe;

        // L'initiateur est lui-même participant
        participants.add(client.getNomUtilisateur() + " (vous)");

        initialiserUI();
        demarrerMedia();
        demarrerChrono();
    }

    // ── API appelée par Client ──────────────────────────────────

    /** Reçoit un chunk audio d'un participant du groupe. */
    public void jouerAudio(Message msg) {
        if (audioReceiver != null) audioReceiver.recevoirChunk(msg);
    }

    /** Appelé quand un nouveau participant rejoint. */
    public void membreRejoint(String nomMembre) {
        if (!participants.contains(nomMembre)) {
            participants.add(nomMembre);
        }
        SwingUtilities.invokeLater(this::actualiserListeParticipants);
    }

    /** Appelé quand un participant quitte la réunion. */
    public void membreParti(String nomMembre) {
        participants.remove(nomMembre);
        SwingUtilities.invokeLater(() -> {
            actualiserListeParticipants();
            labelStatut.setText(nomMembre + " a quitté la réunion");
        });
    }

    /** Appelé si le groupe est dissous depuis l'extérieur. */
    public void terminerReunionExterieure() {
        if (termine) return;
        termine = true;
        SwingUtilities.invokeLater(() -> {
            if (swingTimer != null) swingTimer.stop();
            JOptionPane.showMessageDialog(this,
                    "La réunion du groupe \"" + nomGroupe + "\" est terminée.",
                    "Réunion terminée",
                    JOptionPane.INFORMATION_MESSAGE);
            arreterMedia();
            client.setFenetreReunionActive(null);
            dispose();
        });
    }

    // ── Construction UI ────────────────────────────────────────

    private void initialiserUI() {
        setTitle("Réunion : " + nomGroupe);
        setSize(420, 420);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setLocationRelativeTo(null);
        setResizable(false);
        getContentPane().setBackground(new Color(18, 18, 18));
        setLayout(new BorderLayout(8, 8));

        // ── En-tête ─────────────────────────────────────────────
        JPanel entete = new JPanel();
        entete.setLayout(new BoxLayout(entete, BoxLayout.Y_AXIS));
        entete.setBackground(new Color(18, 18, 18));
        entete.setBorder(BorderFactory.createEmptyBorder(18, 20, 8, 20));

        // Icône groupe
        JLabel iconGroupe = new JLabel("👥", SwingConstants.CENTER) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(123, 45, 139)); // violet
                g2.fillOval(0, 0, getWidth(), getHeight());
                g2.dispose();
                super.paintComponent(g);
            }
        };
        iconGroupe.setFont(new Font("Arial", Font.PLAIN, 30));
        iconGroupe.setForeground(Color.WHITE);
        iconGroupe.setOpaque(false);
        iconGroupe.setPreferredSize(new Dimension(80, 80));
        iconGroupe.setMaximumSize(new Dimension(80, 80));
        JPanel iconPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        iconPanel.setBackground(new Color(18, 18, 18));
        iconPanel.add(iconGroupe);

        JLabel labelNomGroupe = new JLabel(nomGroupe, SwingConstants.CENTER);
        labelNomGroupe.setFont(new Font("Arial", Font.BOLD, 17));
        labelNomGroupe.setForeground(Color.WHITE);
        labelNomGroupe.setAlignmentX(Component.CENTER_ALIGNMENT);

        labelStatut = new JLabel("Réunion en cours...", SwingConstants.CENTER);
        labelStatut.setFont(new Font("Arial", Font.PLAIN, 12));
        labelStatut.setForeground(new Color(180, 180, 180));
        labelStatut.setAlignmentX(Component.CENTER_ALIGNMENT);

        labelTimer = new JLabel("00:00", SwingConstants.CENTER);
        labelTimer.setFont(new Font("Arial", Font.BOLD, 22));
        labelTimer.setForeground(new Color(37, 211, 102));
        labelTimer.setAlignmentX(Component.CENTER_ALIGNMENT);

        entete.add(iconPanel);
        entete.add(Box.createVerticalStrut(6));
        entete.add(labelNomGroupe);
        entete.add(Box.createVerticalStrut(3));
        entete.add(labelStatut);
        entete.add(Box.createVerticalStrut(6));
        entete.add(labelTimer);

        add(entete, BorderLayout.NORTH);

        // ── Liste des participants ──────────────────────────────
        modelParticipants = new DefaultListModel<>();
        listeParticipants = new JList<>(modelParticipants);
        listeParticipants.setBackground(new Color(30, 30, 30));
        listeParticipants.setForeground(Color.WHITE);
        listeParticipants.setFont(new Font("Arial", Font.PLAIN, 13));
        listeParticipants.setSelectionBackground(new Color(55, 55, 55));
        listeParticipants.setCellRenderer(new ParticipantRenderer());
        actualiserListeParticipants();

        JLabel titreParticipants = new JLabel("  Participants", SwingConstants.LEFT);
        titreParticipants.setFont(new Font("Arial", Font.BOLD, 12));
        titreParticipants.setForeground(new Color(140, 140, 140));
        titreParticipants.setBackground(new Color(25, 25, 25));
        titreParticipants.setOpaque(true);
        titreParticipants.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 0));

        panneauParticipants = new JPanel(new BorderLayout());
        panneauParticipants.setBackground(new Color(25, 25, 25));
        panneauParticipants.add(titreParticipants, BorderLayout.NORTH);
        panneauParticipants.add(new JScrollPane(listeParticipants), BorderLayout.CENTER);
        panneauParticipants.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));

        add(panneauParticipants, BorderLayout.CENTER);

        // ── Boutons ─────────────────────────────────────────────
        add(creerBoutons(), BorderLayout.SOUTH);

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                raccrocher();
            }
        });

        setVisible(true);
    }

    private JPanel creerBoutons() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 12));
        p.setBackground(new Color(22, 22, 22));
        p.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(50, 50, 50)));

        boutonMute = new JButton("🎤 Muet");
        boutonMute.setBackground(new Color(60, 60, 60));
        boutonMute.setForeground(Color.WHITE);
        boutonMute.setFont(new Font("Arial", Font.BOLD, 13));
        boutonMute.setFocusPainted(false);
        boutonMute.setPreferredSize(new Dimension(130, 44));
        boutonMute.addActionListener(e -> basculerMute());

        boutonRaccrocher = new JButton("📵 Quitter");
        boutonRaccrocher.setBackground(new Color(220, 50, 50));
        boutonRaccrocher.setForeground(Color.WHITE);
        boutonRaccrocher.setFont(new Font("Arial", Font.BOLD, 13));
        boutonRaccrocher.setFocusPainted(false);
        boutonRaccrocher.setPreferredSize(new Dimension(140, 44));
        boutonRaccrocher.addActionListener(e -> raccrocher());

        p.add(boutonMute);
        p.add(boutonRaccrocher);
        return p;
    }

    // ── Logique ─────────────────────────────────────────────────

    private void demarrerMedia() {
        // Envoie l'audio vers le groupe (destinataire = nomGroupe, type = GROUP_AUDIO)
        audioSender   = new AudioSender(client, nomGroupe, TypeMessage.GROUP_AUDIO);
        audioReceiver = new AudioReceiver();
        audioSender.demarrer();
        audioReceiver.demarrer();
    }

    private void arreterMedia() {
        if (audioSender   != null) audioSender.arreter();
        if (audioReceiver != null) audioReceiver.arreter();
    }

    private void demarrerChrono() {
        swingTimer = new Timer(1000, e -> {
            secondes++;
            int m = secondes / 60;
            int s = secondes % 60;
            labelTimer.setText(String.format("%02d:%02d", m, s));
        });
        swingTimer.start();
    }

    private void raccrocher() {
        if (termine) return;
        termine = true;
        if (swingTimer != null) swingTimer.stop();

        // Prévenir le serveur et tous les participants
        client.quitterReunionGroupe(nomGroupe);
        arreterMedia();
        client.setFenetreReunionActive(null);
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
            boutonMute.setBackground(new Color(60, 60, 60));
        }
    }

    private void actualiserListeParticipants() {
        modelParticipants.clear();
        for (String p : participants) modelParticipants.addElement(p);
        labelStatut.setText(participants.size() + " participant(s)");
    }

    // ── Renderer participant ────────────────────────────────────

    private static class ParticipantRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list,
                                                      Object value, int index, boolean isSelected, boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(
                    list, value, index, isSelected, cellHasFocus);
            label.setText("  🎙 " + value);
            label.setBorder(BorderFactory.createEmptyBorder(5, 6, 5, 6));
            label.setFont(new Font("Arial", Font.PLAIN, 13));
            if (!isSelected) {
                label.setBackground(new Color(30, 30, 30));
                label.setForeground(Color.WHITE);
            }
            return label;
        }
    }
}