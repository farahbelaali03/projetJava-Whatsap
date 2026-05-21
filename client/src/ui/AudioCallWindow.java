package ui;

import client.Client;
import media.AudioReceiver;
import media.AudioSender;
import model.Message;
import model.TypeMessage;

import javax.swing.*;
import java.awt.*;


public class AudioCallWindow extends JFrame {

    private JLabel   labelStatut;
    private JLabel   labelTimer;
    private JButton  boutonMute;
    private JButton  boutonRaccrocher;

    private AudioSender   audioSender;
    private AudioReceiver audioReceiver;

    private final Client  client;
    private final String  interlocuteur;
    private boolean       muet    = false;
    private boolean       termine = false;

    // Chronomètre
    private Timer swingTimer;
    private int   secondes = 0;

    public AudioCallWindow(Client client, String interlocuteur) {
        this.client        = client;
        this.interlocuteur = interlocuteur;
        initialiserUI();
        demarrerMedia();
        demarrerChrono();
    }



    public void jouerAudio(Message msg) {
        if (audioReceiver != null) audioReceiver.recevoirChunk(msg);
    }


    public void terminerAppelExterieur() {
        if (termine) return;
        termine = true;
        SwingUtilities.invokeLater(() -> {
            if (swingTimer != null) swingTimer.stop();
            JOptionPane.showMessageDialog(this,
                    interlocuteur + " a raccroché.",
                    "Appel terminé",
                    JOptionPane.INFORMATION_MESSAGE);
            arreterMedia();
            client.setFenetreAudioActive(null);
            dispose();
        });
    }


    private void initialiserUI() {
        setTitle("Appel audio avec " + interlocuteur);
        setSize(360, 300);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setLocationRelativeTo(null);
        setResizable(false);
        getContentPane().setBackground(new Color(18, 18, 18));
        setLayout(new BorderLayout(10, 10));

        JPanel centre = new JPanel();
        centre.setLayout(new BoxLayout(centre, BoxLayout.Y_AXIS));
        centre.setBackground(new Color(18, 18, 18));
        centre.setBorder(BorderFactory.createEmptyBorder(20, 20, 10, 20));

        JLabel avatar = new JLabel(initiales(interlocuteur), SwingConstants.CENTER);
        avatar.setFont(new Font("Arial", Font.BOLD, 28));
        avatar.setForeground(Color.WHITE);
        avatar.setOpaque(true);
        avatar.setBackground(new Color(37, 211, 102));
        avatar.setPreferredSize(new Dimension(80, 80));
        avatar.setMaximumSize(new Dimension(80, 80));
        avatar.setBorder(BorderFactory.createEmptyBorder());
        JPanel avatarPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        avatarPanel.setBackground(new Color(18, 18, 18));
        // Rendre le label rond via paintComponent custom
        JLabel avatarRond = new JLabel(initiales(interlocuteur), SwingConstants.CENTER) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(37, 211, 102));
                g2.fillOval(0, 0, getWidth(), getHeight());
                g2.dispose();
                super.paintComponent(g);
            }
        };
        avatarRond.setFont(new Font("Arial", Font.BOLD, 26));
        avatarRond.setForeground(Color.WHITE);
        avatarRond.setOpaque(false);
        avatarRond.setPreferredSize(new Dimension(80, 80));
        avatarPanel.add(avatarRond);

        JLabel labelNom = new JLabel(interlocuteur, SwingConstants.CENTER);
        labelNom.setFont(new Font("Arial", Font.BOLD, 18));
        labelNom.setForeground(Color.WHITE);
        labelNom.setAlignmentX(Component.CENTER_ALIGNMENT);

        labelStatut = new JLabel("Appel audio en cours...", SwingConstants.CENTER);
        labelStatut.setFont(new Font("Arial", Font.PLAIN, 13));
        labelStatut.setForeground(new Color(180, 180, 180));
        labelStatut.setAlignmentX(Component.CENTER_ALIGNMENT);

        labelTimer = new JLabel("00:00", SwingConstants.CENTER);
        labelTimer.setFont(new Font("Arial", Font.BOLD, 22));
        labelTimer.setForeground(new Color(37, 211, 102));
        labelTimer.setAlignmentX(Component.CENTER_ALIGNMENT);

        centre.add(avatarPanel);
        centre.add(Box.createVerticalStrut(8));
        centre.add(labelNom);
        centre.add(Box.createVerticalStrut(4));
        centre.add(labelStatut);
        centre.add(Box.createVerticalStrut(8));
        centre.add(labelTimer);

        add(centre, BorderLayout.CENTER);
        add(creerBoutons(), BorderLayout.SOUTH);

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) { raccrocher(); }
        });

        setVisible(true);
    }

    private JPanel creerBoutons() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 12));
        p.setBackground(new Color(18, 18, 18));

        boutonMute = new JButton("🎤 Muet");
        boutonMute.setBackground(new Color(60, 60, 60));
        boutonMute.setForeground(Color.WHITE);
        boutonMute.setFont(new Font("Arial", Font.BOLD, 13));
        boutonMute.setFocusPainted(false);
        boutonMute.setPreferredSize(new Dimension(130, 44));
        boutonMute.addActionListener(e -> basculerMute());

        boutonRaccrocher = new JButton("📵 Raccrocher");
        boutonRaccrocher.setBackground(new Color(220, 50, 50));
        boutonRaccrocher.setForeground(Color.WHITE);
        boutonRaccrocher.setFont(new Font("Arial", Font.BOLD, 13));
        boutonRaccrocher.setFocusPainted(false);
        boutonRaccrocher.setPreferredSize(new Dimension(150, 44));
        boutonRaccrocher.addActionListener(e -> raccrocher());

        p.add(boutonMute);
        p.add(boutonRaccrocher);
        return p;
    }

    private void demarrerMedia() {
        audioSender   = new AudioSender(client, interlocuteur, TypeMessage.AUDIO_ONLY);
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
        client.envoyer(new Message(
                client.getNomUtilisateur(), interlocuteur, "", TypeMessage.AUDIO_CALL_END));
        arreterMedia();
        client.setFenetreAudioActive(null);
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

    private String initiales(String nom) {
        String[] p = nom.trim().split("\\s+");
        if (p.length == 1) return p[0].substring(0, Math.min(2, p[0].length())).toUpperCase();
        return ("" + p[0].charAt(0) + p[1].charAt(0)).toUpperCase();
    }
}
