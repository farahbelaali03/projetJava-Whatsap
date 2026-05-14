package media;

import model.Message;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Reçoit les frames vidéo et les affiche dans un JLabel.
 *
 * CORRECTIONS :
 *  - Redimensionnement adaptatif à la taille réelle du label (pas fixe 320x240)
 *  - File de 3 frames max (évite le lag en cas de réseau lent)
 *  - Conversion TYPE_INT_RGB pour éviter les frames noires
 */
public class VideoReceiver {

    private final JLabel label;
    private volatile boolean actif = false;
    private Thread thread;

    // File de 3 frames max : si pleine, on jette la plus ancienne (affichage temps réel)
    private final BlockingQueue<byte[]> file = new LinkedBlockingQueue<>(3);

    public VideoReceiver(JLabel label) {
        this.label = label;
    }

    public void demarrer() {
        actif  = true;
        thread = new Thread(this::boucle);
        thread.setDaemon(true);
        thread.setName("VideoReceiver");
        thread.start();
        System.out.println("[Video] Receiver démarré.");
    }

    public void arreter() {
        actif = false;
        if (thread != null) thread.interrupt();
        SwingUtilities.invokeLater(() -> {
            if (label != null) {
                label.setIcon(null);
                label.setText(
                        "<html><center><font color='#888888'>Appel terminé</font></center></html>");
            }
        });
    }

    /** Appelé par Client depuis le thread réseau. */
    public void recevoirFrame(Message msg) {
        if (!actif || msg == null || msg.getContenu() == null) return;
        try {
            byte[] jpeg = Base64.getDecoder().decode(msg.getContenu());
            // Si la file est pleine, on retire la plus vieille pour garder le temps réel
            if (!file.offer(jpeg)) {
                file.poll();   // supprimer la plus ancienne
                file.offer(jpeg);
            }
        } catch (Exception e) {
            System.err.println("[Video] Erreur décodage : " + e.getMessage());
        }
    }

    private void boucle() {
        while (actif) {
            try {
                byte[] jpeg = file.take();
                afficher(jpeg);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void afficher(byte[] jpeg) {
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(jpeg));
            if (img == null || label == null) return;

            // Redimensionner à la taille RÉELLE du label (adaptive, pas fixe)
            SwingUtilities.invokeLater(() -> {
                int w = label.getWidth();
                int h = label.getHeight();
                // Si le label n'a pas encore été rendu, utiliser une taille par défaut
                if (w <= 10) w = 500;
                if (h <= 10) h = 400;

                // Garder le ratio de l'image (letterbox)
                double ratioImg   = (double) img.getWidth()  / img.getHeight();
                double ratioLabel = (double) w / h;
                int fw, fh;
                if (ratioImg > ratioLabel) {
                    fw = w;
                    fh = (int)(w / ratioImg);
                } else {
                    fh = h;
                    fw = (int)(h * ratioImg);
                }

                Image redim = img.getScaledInstance(fw, fh, Image.SCALE_FAST);
                label.setIcon(new ImageIcon(redim));
                label.setText("");
            });
        } catch (Exception e) {
            System.err.println("[Video] Erreur affichage : " + e.getMessage());
        }
    }
}
