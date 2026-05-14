package media;

import model.Message;

import javax.sound.sampled.*;
import java.util.Base64;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Reçoit les chunks audio et les joue via SourceDataLine.
 * Version corrigée : gestion robuste des erreurs audio.
 */
public class AudioReceiver {

    private static final float   SAMPLE_RATE = 44100.0f;
    private static final int     SAMPLE_SIZE = 16;
    private static final int     CANAUX      = 1;
    private static final boolean SIGNE       = true;
    private static final boolean BIG_ENDIAN  = false;

    private volatile boolean       actif  = false;
    private Thread                 thread;
    private SourceDataLine         ligne;
    private final BlockingQueue<byte[]> file = new LinkedBlockingQueue<>(50);

    public void demarrer() {
        if (actif) return; // déjà démarré
        try {
            AudioFormat format = new AudioFormat(SAMPLE_RATE, SAMPLE_SIZE, CANAUX, SIGNE, BIG_ENDIAN);
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);

            if (!AudioSystem.isLineSupported(info)) {
                System.err.println("[Audio] Haut-parleur non supporté sur ce système.");
                return;
            }

            ligne = (SourceDataLine) AudioSystem.getLine(info);
            ligne.open(format, 4096);
            ligne.start();
            actif = true;

            thread = new Thread(this::boucle);
            thread.setDaemon(true);
            thread.setName("AudioReceiver");
            thread.start();

            System.out.println("[Audio] Receiver démarré.");
        } catch (LineUnavailableException e) {
            System.err.println("[Audio] Impossible d'ouvrir le haut-parleur : " + e.getMessage());
        } catch (Exception e) {
            System.err.println("[Audio] Erreur démarrage receiver : " + e.getMessage());
        }
    }

    public void arreter() {
        actif = false;
        if (thread != null) thread.interrupt();
        if (ligne != null && ligne.isOpen()) {
            try {
                ligne.drain();
                ligne.stop();
                ligne.close();
            } catch (Exception ignored) {}
        }
    }

    public void recevoirChunk(Message msg) {
        if (!actif || msg == null || msg.getContenu() == null) return;
        try {
            byte[] chunk = Base64.getDecoder().decode(msg.getContenu());
            // Si la file est pleine, vider les vieux chunks (éviter le lag)
            if (!file.offer(chunk)) {
                file.clear();
                file.offer(chunk);
            }
        } catch (Exception e) {
            System.err.println("[Audio] Erreur décodage chunk : " + e.getMessage());
        }
    }

    private void boucle() {
        while (actif) {
            try {
                byte[] chunk = file.take();
                if (ligne != null && ligne.isOpen()) {
                    ligne.write(chunk, 0, chunk.length);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                System.err.println("[Audio] Erreur lecture chunk : " + e.getMessage());
            }
        }
    }
}
