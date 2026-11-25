package huntersea;

import java.io.ByteArrayInputStream;
import java.util.concurrent.*;
import javax.sound.sampled.*;

/**
 * Bộ tổng hợp và phát âm thanh đại dương thời gian thực cho Hunter of the Sea.
 * Sử dụng Java Sound API với sóng âm tổng hợp (PCM Wave Synthesis),
 * không phụ thuộc file mp3/wav ngoài, đảm bảo 100% hoạt động mượt mà.
 */
public class SoundManager {
    private static SoundManager instance;

    private float volume = 0.8f; // 0.0f -> 1.0f
    private boolean muted = false;

    private final ExecutorService soundPool = Executors.newFixedThreadPool(4);

    private SoundManager() {}

    public static synchronized SoundManager getInstance() {
        if (instance == null) {
            instance = new SoundManager();
        }
        return instance;
    }

    public void setVolume(float vol) {
        this.volume = Math.max(0.0f, Math.min(1.0f, vol));
    }

    public float getVolume() {
        return volume;
    }

    public void volumeUp() {
        setVolume(volume + 0.1f);
    }

    public void volumeDown() {
        setVolume(volume - 0.1f);
    }

    public void setMuted(boolean muted) {
        this.muted = muted;
    }

    public boolean isMuted() {
        return muted;
    }

    public void toggleMute() {
        this.muted = !this.muted;
    }

    /**
     * Tiếng đớp mồi (Chomp / Pop)
     */
    public void playEat() {
        if (muted || volume <= 0) return;
        soundPool.submit(() -> {
            byte[] data = synthesizeToneSweep(450, 950, 0.08, 0.9f * volume, "sine");
            playBytes(data);
        });
    }

    /**
     * Tiếng lên cấp (Level Up Fanfare)
     */
    public void playLevelUp() {
        if (muted || volume <= 0) return;
        soundPool.submit(() -> {
            int[] notes = {523, 659, 784, 1046}; // C5, E5, G5, C6
            for (int note : notes) {
                byte[] data = synthesizeTone(note, 0.1, 0.85f * volume, "triangle");
                playBytes(data);
                try { Thread.sleep(70); } catch (Exception ignored) {}
            }
        });
    }

    /**
     * Tiếng nổ bom biển (Deep Underwater Explosion)
     */
    public void playBombExplode() {
        if (muted || volume <= 0) return;
        soundPool.submit(() -> {
            byte[] noise = synthesizeNoise(0.45, 1.0f * volume, true);
            playBytes(noise);
        });
    }

    /**
     * Tiếng kích hoạt kỹ năng Lướt / Búng (Whoosh Dash)
     */
    public void playDash() {
        if (muted || volume <= 0) return;
        soundPool.submit(() -> {
            byte[] data = synthesizeToneSweep(250, 800, 0.18, 0.7f * volume, "sine");
            playBytes(data);
        });
    }

    /**
     * Tiếng phóng điện tê liệt (Electric Zap)
     */
    public void playElectricShock() {
        if (muted || volume <= 0) return;
        soundPool.submit(() -> {
            byte[] data = synthesizeToneSweep(1200, 300, 0.25, 0.75f * volume, "saw");
            playBytes(data);
        });
    }

    /**
     * Tiếng phun mực đen (Ink Splash)
     */
    public void playInkSplash() {
        if (muted || volume <= 0) return;
        soundPool.submit(() -> {
            byte[] data = synthesizeNoise(0.3, 0.6f * volume, false);
            playBytes(data);
        });
    }

    /**
     * Tiếng nhặt Bá Khí / Nhặt Kỹ năng (Magic Ding)
     */
    public void playPowerUp() {
        if (muted || volume <= 0) return;
        soundPool.submit(() -> {
            int[] chord = {587, 880, 1174}; // D5, A5, D6
            for (int note : chord) {
                byte[] data = synthesizeTone(note, 0.12, 0.8f * volume, "sine");
                playBytes(data);
                try { Thread.sleep(50); } catch (Exception ignored) {}
            }
        });
    }

    /**
     * Tiếng va chạm / bị cắn trúng (Hit Hurt)
     */
    public void playHit() {
        if (muted || volume <= 0) return;
        soundPool.submit(() -> {
            byte[] data = synthesizeToneSweep(300, 100, 0.15, 0.9f * volume, "square");
            playBytes(data);
        });
    }

    /**
     * Tiếng Game Over (Game Over Descending)
     */
    public void playGameOver() {
        if (muted || volume <= 0) return;
        soundPool.submit(() -> {
            int[] notes = {400, 350, 300, 220};
            for (int note : notes) {
                byte[] data = synthesizeTone(note, 0.22, 0.85f * volume, "sine");
                playBytes(data);
                try { Thread.sleep(180); } catch (Exception ignored) {}
            }
        });
    }

    /**
     * Tiếng Chiến Thắng (Victory Fanfare)
     */
    public void playVictory() {
        if (muted || volume <= 0) return;
        soundPool.submit(() -> {
            int[] notes = {523, 523, 523, 659, 784, 1046};
            for (int note : notes) {
                byte[] data = synthesizeTone(note, 0.15, 0.9f * volume, "triangle");
                playBytes(data);
                try { Thread.sleep(120); } catch (Exception ignored) {}
            }
        });
    }

    // =========================================================================
    // THUẬT TOÁN TỔNG HỢP SÓNG ÂM THANH (SYNTHESIS ALGORITHMS)
    // =========================================================================

    private byte[] synthesizeTone(double freq, double durationSec, float vol, String waveType) {
        float sampleRate = 22050f;
        int numSamples = (int) (durationSec * sampleRate);
        byte[] buffer = new byte[numSamples];

        for (int i = 0; i < numSamples; i++) {
            double time = i / (double) sampleRate;
            double sample = 0;
            double phase = 2 * Math.PI * freq * time;

            if ("triangle".equals(waveType)) {
                sample = (2.0 / Math.PI) * Math.asin(Math.sin(phase));
            } else if ("square".equals(waveType)) {
                sample = Math.sin(phase) >= 0 ? 0.7 : -0.7;
            } else {
                sample = Math.sin(phase);
            }

            // Envelope decay (giảm dần âm lượng)
            double envelope = 1.0 - (i / (double) numSamples);
            sample *= envelope * vol;

            buffer[i] = (byte) (sample * 127);
        }
        return buffer;
    }

    private byte[] synthesizeToneSweep(double startFreq, double endFreq, double durationSec, float vol, String waveType) {
        float sampleRate = 22050f;
        int numSamples = (int) (durationSec * sampleRate);
        byte[] buffer = new byte[numSamples];
        double phase = 0;

        for (int i = 0; i < numSamples; i++) {
            double t = i / (double) numSamples;
            double currentFreq = startFreq + (endFreq - startFreq) * t;
            phase += 2 * Math.PI * currentFreq / sampleRate;

            double sample;
            if ("saw".equals(waveType)) {
                sample = 2.0 * ((phase / (2 * Math.PI)) - Math.floor(0.5 + (phase / (2 * Math.PI))));
            } else if ("square".equals(waveType)) {
                sample = Math.sin(phase) >= 0 ? 0.8 : -0.8;
            } else {
                sample = Math.sin(phase);
            }

            double envelope = 1.0 - t;
            sample *= envelope * vol;
            buffer[i] = (byte) (sample * 127);
        }
        return buffer;
    }

    private byte[] synthesizeNoise(double durationSec, float vol, boolean lowPass) {
        float sampleRate = 22050f;
        int numSamples = (int) (durationSec * sampleRate);
        byte[] buffer = new byte[numSamples];
        double lastSample = 0;

        for (int i = 0; i < numSamples; i++) {
            double white = (Math.random() * 2.0 - 1.0);
            if (lowPass) {
                // Bộ lọc thông thấp tạo tiếng nổ trầm dưới nước
                white = lastSample * 0.85 + white * 0.15;
                lastSample = white;
            }
            double envelope = Math.pow(1.0 - (i / (double) numSamples), 1.5);
            double sample = white * envelope * vol;
            buffer[i] = (byte) (sample * 127);
        }
        return buffer;
    }

    private void playBytes(byte[] audioData) {
        try {
            AudioFormat format = new AudioFormat(22050f, 8, 1, true, false);
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
            if (!AudioSystem.isLineSupported(info)) return;

            SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info);
            line.open(format);
            line.start();
            line.write(audioData, 0, audioData.length);
            line.drain();
            line.close();
        } catch (Exception ignored) {}
    }
}
