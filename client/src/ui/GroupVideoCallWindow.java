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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class GroupVideoCallWindow extends JFrame {

    private JLabel   labelNomGroupe;
    private JLabel   labelStatut;
    private JLabel   labelTimer;
    private JButton  boutonMute;
    private JButton  boutonCamera;
    private JButton  boutonRaccrocher;

    private JPanel   panneauVideos;
    private JLabel   labelVideoLocal;

    private final Map<String, JLabel>        labelsDistants = new ConcurrentHashMap<>();
    private final Map<String, VideoReceiver> receiversVideo = new ConcurrentHashMap<>();

    private VideoSender   videoSender;
    private AudioSender   audioSender;
    private AudioReceiver audioReceiver;

    private final Client client;
    private final String nomGroupe;
    private boolean      muet        = false;
    private boolean      cameraOff   = false;
    private boolean      termine     = false;

    private Timer swingTimer;
    private int   secondes = 0;

    private final List<String> participants = new ArrayList<>();

    public GroupVideoCallWindow(Client client, String nomGroupe) {
        this.client    = client;
        this.nomGroupe = nomGroupe;
        participants.add(client.getNomUtilisateur() + " (vous)");
        initialiserUI();
        demarrerMedia();
        demarrerChrono();
    }


    public void afficherFrameDistante(Message msg) {
        String exp = msg.getExpediteur();
        if (!receiversVideo.containsKey(exp)) {
            SwingUtilities.invokeLater(() -> ajouterPanneauParticipant(exp));
        }
        VideoReceiver vr = receiversVideo.get(exp);
        if (vr != null) vr.recevoirFrame(msg);
    }

    public void jouerAudio(Message msg) {
        if (audioReceiver != null) audioReceiver.recevoirChunk(msg);
    }

    public void membreRejoint(String nomMembre) {
        if (!participants.contains(nomMembre)) participants.add(nomMembre);
        SwingUtilities.invokeLater(() -> {
            actualiserStatut();
            ajouterPanneauParticipant(nomMembre);
        });
    }

    public void membreParti(String nomMembre) {
        participants.remove(nomMembre);
        SwingUtilities.invokeLater(() -> {
            actualiserStatut();
            retirerPanneauParticipant(nomMembre);
        });
    }

    public void terminerReunionExterieure() {
        if (termine) return;
        termine = true;
        SwingUtilities.invokeLater(() -> {
            if (swingTimer != null) swingTimer.stop();
            JOptionPane.showMessageDialog(this,
                    "La réunion vidéo \"" + nomGroupe + "\" est terminée.",
                    "Réunion terminée", JOptionPane.INFORMATION_MESSAGE);
            arreterMedia();
            client.setFenetreReunionVideoActive(null);
            dispose();
        });
    }


    private void ajouterPanneauParticipant(String nom) {
        if (labelsDistants.containsKey(nom)) return;

        JLabel label = creerLabelVideo("📷 " + nom);
        labelsDistants.put(nom, label);

        VideoReceiver vr = new VideoReceiver(label);
        vr.demarrer();
        receiversVideo.put(nom, vr);

        JPanel panneau = creerPanneauVideo(label, nom);
        panneauVideos.add(panneau);
        reorganiserGrille();
    }

    private void retirerPanneauParticipant(String nom) {
        VideoReceiver vr = receiversVideo.remove(nom);
        if (vr != null) vr.arreter();

        JLabel label = labelsDistants.remove(nom);
        if (label != null) {
            // Trouver et supprimer le panneau parent
            for (Component c : panneauVideos.getComponents()) {
                if (c instanceof JPanel) {
                    JPanel p = (JPanel) c;
                    for (Component child : p.getComponents()) {
                        if (child == label) {
                            panneauVideos.remove(p);
                            break;
                        }
                    }
                }
            }
        }
        reorganiserGrille();
    }

    /** ✅ Réorganise la grille selon le nombre de participants */
    private void reorganiserGrille() {
        int total = 1 + labelsDistants.size(); // local + distants
        int cols = total <= 1 ? 1 : total <= 4 ? 2 : 3;
        int rows = (int) Math.ceil((double) total / cols);
        ((GridLayout) panneauVideos.getLayout()).setColumns(cols);
        ((GridLayout) panneauVideos.getLayout()).setRows(rows);
        panneauVideos.revalidate();
        panneauVideos.repaint();
    }


    private void initialiserUI() {
        setTitle("Réunion vidéo : " + nomGroupe);
        setSize(1000, 650);
        setMinimumSize(new Dimension(720, 480));
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setLocationRelativeTo(null);
        setResizable(true);
        getContentPane().setBackground(new Color(18, 18, 18));
        setLayout(new BorderLayout(6, 6));

        JPanel entete = new JPanel(new BorderLayout());
        entete.setBackground(new Color(18, 18, 18));
        entete.setBorder(BorderFactory.createEmptyBorder(10, 16, 6, 16));

        labelNomGroupe = new JLabel("📹 Réunion vidéo : " + nomGroupe, SwingConstants.LEFT);
        labelNomGroupe.setFont(new Font("Arial", Font.BOLD, 15));
        labelNomGroupe.setForeground(Color.WHITE);

        labelStatut = new JLabel("1 participant(s)", SwingConstants.CENTER);
        labelStatut.setFont(new Font("Arial", Font.PLAIN, 12));
        labelStatut.setForeground(new Color(180, 180, 180));

        labelTimer = new JLabel("00:00", SwingConstants.RIGHT);
        labelTimer.setFont(new Font("Arial", Font.BOLD, 16));
        labelTimer.setForeground(new Color(37, 211, 102));

        entete.add(labelNomGroupe, BorderLayout.WEST);
        entete.add(labelStatut,    BorderLayout.CENTER);
        entete.add(labelTimer,     BorderLayout.EAST);
        add(entete, BorderLayout.NORTH);

        panneauVideos = new JPanel(new GridLayout(1, 1, 6, 6));
        panneauVideos.setBackground(new Color(18, 18, 18));
        panneauVideos.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        labelVideoLocal = creerLabelVideo("Chargement caméra...");
        panneauVideos.add(creerPanneauVideo(labelVideoLocal, client.getNomUtilisateur() + " (vous)"));

        JScrollPane scroll = new JScrollPane(panneauVideos);
        scroll.setBorder(null);
        scroll.setBackground(new Color(18, 18, 18));
        add(scroll, BorderLayout.CENTER);

        add(creerBoutons(), BorderLayout.SOUTH);

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
        lbl.setPreferredSize(new Dimension(320, 240));
        return lbl;
    }

    private JPanel creerPanneauVideo(JLabel label, String nom) {
        JPanel p = new JPanel(new BorderLayout(0, 0));
        p.setBackground(new Color(28, 28, 28));
        p.setBorder(BorderFactory.createLineBorder(new Color(55, 55, 55), 1));
        JLabel labelNom = new JLabel(nom, SwingConstants.CENTER);
        labelNom.setForeground(Color.WHITE);
        labelNom.setFont(new Font("Arial", Font.BOLD, 12));
        labelNom.setBackground(new Color(38, 38, 38));
        labelNom.setOpaque(true);
        labelNom.setPreferredSize(new Dimension(0, 28));
        p.add(label,    BorderLayout.CENTER);
        p.add(labelNom, BorderLayout.SOUTH);
        return p;
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

        boutonCamera = new JButton("📷 Caméra");
        boutonCamera.setBackground(new Color(60, 60, 60));
        boutonCamera.setForeground(Color.WHITE);
        boutonCamera.setFont(new Font("Arial", Font.BOLD, 13));
        boutonCamera.setFocusPainted(false);
        boutonCamera.setPreferredSize(new Dimension(140, 44));
        boutonCamera.addActionListener(e -> basculerCamera());

        boutonRaccrocher = new JButton("📵 Quitter");
        boutonRaccrocher.setBackground(new Color(220, 50, 50));
        boutonRaccrocher.setForeground(Color.WHITE);
        boutonRaccrocher.setFont(new Font("Arial", Font.BOLD, 13));
        boutonRaccrocher.setFocusPainted(false);
        boutonRaccrocher.setPreferredSize(new Dimension(140, 44));
        boutonRaccrocher.addActionListener(e -> raccrocher());

        p.add(boutonMute);
        p.add(boutonCamera);
        p.add(boutonRaccrocher);
        return p;
    }


    private void demarrerMedia() {
        videoSender   = new VideoSender(client, nomGroupe, TypeMessage.GROUP_VIDEO, labelVideoLocal);
        audioSender   = new AudioSender(client, nomGroupe, TypeMessage.GROUP_AUDIO);
        audioReceiver = new AudioReceiver();
        videoSender.demarrer();
        audioSender.demarrer();
        audioReceiver.demarrer();
    }

    private void arreterMedia() {
        if (videoSender   != null) videoSender.arreter();
        if (audioSender   != null) audioSender.arreter();
        if (audioReceiver != null) audioReceiver.arreter();
        for (VideoReceiver vr : receiversVideo.values()) vr.arreter();
        receiversVideo.clear();
    }

    private void demarrerChrono() {
        swingTimer = new Timer(1000, e -> {
            secondes++;
            labelTimer.setText(String.format("%02d:%02d", secondes / 60, secondes % 60));
        });
        swingTimer.start();
    }

    private void raccrocher() {
        if (termine) return;
        termine = true;
        if (swingTimer != null) swingTimer.stop();
        client.quitterReunionGroupe(nomGroupe);
        arreterMedia();
        client.setFenetreReunionVideoActive(null);
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

    private void basculerCamera() {
        cameraOff = !cameraOff;
        if (cameraOff) {
            videoSender.arreter();
            boutonCamera.setText("📷 Activer cam");
            boutonCamera.setBackground(new Color(180, 100, 0));
            labelVideoLocal.setText("<html><center><font color='#555555'>Caméra désactivée</font></center></html>");
        } else {
            videoSender.demarrer();
            boutonCamera.setText("📷 Caméra");
            boutonCamera.setBackground(new Color(60, 60, 60));
        }
    }

    private void actualiserStatut() {
        int total = 1 + labelsDistants.size();
        labelStatut.setText(total + " participant(s)");
    }

    private static class ParticipantRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list,
                                                      Object value, int index, boolean isSelected, boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(
                    list, value, index, isSelected, cellHasFocus);
            label.setText("  🎥 " + value);
            label.setBorder(BorderFactory.createEmptyBorder(5, 6, 5, 6));
            label.setFont(new Font("Arial", Font.PLAIN, 12));
            if (!isSelected) {
                label.setBackground(new Color(30, 30, 30));
                label.setForeground(Color.WHITE);
            }
            return label;
        }
    }
}