package media;

import client.Client;
import model.Message;
import model.TypeMessage;

import javax.sound.sampled.*;
import java.util.Base64;

/**
 * Capture du microphone et envoi des chunks audio via TCP.
 * Version corrigée : gestion robuste des erreurs et de l'arrêt/redémarrage.
 */
public class AudioSender {

    private static final int     TAILLE_BUFFER = 2048;
    private static final float   SAMPLE_RATE   = 44100.0f;
    private static final int     SAMPLE_SIZE   = 16;
    private static final int     CANAUX        = 1;
    private static final boolean SIGNE         = true;
    private static final boolean BIG_ENDIAN    = false;

    private final Client       client;
    private final String       destinataire;
    private final TypeMessage  typeAudio;
    private volatile boolean   actif  = false;
    private Thread             thread;
    private TargetDataLine     ligne;

    /** Constructeur par défaut : type AUDIO (appel vidéo). */
    public AudioSender(Client client, String destinataire) {
        this(client, destinataire, TypeMessage.AUDIO);
    }

    /** Constructeur avec type explicite (AUDIO, AUDIO_ONLY, GROUP_AUDIO). */
    public AudioSender(Client client, String destinataire, TypeMessage typeAudio) {
        this.client       = client;
        this.destinataire = destinataire;
        this.typeAudio    = typeAudio;
    }

    public void demarrer() {
        if (actif) return;
        actif  = true;
        thread = new Thread(this::boucle);
        thread.setDaemon(true);
        thread.setName("AudioSender");
        thread.start();
    }

    public void arreter() {
        actif = false;
        if (ligne != null && ligne.isOpen()) {
            ligne.stop();
            ligne.close();
        }
        if (thread != null) {
            thread.interrupt();
        }
    }

    private void boucle() {
        try {
            AudioFormat format = new AudioFormat(SAMPLE_RATE, SAMPLE_SIZE, CANAUX, SIGNE, BIG_ENDIAN);
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);

            if (!AudioSystem.isLineSupported(info)) {
                System.err.println("[Audio] Microphone non supporté sur ce système.");
                return;
            }

            ligne = (TargetDataLine) AudioSystem.getLine(info);
            ligne.open(format, TAILLE_BUFFER * 2);
            ligne.start();

            System.out.println("[Audio] Sender démarré -> " + destinataire);

            byte[] buffer = new byte[TAILLE_BUFFER];
            while (actif) {
                int n = ligne.read(buffer, 0, buffer.length);
                if (n > 0 && actif) envoyer(buffer, n);
            }

        } catch (LineUnavailableException e) {
            System.err.println("[Audio] Impossible d'ouvrir le microphone : " + e.getMessage());
        } catch (Exception e) {
            if (actif) System.err.println("[Audio] Erreur sender : " + e.getMessage());
        } finally {
            if (ligne != null && ligne.isOpen()) {
                ligne.stop();
                ligne.close();
            }
        }
    }

    private void envoyer(byte[] buffer, int n) {
        byte[] chunk = new byte[n];
        System.arraycopy(buffer, 0, chunk, 0, n);
        String b64 = Base64.getEncoder().encodeToString(chunk);
        client.envoyer(new Message(
                client.getNomUtilisateur(), destinataire, b64, typeAudio));
    }
}
