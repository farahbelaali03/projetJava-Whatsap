package media;

import javax.sound.sampled.*;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

public class VoiceRecorder {

    private static final AudioFormat FORMAT = new AudioFormat(44100f, 16, 1, true, false);

    private TargetDataLine line;
    private ByteArrayOutputStream buffer;
    private Thread recordThread;
    private volatile boolean recording = false;

    public void startRecording() throws LineUnavailableException {
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, FORMAT);
        if (!AudioSystem.isLineSupported(info)) {
            throw new LineUnavailableException("Microphone non supporté sur ce système");
        }
        line = (TargetDataLine) AudioSystem.getLine(info);
        line.open(FORMAT);
        line.start();
        buffer = new ByteArrayOutputStream();
        recording = true;
        recordThread = new Thread(() -> {
            byte[] chunk = new byte[4096];
            while (recording) {
                int read = line.read(chunk, 0, chunk.length);
                if (read > 0) buffer.write(chunk, 0, read);
            }
        });
        recordThread.setDaemon(true);
        recordThread.start();
    }

    public String stopRecording() {
        recording = false;
        if (line != null) { line.stop(); line.close(); }
        byte[] raw = buffer.toByteArray();
        if (raw.length == 0) return null;
        byte[] wav = addWavHeader(raw);
        return Base64.getEncoder().encodeToString(wav);
    }

    private byte[] addWavHeader(byte[] raw) {
        int dataLen = raw.length;
        int totalLen = dataLen + 36;
        byte[] wav = new byte[totalLen + 8];
        System.arraycopy("RIFF".getBytes(), 0, wav, 0, 4);
        intToLE(totalLen, wav, 4);
        System.arraycopy("WAVE".getBytes(), 0, wav, 8, 4);
        System.arraycopy("fmt ".getBytes(), 0, wav, 12, 4);
        intToLE(16, wav, 16);
        shortToLE((short) 1, wav, 20);
        shortToLE((short) 1, wav, 22);
        intToLE(44100, wav, 24);
        intToLE(88200, wav, 28);
        shortToLE((short) 2, wav, 32);
        shortToLE((short) 16, wav, 34);
        System.arraycopy("data".getBytes(), 0, wav, 36, 4);
        intToLE(dataLen, wav, 40);
        System.arraycopy(raw, 0, wav, 44, dataLen);
        return wav;
    }

    private void intToLE(int v, byte[] b, int off) {
        b[off] = (byte)(v); b[off+1] = (byte)(v>>8);
        b[off+2] = (byte)(v>>16); b[off+3] = (byte)(v>>24);
    }

    private void shortToLE(short v, byte[] b, int off) {
        b[off] = (byte)(v); b[off+1] = (byte)(v>>8);
    }
}