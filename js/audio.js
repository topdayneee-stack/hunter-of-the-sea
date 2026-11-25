/**
 * HUNTER OF THE SEA - SOUND MANAGER & AUDIO SYNTHESIZER
 * Bám sát tài liệu thiết kế SoundManager.java:
 * - Tổng hợp âm thanh đại dương thời gian thực bằng Web Audio API (PCM Wave Synthesis)
 * - Quản lý âm lượng tổng (Master Volume: 0% - 100%), Tăng/Giảm (+/-), Bật/Tắt tiếng (Mute)
 * - Tự động lưu & phục hồi trạng thái âm lượng qua localStorage ('hunter_volume', 'hunter_muted')
 * - Phát đầy đủ âm thanh trò chơi: Ăn mồi (Chomp/Pop), Thăng cấp (Level Up), Thi triển chiêu thức (Skill), Bom nổ, Click nút, Thua trận
 */

class SoundManagerClass {
    constructor() {
        this.ctx = null;
        this.masterGain = null;
        this.volume = 0.8;
        this.muted = false;
        this.listeners = [];

        // Khôi phục cài đặt âm lượng từ localStorage
        try {
            const savedVol = localStorage.getItem('hunter_volume');
            if (savedVol !== null) {
                this.volume = Math.max(0.0, Math.min(1.0, parseFloat(savedVol)));
            }
            const savedMute = localStorage.getItem('hunter_muted');
            if (savedMute !== null) {
                this.muted = (savedMute === 'true');
            }
        } catch (e) {
            console.warn('[SoundManager] LocalStorage không khả dụng:', e);
        }
    }

    init() {
        if (this.ctx) return;
        try {
            const AudioContext = window.AudioContext || window.webkitAudioContext;
            if (!AudioContext) return;
            this.ctx = new AudioContext();
            this.masterGain = this.ctx.createGain();
            this.masterGain.connect(this.ctx.destination);
            this.applyVolume();
        } catch (e) {
            console.warn('[SoundManager] Không thể khởi tạo AudioContext:', e);
        }
    }

    ensureContext() {
        if (!this.ctx) {
            this.init();
        }
        if (this.ctx && this.ctx.state === 'suspended') {
            this.ctx.resume();
        }
    }

    applyVolume() {
        if (!this.masterGain || !this.ctx) return;
        const targetGain = this.muted ? 0.0 : this.volume;
        this.masterGain.gain.setValueAtTime(targetGain, this.ctx.currentTime);
    }

    getVolume() {
        return this.volume;
    }

    setVolume(vol) {
        this.volume = Math.max(0.0, Math.min(1.0, vol));
        if (this.muted && this.volume > 0) {
            this.muted = false;
        }
        this.applyVolume();
        this.saveState();
        this.notifyListeners();
    }

    volumeUp() {
        this.setVolume(Math.min(1.0, this.volume + 0.1));
    }

    volumeDown() {
        this.setVolume(Math.max(0.0, this.volume - 0.1));
    }

    isMuted() {
        return this.muted;
    }

    toggleMute() {
        this.muted = !this.muted;
        this.applyVolume();
        this.saveState();
        this.notifyListeners();
    }

    saveState() {
        try {
            localStorage.setItem('hunter_volume', this.volume.toFixed(2));
            localStorage.setItem('hunter_muted', this.muted ? 'true' : 'false');
        } catch (e) {}
    }

    addListener(fn) {
        if (typeof fn === 'function' && !this.listeners.includes(fn)) {
            this.listeners.push(fn);
        }
    }

    notifyListeners() {
        this.listeners.forEach(fn => {
            try { fn(this.volume, this.muted); } catch (e) {}
        });
    }

    // =========================================================================
    // TỔNG HỢP ÂM THANH GAME REALTIME (SYNTHESIS)
    // =========================================================================

    /**
     * Tiếng đớp cá mồi (Chomp / Pop giọt nước)
     */
    playEat() {
        if (this.muted || this.volume <= 0) return;
        this.ensureContext();
        if (!this.ctx) return;

        const osc = this.ctx.createOscillator();
        const gain = this.ctx.createGain();

        osc.type = 'sine';
        const now = this.ctx.currentTime;
        // Bắt đầu từ 240Hz lướt vọt lên 480Hz rồi rơi nhanh (hiệu ứng pop nước)
        osc.frequency.setValueAtTime(220, now);
        osc.frequency.exponentialRampToValueAtTime(540, now + 0.04);
        osc.frequency.exponentialRampToValueAtTime(140, now + 0.12);

        gain.gain.setValueAtTime(0.4, now);
        gain.gain.exponentialRampToValueAtTime(0.001, now + 0.12);

        osc.connect(gain);
        gain.connect(this.masterGain);

        osc.start(now);
        osc.stop(now + 0.13);
    }

    /**
     * Tiếng thăng cấp tiến hóa (Level Up Chime)
     */
    playLevelUp() {
        if (this.muted || this.volume <= 0) return;
        this.ensureContext();
        if (!this.ctx) return;

        const now = this.ctx.currentTime;
        const notes = [329.63, 440.0, 554.37, 659.25, 880.0]; // E4, A4, C#5, E5, A5
        notes.forEach((freq, idx) => {
            const osc = this.ctx.createOscillator();
            const gain = this.ctx.createGain();

            osc.type = 'triangle';
            osc.frequency.setValueAtTime(freq, now + idx * 0.06);

            gain.gain.setValueAtTime(0, now + idx * 0.06);
            gain.gain.linearRampToValueAtTime(0.35, now + idx * 0.06 + 0.02);
            gain.gain.exponentialRampToValueAtTime(0.001, now + idx * 0.06 + 0.35);

            osc.connect(gain);
            gain.connect(this.masterGain);

            osc.start(now + idx * 0.06);
            osc.stop(now + idx * 0.06 + 0.36);
        });
    }

    /**
     * Tiếng thi triển kỹ năng [Q] (Whoosh / Energy Zap)
     */
    playSkill(charId) {
        if (this.muted || this.volume <= 0) return;
        this.ensureContext();
        if (!this.ctx) return;

        const now = this.ctx.currentTime;
        const osc = this.ctx.createOscillator();
        const gain = this.ctx.createGain();

        if (charId === 6) {
            // Cá đuối điện: Tiếng rè xung điện saw-tooth
            osc.type = 'sawtooth';
            osc.frequency.setValueAtTime(180, now);
            osc.frequency.linearRampToValueAtTime(420, now + 0.15);
            gain.gain.setValueAtTime(0.3, now);
            gain.gain.exponentialRampToValueAtTime(0.001, now + 0.25);
        } else {
            // Kỹ năng lướt/bám/khiên: Tiếng gió nước Whoosh
            osc.type = 'sine';
            osc.frequency.setValueAtTime(120, now);
            osc.frequency.exponentialRampToValueAtTime(380, now + 0.08);
            osc.frequency.exponentialRampToValueAtTime(80, now + 0.28);
            gain.gain.setValueAtTime(0.45, now);
            gain.gain.exponentialRampToValueAtTime(0.001, now + 0.28);
        }

        osc.connect(gain);
        gain.connect(this.masterGain);

        osc.start(now);
        osc.stop(now + 0.3);
    }

    /**
     * Tiếng bom nổ / va chạm
     */
    playBomb() {
        if (this.muted || this.volume <= 0) return;
        this.ensureContext();
        if (!this.ctx) return;

        const now = this.ctx.currentTime;
        const osc = this.ctx.createOscillator();
        const gain = this.ctx.createGain();

        osc.type = 'square';
        osc.frequency.setValueAtTime(120, now);
        osc.frequency.exponentialRampToValueAtTime(30, now + 0.35);

        gain.gain.setValueAtTime(0.5, now);
        gain.gain.exponentialRampToValueAtTime(0.001, now + 0.4);

        osc.connect(gain);
        gain.connect(this.masterGain);

        osc.start(now);
        osc.stop(now + 0.42);
    }

    /**
     * Tiếng click nút giao diện
     */
    playClick() {
        if (this.muted || this.volume <= 0) return;
        this.ensureContext();
        if (!this.ctx) return;

        const now = this.ctx.currentTime;
        const osc = this.ctx.createOscillator();
        const gain = this.ctx.createGain();

        osc.type = 'sine';
        osc.frequency.setValueAtTime(600, now);
        osc.frequency.exponentialRampToValueAtTime(300, now + 0.04);

        gain.gain.setValueAtTime(0.18, now);
        gain.gain.exponentialRampToValueAtTime(0.001, now + 0.04);

        osc.connect(gain);
        gain.connect(this.masterGain);

        osc.start(now);
        osc.stop(now + 0.05);
    }

    /**
     * Tiếng thua trận Game Over
     */
    playGameOver() {
        if (this.muted || this.volume <= 0) return;
        this.ensureContext();
        if (!this.ctx) return;

        const now = this.ctx.currentTime;
        const notes = [311.13, 293.66, 277.18, 261.63]; // Eb4, D4, C#4, C4
        notes.forEach((freq, idx) => {
            const osc = this.ctx.createOscillator();
            const gain = this.ctx.createGain();

            osc.type = 'sawtooth';
            osc.frequency.setValueAtTime(freq, now + idx * 0.12);

            gain.gain.setValueAtTime(0, now + idx * 0.12);
            gain.gain.linearRampToValueAtTime(0.25, now + idx * 0.12 + 0.02);
            gain.gain.exponentialRampToValueAtTime(0.001, now + idx * 0.12 + 0.4);

            osc.connect(gain);
            gain.connect(this.masterGain);

            osc.start(now + idx * 0.12);
            osc.stop(now + idx * 0.12 + 0.42);
        });
    }
}

// Khởi tạo Singleton
const soundManagerInstance = new SoundManagerClass();
window.SoundManager = soundManagerInstance;
window.GameAudio = soundManagerInstance;

// Kích hoạt AudioContext khi người dùng tương tác lần đầu
window.addEventListener('click', () => {
    soundManagerInstance.ensureContext();
}, { once: true });
window.addEventListener('keydown', () => {
    soundManagerInstance.ensureContext();
}, { once: true });
