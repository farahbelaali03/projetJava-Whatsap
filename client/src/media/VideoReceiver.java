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

public class VideoReceiver {

    private final JLabel label;
    private volatile boolean actif = false;
    private Thread thread;

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

    public void recevoirFrame(Message msg) {
        if (!actif || msg == null || msg.getContenu() == null) return;
        try {
            byte[] jpeg = Base64.getDecoder().decode(msg.getContenu());
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

            SwingUtilities.invokeLater(() -> {
                int w = label.getWidth();
                int h = label.getHeight();
                if (w <= 10) w = 500;
                if (h <= 10) h = 400;

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
