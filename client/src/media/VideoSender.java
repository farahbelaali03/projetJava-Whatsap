package media;

import client.Client;
import model.Message;
import model.TypeMessage;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.Base64;

public class VideoSender {

    private static final int    LARGEUR   = 640;
    private static final int    HAUTEUR   = 480;
    private static final int    FPS       = 15;
    private static final int    DELAI_MS  = 1000 / FPS;
    private static final File   FRAME_FILE =
            new File(System.getProperty("java.io.tmpdir"), "webcam_frame.jpg");
    private static final File   LOCK_FILE  =
            new File(System.getProperty("java.io.tmpdir"), "webcam_lock.tmp");

    private final Client client;
    private final String destinataire;
    private final JLabel previewLocal;
    private final TypeMessage typeVideo;

    private volatile boolean actif = false;
    private Thread thread;
    private Object webcamRef = null;
    private boolean proprietaire = false;

    public VideoSender(Client client, String destinataire) {
        this(client, destinataire, TypeMessage.VIDEO, null);
    }

    public VideoSender(Client client, String destinataire, JLabel previewLocal) {
        this(client, destinataire, TypeMessage.VIDEO, previewLocal);
    }

    public VideoSender(Client client, String destinataire, TypeMessage typeVideo, JLabel previewLocal) {
        this.client       = client;
        this.destinataire = destinataire;
        this.typeVideo    = typeVideo;
        this.previewLocal = previewLocal;
    }

    public void demarrer() {
        if (actif) return;
        actif  = true;
        thread = new Thread(this::boucle);
        thread.setDaemon(true);
        thread.setName("VideoSender-" + client.getNomUtilisateur());
        thread.start();
        System.out.println("[Video] Sender démarré -> " + destinataire);
    }

    public void arreter() {
        actif = false;
        if (proprietaire) {
            fermerWebcam();
            LOCK_FILE.delete();
            proprietaire = false;
            if (FRAME_FILE.exists()) {
                FRAME_FILE.delete();
                System.out.println("[Video] Fichier partagé supprimé.");
            }
        }
        if (thread != null) thread.interrupt();
        System.out.println("[Video] Sender arrêté.");
    }

    private void boucle() {
        if (essayerOuvrirWebcam()) {
            proprietaire = true;
            System.out.println("[Video] Mode PROPRIÉTAIRE (écrit les frames).");
            boucleProprio();
        } else {
            System.out.println("[Video] Mode LECTEUR (lit les frames du fichier partagé).");
            boucleLecteur();
        }
    }


    private void boucleProprio() {
        int erreurs = 0;
        while (actif) {
            long t = System.currentTimeMillis();
            try {
                BufferedImage img = capturerFrame();
                if (img != null) {
                    erreurs = 0;
                    BufferedImage rgb = toRGB(img);
                    ecrireFichier(rgb);
                    afficherLocal(rgb);
                    envoyer(toJpeg(rgb));
                } else {
                    if (++erreurs > 10) break;
                }
                sleep(t);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                if (actif) System.err.println("[Video] Proprio frame : " + e.getMessage());
                if (++erreurs > 5) break;
            }
        }
        fermerWebcam();
        LOCK_FILE.delete();
        proprietaire = false;
        if (actif) boucleImageStatique();
    }

    private void boucleLecteur() {
        int attente = 0;
        while (!FRAME_FILE.exists() && attente < 30 && actif) {
            try { Thread.sleep(100); } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); return;
            }
            attente++;
        }

        if (!FRAME_FILE.exists()) {
            System.out.println("[Video] Fichier partagé absent → image statique.");
            boucleImageStatique();
            return;
        }

        while (actif) {
            long t = System.currentTimeMillis();
            try {
                byte[] jpeg = lireFichier();
                if (jpeg != null && jpeg.length > 0) {
                    // Afficher localement depuis le fichier
                    BufferedImage img = ImageIO.read(new ByteArrayInputStream(jpeg));
                    if (img != null) afficherLocal(img);
                    envoyer(jpeg);
                }
                sleep(t);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                if (actif) System.err.println("[Video] Lecteur : " + e.getMessage());
            }
        }
    }

    private void boucleImageStatique() {
        System.out.println("[Video] Fallback → image statique.");
        BufferedImage img = creerImageStatique();
        afficherLocal(img);
        byte[] jpeg = toJpeg(img);
        while (actif) {
            long t = System.currentTimeMillis();
            try {
                envoyer(jpeg);
                sleep(t);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private boolean essayerOuvrirWebcam() {
        // Si le fichier verrou existe → une autre JVM a déjà la webcam
        if (LOCK_FILE.exists()) {
            System.out.println("[Video] Webcam prise par une autre instance.");
            return false;
        }

        try {
            Class<?> cls = Class.forName("com.github.sarxos.webcam.Webcam");
            Object webcam = cls.getMethod("getDefault").invoke(null);
            if (webcam == null) {
                System.out.println("[Video] Aucune webcam détectée.");
                return false;
            }

            try {
                webcam.getClass()
                        .getMethod("setViewSize", Dimension.class)
                        .invoke(webcam, new Dimension(LARGEUR, HAUTEUR));
            } catch (Exception ignored) {}

            webcam.getClass().getMethod("open").invoke(webcam);

            Object isOpen = webcam.getClass().getMethod("isOpen").invoke(webcam);
            if (!Boolean.TRUE.equals(isOpen)) {
                System.out.println("[Video] Ouverture webcam échouée.");
                return false;
            }

            webcamRef = webcam;
            LOCK_FILE.createNewFile();
            System.out.println("[Video] ✅ Webcam ouverte (Sarxos 0.3.12).");
            return true;

        } catch (ClassNotFoundException e) {
            System.out.println("[Video] Sarxos absent.");
            return false;
        } catch (Exception e) {
            System.out.println("[Video] Webcam indisponible : " + e.getMessage());
            return false;
        }
    }

    private BufferedImage capturerFrame() {
        if (webcamRef == null) return null;
        try {
            return (BufferedImage) webcamRef.getClass()
                    .getMethod("getImage").invoke(webcamRef);
        } catch (Exception e) {
            return null;
        }
    }

    private void fermerWebcam() {
        if (webcamRef != null) {
            try { webcamRef.getClass().getMethod("close").invoke(webcamRef); }
            catch (Exception ignored) {}
            webcamRef = null;
        }
    }

    private void ecrireFichier(BufferedImage img) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "jpg", baos);
            File tmp = new File(FRAME_FILE.getParent(), "webcam_frame_tmp.jpg");
            Files.write(tmp.toPath(), baos.toByteArray(),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            Files.move(tmp.toPath(), FRAME_FILE.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            try {
                ImageIO.write(img, "jpg", FRAME_FILE);
            } catch (Exception ignored) {}
        }
    }

    private byte[] lireFichier() {
        try {
            return Files.readAllBytes(FRAME_FILE.toPath());
        } catch (Exception e) {
            return null;
        }
    }

    // ── Utilitaires ────────────────────────────────────────────

    private BufferedImage creerImageStatique() {
        BufferedImage img = new BufferedImage(LARGEUR, HAUTEUR, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(28, 32, 36));
        g.fillRect(0, 0, LARGEUR, HAUTEUR);
        int cx = LARGEUR / 2, cy = HAUTEUR / 2 - 20;
        g.setColor(new Color(75, 75, 75));
        g.fillRoundRect(cx - 55, cy - 25, 110, 65, 12, 12);
        g.setColor(new Color(55, 55, 55));
        g.fillOval(cx - 22, cy - 10, 44, 44);
        g.setColor(new Color(38, 38, 38));
        g.fillOval(cx - 13, cy - 1, 26, 26);
        g.setColor(new Color(200, 50, 50));
        g.setStroke(new BasicStroke(5, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(cx - 65, cy - 40, cx + 65, cy + 55);
        g.setFont(new Font("Arial", Font.BOLD, 15));
        g.setColor(new Color(180, 180, 180));
        FontMetrics fm = g.getFontMetrics();
        String t1 = "Caméra non disponible";
        g.drawString(t1, cx - fm.stringWidth(t1) / 2, cy + 65);
        g.setFont(new Font("Arial", Font.PLAIN, 12));
        g.setColor(new Color(110, 110, 110));
        fm = g.getFontMetrics();
        String t2 = client.getNomUtilisateur();
        g.drawString(t2, cx - fm.stringWidth(t2) / 2, cy + 88);
        g.dispose();
        return img;
    }

    private BufferedImage toRGB(BufferedImage src) {
        if (src == null) return null;
        if (src.getType() == BufferedImage.TYPE_INT_RGB) return src;
        BufferedImage dst = new BufferedImage(LARGEUR, HAUTEUR, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = dst.createGraphics();
        g.drawImage(src, 0, 0, LARGEUR, HAUTEUR, null);
        g.dispose();
        return dst;
    }

    private void afficherLocal(BufferedImage img) {
        if (previewLocal == null || img == null) return;
        SwingUtilities.invokeLater(() -> {
            int w = previewLocal.getWidth();
            int h = previewLocal.getHeight();
            if (w <= 10) w = LARGEUR;
            if (h <= 10) h = HAUTEUR;
            previewLocal.setIcon(new ImageIcon(img.getScaledInstance(w, h, Image.SCALE_FAST)));
            previewLocal.setText("");
        });
    }

    private byte[] toJpeg(BufferedImage img) {
        if (img == null) return null;
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            ImageIO.write(img, "jpg", b);
            return b.toByteArray();
        } catch (Exception e) { return null; }
    }

    private void envoyer(byte[] jpeg) {
        if (jpeg == null) return;
        client.envoyer(new Message(
                client.getNomUtilisateur(), destinataire,
                Base64.getEncoder().encodeToString(jpeg),
                typeVideo));  // VIDEO pour appel 1-à-1, GROUP_VIDEO pour réunion
    }

    private void sleep(long debut) throws InterruptedException {
        long w = DELAI_MS - (System.currentTimeMillis() - debut);
        if (w > 0) Thread.sleep(w);
    }
}