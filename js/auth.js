/**
 * HUNTER OF THE SEA - AUTHENTICATION & USER SESSION MANAGER (PHASE 1)
 * Quản lý phiên đăng nhập/đăng ký của người chơi, phân biệt Player A & Player B giữa các Tab.
 */

const AuthManager = (function () {
    const SESSION_KEY = 'hos_session_current_user';
    const DEFAULT_LOCAL_USER = 'hos_default_username';

    let currentUser = null;
    const authChangeListeners = new Set();

    function safeGetSession(key) {
        try { return sessionStorage.getItem(key); } catch (e) { return null; }
    }
    function safeSetSession(key, val) {
        try { sessionStorage.setItem(key, val); } catch (e) {}
    }
    function safeGetLocal(key) {
        try { return localStorage.getItem(key); } catch (e) { return null; }
    }
    function safeSetLocal(key, val) {
        try { localStorage.setItem(key, val); } catch (e) {}
    }

    // Khởi tạo người chơi từ sessionStorage (độc lập giữa các Tab để dễ test A và B)
    function init() {
        try {
            const savedSession = safeGetSession(SESSION_KEY);
            if (savedSession) {
                currentUser = JSON.parse(savedSession);
            } else {
                // Nếu chưa có trong tab này, kiểm tra localStorage mặc định
                const defaultName = safeGetLocal(DEFAULT_LOCAL_USER) || 'Player A';
                login(defaultName);
            }
        } catch (e) {
            login('Player A');
        }
        updateProfileUI();
    }

    async function login(username) {
        const cleanName = (username || '').trim() || 'Thủy Thủ Săn Mồi';
        try {
            const user = await window.GameApi.loginUser(cleanName);
            currentUser = {
                userId: user.userId || 1,
                username: user.username || cleanName,
                createdAt: user.createdAt || new Date().toISOString()
            };
            safeSetSession(SESSION_KEY, JSON.stringify(currentUser));
            safeSetLocal(DEFAULT_LOCAL_USER, currentUser.username);
            updateProfileUI();
            notifyListeners();
            return currentUser;
        } catch (err) {
            console.error('[AuthManager] Đăng nhập lỗi, dùng local fallback:', err);
            currentUser = {
                userId: Math.floor(Math.random() * 1000) + 1,
                username: cleanName,
                createdAt: new Date().toISOString()
            };
            safeSetSession(SESSION_KEY, JSON.stringify(currentUser));
            safeSetLocal(DEFAULT_LOCAL_USER, currentUser.username);
            updateProfileUI();
            notifyListeners();
            return currentUser;
        }
    }

    function notifyListeners() {
        authChangeListeners.forEach(cb => {
            try { cb(currentUser); } catch (e) {}
        });
    }

    function updateProfileUI() {
        const nameEl = document.getElementById('lobby-username') || document.querySelector('.player-title');
        if (nameEl && currentUser) {
            nameEl.innerText = currentUser.username;
        }

        const scoreEl = document.getElementById('lobby-high-score');
        if (scoreEl) {
            const localHigh = safeGetLocal(`hos_score_${currentUser ? currentUser.username : 'default'}`) || '0';
            scoreEl.innerText = localHigh;
        }
    }

    return {
        init,
        getCurrentUser() {
            if (!currentUser) {
                init();
            }
            return currentUser;
        },
        async login(username) {
            return await login(username);
        },
        onAuthChange(callback) {
            if (typeof callback === 'function') {
                authChangeListeners.add(callback);
            }
        },
        updateProfileUI
    };
})();

window.AuthManager = AuthManager;
