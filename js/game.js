/**
 * HUNTER OF THE SEA - GAME LOOP & REALTIME PVP CONTROLLER (PHASE 2)
 * Vòng lặp 60 FPS Canvas 2D, điều khiển chuột Mouse-Aim,
 * Camera mượt mà, Đồng bộ vị trí 2 người chơi & Xử lý va chạm sinh tử.
 */

const GameController = (function () {
    const MAP_WIDTH = 1920;
    const MAP_HEIGHT = 1080;

    let canvas = null;
    let ctx = null;
    let minimapCanvas = null;
    let minimapCtx = null;

    // Ảnh nền bản đồ đại dương chuẩn map.png (với fallback map 1.png)
    const mapImg = new Image();
    mapImg.src = 'map.png';
    mapImg.onerror = () => {
        if (!mapImg.src.includes('map%201.png') && !mapImg.src.includes('map 1.png')) {
            mapImg.src = 'map 1.png';
        }
    };

    let localPlayer = null;
    let remotePlayer = null;
    let entityManager = null;
    let realtimeChannel = null;

    let isRunning = false;
    let isPaused = false;
    let isHost = false;
    let isPvE = false;
    let currentGameMode = 'PVE'; // 'CLASSIC', 'PVE', 'TIMED', 'PVP'
    let timeRemaining = 180; // Đếm ngược 180s cho chế độ TIMED
    let lastTime = 0;
    let syncTimer = 0;
    let lastBumpTime = 0;

    // Thuật toán hỗ trợ người chơi & Cân bằng động DDA (Dynamic Difficulty Adjustment) trong PvE
    let pveDifficulty = 'MEDIUM'; // 'EASY' | 'MEDIUM' | 'HARD'
    let pveGameTime = 0.0;
    let ddaCheckTimer = 0.0;
    let ddaStatus = 'BALANCED'; // 'ASSISTING' | 'BALANCED' | 'CHALLENGING'
    let ddaFishSpeedMult = 1.0;
    let ddaSpawnNearTimer = 0.0;

    // Rematch & Quản lý kết nối (Phase 5)
    let rematchRequested = false;
    let remoteRematchRequested = false;
    let activeRoom = null;
    let currentUserRef = null;

    // Tối ưu hóa gói tin di chuyển (Delta check)
    let lastSentX = -9999;
    let lastSentY = -9999;
    let lastSentAngle = -9999;

    // Camera
    let cameraX = 0;
    let cameraY = 0;

    // Tọa độ chuột trong màn hình & thế giới
    let mouseScreenX = 0;
    let mouseScreenY = 0;
    let worldMouseX = MAP_WIDTH / 2;
    let worldMouseY = MAP_HEIGHT / 2;

    function init() {
        canvas = document.getElementById('game-canvas');
        if (!canvas) return;
        ctx = canvas.getContext('2d');

        minimapCanvas = document.getElementById('minimap-canvas');
        if (minimapCanvas) minimapCtx = minimapCanvas.getContext('2d');

        resizeCanvas();
        window.addEventListener('resize', resizeCanvas);

        // LẮNG NGHE CHUỘT (MOUSE-AIM)
        window.addEventListener('mousemove', (e) => {
            mouseScreenX = e.clientX;
            mouseScreenY = e.clientY;
            updateWorldMouse();
        });

        // LẮNG NGHE BÀN PHÍM: KÍCH HOẠT KỸ NĂNG [Q], [W], [E] & PHÍM [ESC] TẠM DỪNG
        window.addEventListener('keydown', (e) => {
            if (e.code === 'Escape') {
                if (isRunning) {
                    togglePause();
                }
                return;
            }

            if (!isRunning || !localPlayer) return;
            if (e.code === 'KeyQ') {
                triggerSkillQ();
            } else if (e.code === 'KeyW') {
                triggerSkillW();
            } else if (e.code === 'KeyE') {
                triggerSkillE();
            }
        });

        // LẮNG NGHE CLICK CHUỘT VÀO 3 Ô KỸ NĂNG HUD
        const slotQ = document.getElementById('slot-q');
        if (slotQ) slotQ.addEventListener('click', triggerSkillQ);

        const slotW = document.getElementById('slot-w');
        if (slotW) slotW.addEventListener('click', triggerSkillW);

        const slotE = document.getElementById('slot-e');
        if (slotE) slotE.addEventListener('click', triggerSkillE);

        // Nút Tạm dừng / Mở bảng Tạm dừng (PauseDialog)
        const btnPause = document.getElementById('btn-pause');
        if (btnPause) {
            btnPause.addEventListener('click', () => {
                togglePause(true);
            });
        }

        const btnResume = document.getElementById('btn-resume');
        if (btnResume) {
            btnResume.addEventListener('click', () => {
                togglePause(false);
            });
        }

        // Điều khiển Âm lượng trong Bảng Tạm dừng (SoundManager)
        initPauseVolumeControls();

        const btnQuitLobby = document.getElementById('btn-quit-lobby');
        if (btnQuitLobby) {
            btnQuitLobby.addEventListener('click', () => {
                if (window.SoundManager) window.SoundManager.playClick();
                togglePause(false);
                stopGame();
                returnToLobby();
            });
        }

        const btnLobbyFromOver = document.getElementById('btn-lobby-from-over');
        if (btnLobbyFromOver) {
            btnLobbyFromOver.addEventListener('click', () => {
                if (window.SoundManager) window.SoundManager.playClick();
                const modalGameOver = document.getElementById('modal-gameover');
                if (modalGameOver) modalGameOver.classList.add('hidden');
                returnToLobby();
            });
        }

        // NÚT CHƠI LẠI / ĐẤU LẠI (PHASE 5)
        const btnRetry = document.getElementById('btn-retry');
        if (btnRetry) {
            btnRetry.addEventListener('click', () => {
                if (window.SoundManager) window.SoundManager.playClick();
                handleRematchClick();
            });
        }

        const btnRestartPause = document.getElementById('btn-restart-pause');
        if (btnRestartPause) {
            btnRestartPause.addEventListener('click', () => {
                if (window.SoundManager) window.SoundManager.playClick();
                togglePause(false);

                if (currentGameMode !== 'PVP') {
                    startSoloMatch(currentGameMode, localPlayer ? localPlayer.character : null, currentUserRef, pveDifficulty);
                } else {
                    if (window.showToast) window.showToast('⚠️ Đang trong trận PvP online, không thể tự chơi lại!');
                }
            });
        }

        // Tự động thông báo cho đối thủ khi đóng tab hoặc reload
        window.addEventListener('beforeunload', () => {
            if (isRunning && currentGameMode === 'PVP' && realtimeChannel && localPlayer) {
                realtimeChannel.send('PLAYER_DISCONNECTED', {
                    leaverId: localPlayer.id,
                    leaverName: localPlayer.username
                });
            }
        });
    }

    function resizeCanvas() {
        if (!canvas) return;
        canvas.width = window.innerWidth;
        canvas.height = window.innerHeight;
    }

    function updateWorldMouse() {
        worldMouseX = mouseScreenX + cameraX;
        worldMouseY = mouseScreenY + cameraY;
    }

    // =========================================================================
    // QUẢN LÝ TẠM DỪNG (PAUSE) & ÂM LƯỢNG (PauseDialog & SoundManager)
    // =========================================================================
    function togglePause(forcedState) {
        if (!isRunning) return;

        const modalPause = document.getElementById('modal-pause');
        const shouldPause = (typeof forcedState === 'boolean') ? forcedState : !isPaused;

        if (shouldPause) {
            // Đối với chế độ đơn / PvE: đóng băng update loop
            if (currentGameMode !== 'PVP') {
                isPaused = true;
            } else {
                // PvP không đóng băng kết nối realtime, trận đấu tiếp tục
                isPaused = false;
            }

            if (modalPause) {
                modalPause.classList.remove('hidden');
                updatePauseModalUI();
            }
            if (window.SoundManager) window.SoundManager.playClick();
        } else {
            isPaused = false;
            lastTime = performance.now(); // Tránh giật đứt quãng thời gian
            if (modalPause) modalPause.classList.add('hidden');
            if (window.SoundManager) window.SoundManager.playClick();
        }
    }

    function updatePauseModalUI() {
        const badge = document.getElementById('pause-mode-badge');
        const pvpNotice = document.getElementById('pause-pvp-notice');

        if (badge) {
            const modeNames = {
                'CLASSIC': '🌟 CƠ BẢN (ĐẠT LV.4)',
                'TIMED': '⏱️ ĐUA ĐIỂM (3 PHÚT)',
                'PVE': `🤖 ĐẤU MÁY PvE (${pveDifficulty === 'EASY' ? 'DỄ' : (pveDifficulty === 'HARD' ? 'KHÓ' : 'VỪA')})`,
                'PVP': '⚔️ ĐỐI KHÁNG ONLINE PvP'
            };
            badge.innerText = modeNames[currentGameMode] || currentGameMode;
        }

        if (pvpNotice) {
            if (currentGameMode === 'PVP') {
                pvpNotice.classList.remove('hidden');
            } else {
                pvpNotice.classList.add('hidden');
            }
        }

        syncVolumeUI();
    }

    function syncVolumeUI() {
        if (!window.SoundManager) return;
        const slider = document.getElementById('pause-vol-slider');
        const text = document.getElementById('pause-vol-text');
        const muteBtn = document.getElementById('btn-vol-mute');

        const vol = window.SoundManager.getVolume();
        const isMuted = window.SoundManager.isMuted();

        if (slider) slider.value = vol;
        if (text) text.innerText = isMuted ? 'MUTE (0%)' : `${vol}%`;

        if (muteBtn) {
            if (isMuted) {
                muteBtn.classList.add('is-muted');
                muteBtn.innerText = '🔇';
                muteBtn.title = 'Bật lại âm thanh';
            } else {
                muteBtn.classList.remove('is-muted');
                muteBtn.innerText = '🔊';
                muteBtn.title = 'Tắt tiếng';
            }
        }
    }

    function initPauseVolumeControls() {
        const slider = document.getElementById('pause-vol-slider');
        const btnUp = document.getElementById('btn-vol-up');
        const btnDown = document.getElementById('btn-vol-down');
        const muteBtn = document.getElementById('btn-vol-mute');

        if (slider) {
            slider.addEventListener('input', (e) => {
                const val = parseInt(e.target.value, 10);
                if (window.SoundManager) {
                    window.SoundManager.setVolume(val);
                    syncVolumeUI();
                }
            });
        }

        if (btnUp) {
            btnUp.addEventListener('click', () => {
                if (window.SoundManager) {
                    window.SoundManager.volumeUp();
                    window.SoundManager.playClick();
                    syncVolumeUI();
                }
            });
        }

        if (btnDown) {
            btnDown.addEventListener('click', () => {
                if (window.SoundManager) {
                    window.SoundManager.volumeDown();
                    window.SoundManager.playClick();
                    syncVolumeUI();
                }
            });
        }

        if (muteBtn) {
            muteBtn.addEventListener('click', () => {
                if (window.SoundManager) {
                    window.SoundManager.toggleMute();
                    window.SoundManager.playClick();
                    syncVolumeUI();
                }
            });
        }
    }

    // =========================================================================
    // KÍCH HOẠT KỸ NĂNG: PHÍM [Q] (MẶC ĐỊNH) VÀ PHÍM [W] & [E] (NHẶT ĐƯỢC)
    // =========================================================================
    function triggerSkillQ() {
        if (!isRunning || !localPlayer || localPlayer.dead) return;

        if (!localPlayer.canUseSkillQ()) {
            if (localPlayer.mana < 5.0) {
                if (window.showToast) window.showToast(`⚠️ Chưa đủ Mana! (${localPlayer.mana.toFixed(1)}/5.0). Hãy ăn thêm cá mồi để nạp đầy!`);
            } else if (localPlayer.stunTimer > 0) {
                if (window.showToast) window.showToast('⚡ Đang bị choáng, không thể thi triển kỹ năng!');
            }
            return;
        }

        const res = localPlayer.useSkillQ(worldMouseX, worldMouseY);
        if (res) {
            // Cá Ép chủ động tách khỏi cá chủ
            if (res.skillKey === 'Q_DETACH') {
                if (window.showToast) window.showToast('🚀 Đã tách khỏi cá chủ!');
                if (realtimeChannel) {
                    realtimeChannel.send('SKILL_TRIGGER', {
                        key: 'Q_DETACH',
                        charId: 4,
                        parasiteId: localPlayer.id
                    });
                }
                return;
            }

            if (entityManager) {
                entityManager.spawnBubbles(localPlayer.getCenterX(), localPlayer.getCenterY(), 20, '#ffd700');
            }

            if (window.SoundManager) {
                window.SoundManager.playSkill(res.charId);
            }

            // Đồng bộ trạng thái/hiệu ứng kỹ năng qua kênh realtime
            if (realtimeChannel) {
                realtimeChannel.send('SKILL_TRIGGER', {
                    key: 'Q',
                    charId: res.charId,
                    fromX: res.fromX,
                    fromY: res.fromY,
                    toX: res.toX,
                    toY: res.toY,
                    targetX: Math.round(res.targetX || worldMouseX),
                    targetY: Math.round(res.targetY || worldMouseY),
                    angle: Number((res.angle || localPlayer.angle).toFixed(3))
                });
            }

            const skillTitle = (localPlayer.character && localPlayer.character.skillName) || 'Thủy Thần';
            if (window.showToast) window.showToast(`💥 Kích hoạt chiêu [Q]: ${skillTitle}!`);
        }
    }

    function triggerSkillW() {
        if (!isRunning || !localPlayer || localPlayer.dead) return;

        if (!localPlayer.slotW) {
            if (window.showToast) window.showToast('ℹ️ Ô kỹ năng [W] đang trống! Hãy nhặt ngọc kỹ năng trên biển.');
            return;
        }

        if (localPlayer.stunTimer > 0) {
            if (window.showToast) window.showToast('⚡ Đang bị choáng, không thể thi triển kỹ năng!');
            return;
        }

        const cx = localPlayer.getCenterX();
        const cy = localPlayer.getCenterY();
        let targetX = worldMouseX;
        let targetY = worldMouseY;
        if (Math.hypot(targetX - cx, targetY - cy) < 8) {
            targetX = cx + Math.cos(localPlayer.angle) * 120;
            targetY = cy + Math.sin(localPlayer.angle) * 120;
        }

        const res = localPlayer.useSkillW(targetX, targetY);
        if (res) {
            if (entityManager) {
                entityManager.spawnBubbles(localPlayer.getCenterX(), localPlayer.getCenterY(), 18, res.skill.color);
            }

            if (realtimeChannel) {
                realtimeChannel.send('SKILL_TRIGGER', {
                    key: 'W',
                    skill: res.skill,
                    targetX: Math.round(res.targetX),
                    targetY: Math.round(res.targetY)
                });
            }

            if (window.showToast) window.showToast(`✨ Kích hoạt [W]: ${res.skill.name}!`);
        }
    }

    function triggerSkillE() {
        if (!isRunning || !localPlayer || localPlayer.dead) return;

        if (!localPlayer.slotE) {
            if (window.showToast) window.showToast('ℹ️ Ô kỹ năng [E] đang trống! Hãy nhặt ngọc kỹ năng trên biển.');
            return;
        }

        if (localPlayer.stunTimer > 0) {
            if (window.showToast) window.showToast('⚡ Đang bị choáng, không thể thi triển kỹ năng!');
            return;
        }

        const cx = localPlayer.getCenterX();
        const cy = localPlayer.getCenterY();
        let targetX = worldMouseX;
        let targetY = worldMouseY;
        if (Math.hypot(targetX - cx, targetY - cy) < 8) {
            targetX = cx + Math.cos(localPlayer.angle) * 120;
            targetY = cy + Math.sin(localPlayer.angle) * 120;
        }

        const res = localPlayer.useSkillE(targetX, targetY);
        if (res) {
            if (entityManager) {
                entityManager.spawnBubbles(localPlayer.getCenterX(), localPlayer.getCenterY(), 18, res.skill.color);
            }

            if (realtimeChannel) {
                realtimeChannel.send('SKILL_TRIGGER', {
                    key: 'E',
                    skill: res.skill,
                    targetX: Math.round(res.targetX),
                    targetY: Math.round(res.targetY)
                });
            }

            if (window.showToast) window.showToast(`✨ Kích hoạt [E]: ${res.skill.name}!`);
        }
    }

    // =========================================================================
    // KHỞI ĐỘNG TRẬN ĐẤU PVP (ĐÃ GHÉP ĐỦ 2 NGƯỜI CHƠI)
    // =========================================================================
    function startPvPMatch(room, currentUser) {
        if (!room || !room.host || !room.guest) return;

        isRunning = true;
        isPvE = false;
        currentGameMode = 'PVP';
        activeRoom = room;
        currentUserRef = currentUser;
        rematchRequested = false;
        remoteRematchRequested = false;
        isHost = (currentUser.userId === room.host.userId);

        entityManager = new window.EntityManager();

        // Xác định ai là Player 1 (Host) và ai là Player 2 (Guest)
        if (isHost) {
            localPlayer = new window.Player({
                id: String(room.host.userId),
                username: room.host.username,
                character: room.hostCharacter,
                fishPerSizeLevel: 2,
                x: MAP_WIDTH * 0.25,
                y: MAP_HEIGHT * 0.5,
                angle: 0,
                isLocal: true
            });

            remotePlayer = new window.Player({
                id: String(room.guest.userId),
                username: room.guest.username,
                character: room.guestCharacter,
                fishPerSizeLevel: 2,
                x: MAP_WIDTH * 0.75,
                y: MAP_HEIGHT * 0.5,
                angle: Math.PI,
                isLocal: false
            });

            // Host sinh đàn cá ban đầu
            entityManager.initEcosystem();
        } else {
            localPlayer = new window.Player({
                id: String(room.guest.userId),
                username: room.guest.username,
                character: room.guestCharacter,
                fishPerSizeLevel: 2,
                x: MAP_WIDTH * 0.75,
                y: MAP_HEIGHT * 0.5,
                angle: Math.PI,
                isLocal: true
            });

            remotePlayer = new window.Player({
                id: String(room.host.userId),
                username: room.host.username,
                character: room.hostCharacter,
                fishPerSizeLevel: 2,
                x: MAP_WIDTH * 0.25,
                y: MAP_HEIGHT * 0.5,
                angle: 0,
                isLocal: false
            });

            entityManager.initEcosystem();
        }

        // Đặt camera bám theo người chơi hiện tại
        cameraX = localPlayer.getCenterX() - canvas.width / 2;
        cameraY = localPlayer.getCenterY() - canvas.height / 2;

        // Callback khi Cua ăn cá mồi bằng Càng Sắt (Hook)
        localPlayer.onClawEatFish = (fish) => {
            if (entityManager) {
                entityManager.spawnBubbles(fish.getCenterX(), fish.getCenterY(), 10, 'rgba(0, 240, 255, 0.8)');
            }
            if (realtimeChannel) {
                realtimeChannel.send('PREY_EATEN', { fishId: fish.id, points: fish.points });
            }
        };

        // Callback khi Cua cắp kéo đối thủ về tới thân (xuyên mọi miễn nhiễm & kích thước)
        localPlayer.onClawEatPlayer = (victim) => {
            if (!victim || victim.dead) return;
            victim.dead = true;
            localPlayer.onEatPrey(500);
            if (entityManager) {
                entityManager.spawnBubbles(victim.getCenterX(), victim.getCenterY(), 35, 'rgba(255, 87, 34, 0.95)');
            }
            if (window.SoundManager) {
                window.SoundManager.playEat();
            }
            if (window.showToast) {
                window.showToast(`🦀 CÀNG SẮT NGHIỀN NÁT ${victim.username}! (+500 điểm)`);
            }
            if (realtimeChannel) {
                realtimeChannel.send('PLAYER_KILLED', {
                    killerId: localPlayer.id,
                    victimId: victim.id,
                    reason: 'CLAW_HOOK'
                });
            }
            endMatch(true, localPlayer.username);
        };

        // Callback khi Cá Ép kết thúc ký sinh hoặc tách ra: Đẩy lùi vật chủ & đồng bộ Realtime
        localPlayer.onDetachPush = (host, hostType, oppositeAngle) => {
            if (entityManager) {
                entityManager.spawnBubbles(host.x + (host.width || 80) / 2, host.y + (host.height || 60) / 2, 25, '#00ffc4');
            }
            if (window.showToast) {
                window.showToast('💨 ĐÃ TÁCH KHỎI VẬT CHỦ! Đẩy văng vật chủ ra xa và lướt an toàn!');
            }
            if (realtimeChannel && host) {
                realtimeChannel.send('DETACH_HOST', {
                    parasiteId: localPlayer.id,
                    hostId: host.id,
                    oppositeAngle: Number(oppositeAngle.toFixed(3)),
                    hostX: Math.round(host.x),
                    hostY: Math.round(host.y)
                });
            }
        };

        // Callback khi Cá Ép điều khiển vật chủ trong PvP
        localPlayer.onHostSteer = (hostId, angle, x, y) => {
            if (realtimeChannel) {
                realtimeChannel.send('HOST_STEER', {
                    hostId: hostId,
                    angle: Number(angle.toFixed(3)),
                    x: Math.round(x),
                    y: Math.round(y)
                });
            }
        };

        // Khởi tạo kênh truyền thời gian thực (Realtime Sync)
        if (realtimeChannel) realtimeChannel.close();
        realtimeChannel = new window.RealtimeChannel(room.roomCode, currentUser.userId);

        // Đăng ký nhận gói tin từ đối thủ
        realtimeChannel.onMessage((msg) => {
            handleIncomingPacket(msg);
        });

        // Nếu là Host, gửi danh sách đàn cá đã sinh cho Guest và hook sinh vật phẩm kỹ năng
        if (isHost) {
            const originalSpawn = entityManager.spawnSkillDrop.bind(entityManager);
            entityManager.spawnSkillDrop = function (skillType, x, y) {
                const it = originalSpawn(skillType, x, y);
                if (realtimeChannel) {
                    realtimeChannel.send('ITEM_SPAWN', {
                        item: {
                            id: it.id,
                            x: Math.round(it.x),
                            y: Math.round(it.y),
                            skillType: it.skill.type
                        }
                    });
                }
                return it;
            };

            setTimeout(() => {
                realtimeChannel.send('SEED_ECOSYSTEM', { seedList: entityManager.getExportList() });
            }, 300);
        } else {
            // Guest nhận vật phẩm hoàn toàn từ Host để tránh desync
            entityManager.itemSpawnTimer = 999999;
        }

        // Đóng toàn bộ modals để không che khuất màn hình chơi
        ['modal-pvp', 'modal-auth', 'modal-guide', 'modal-pause', 'modal-gameover', 'modal-pve-difficulty'].forEach(id => {
            const m = document.getElementById(id);
            if (m) m.classList.add('hidden');
        });

        // Chuyển màn hình từ Sảnh sang Game Canvas
        const lobbyScreen = document.getElementById('lobby-screen');
        const gameScreen = document.getElementById('game-screen');
        if (lobbyScreen) {
            lobbyScreen.classList.remove('active');
            lobbyScreen.classList.add('hidden');
        }
        if (gameScreen) {
            gameScreen.classList.remove('hidden');
            gameScreen.classList.add('active');
        }

        // Cập nhật Avatar & Tên lên HUD
        const hudAvatar = document.getElementById('hud-avatar');
        if (hudAvatar && localPlayer.character) {
            hudAvatar.src = localPlayer.character.img || 'cá kiếm.jpg';
        }

        const hudModePill = document.getElementById('hud-mode-pill');
        if (hudModePill) hudModePill.innerText = '⚔️ ĐỐI KHÁNG ONLINE';
        const hudTimerBox = document.getElementById('hud-timer-box');
        if (hudTimerBox) hudTimerBox.classList.add('hidden');

        isPaused = false;
        isPaused = false;
        lastTime = performance.now();
        requestAnimationFrame(gameLoop);
    }

    // =========================================================================
    // XỬ LÝ GÓI TIN ĐỒNG BỘ THỜI GIAN THỰC (REALTIME PACKETS)
    // =========================================================================
    function handleIncomingPacket(msg) {
        if (!msg || !isRunning) return;

        switch (msg.type) {
            case 'PLAYER_MOVE':
                if (remotePlayer && String(msg.senderId) === remotePlayer.id) {
                    remotePlayer.setRemoteSnapshot({
                        x: msg.x,
                        y: msg.y,
                        angle: msg.angle,
                        sizeLevel: msg.sizeLevel,
                        score: msg.score,
                        bite: msg.bite
                    });
                }
                break;

            case 'SEED_ECOSYSTEM':
                if (!isHost && entityManager && msg.seedList) {
                    entityManager.initEcosystem(msg.seedList);
                }
                break;

            case 'PREY_EATEN':
                if (entityManager && msg.fishId) {
                    const fish = entityManager.fishes.find(f => f.id === msg.fishId);
                    if (fish && !fish.eaten) {
                        fish.eaten = true;
                        entityManager.spawnBubbles(fish.getCenterX(), fish.getCenterY(), 8, 'rgba(0, 240, 255, 0.8)');
                    }
                }
                break;

            case 'PLAYERS_BUMP':
                if (localPlayer) {
                    localPlayer.applyBump(msg.bumpX || 0, msg.bumpY || 0);
                    entityManager.spawnBubbles(localPlayer.getCenterX(), localPlayer.getCenterY(), 15, 'rgba(255, 255, 255, 0.9)');
                }
                break;

            case 'SKILL_TRIGGER':
                if (remotePlayer && String(msg.senderId) === remotePlayer.id) {
                    if (msg.key === 'Q') {
                        // Kỹ năng Q của đối thủ
                        remotePlayer.mana = 0;
                        const charId = msg.charId || (remotePlayer.character ? remotePlayer.character.id : 2);
                        switch (charId) {
                            case 1: {
                                // Tôm (Phase 4): Dịch chuyển tức thời đến toX, toY
                                if (msg.toX !== undefined && msg.toY !== undefined) {
                                    remotePlayer.x = msg.toX - remotePlayer.getWidth() / 2;
                                    remotePlayer.y = msg.toY - remotePlayer.getHeight() / 2;
                                    remotePlayer.remoteTargetX = remotePlayer.x;
                                    remotePlayer.remoteTargetY = remotePlayer.y;
                                    remotePlayer.teleportFromX = msg.fromX;
                                    remotePlayer.teleportFromY = msg.fromY;
                                    remotePlayer.teleportToX = msg.toX;
                                    remotePlayer.teleportToY = msg.toY;
                                    remotePlayer.teleportTimer = 0.45;
                                }
                                break;
                            }
                            case 2: // Cá Kiếm: Lướt Thần Tốc
                                remotePlayer.speedBurstTimer = 3.2;
                                break;
                            case 3: {
                                // Cua (Phase 4): Phóng càng sắt
                                remotePlayer.clawActive = true;
                                remotePlayer.clawState = 'OUT';
                                remotePlayer.clawAngle = msg.angle !== undefined ? msg.angle : remotePlayer.angle;
                                remotePlayer.clawDist = 0;
                                remotePlayer.clawX = remotePlayer.getCenterX();
                                remotePlayer.clawY = remotePlayer.getCenterY();
                                remotePlayer.clawTarget = null;
                                break;
                            }
                            case 4: {
                                // Cá Ép (Phase 4): Lao bám Dash
                                remotePlayer.isDashing = true;
                                remotePlayer.dashTimer = 0.8;
                                remotePlayer.dashAngle = msg.angle !== undefined ? msg.angle : remotePlayer.angle;
                                remotePlayer.angle = remotePlayer.dashAngle;
                                break;
                            }
                            case 5: // Bạch Tuộc: Vùng mực hắc ám (5.0s, R=220px)
                                remotePlayer.inkX = msg.targetX;
                                remotePlayer.inkY = msg.targetY;
                                remotePlayer.inkTimer = 5.0;
                                break;
                            case 6: // Cá Đuối Điện: Vùng điện xung quanh (5.0s, tick 1.0s)
                                remotePlayer.shockWaveTimer = 5.0;
                                remotePlayer.shockWaveTickTimer = 0.0;
                                break;
                            case 7: // Rùa Biển: Mai Rùa Hộ Thể
                                remotePlayer.shieldTimer = 4.0;
                                break;
                        }
                        if (entityManager) {
                            entityManager.spawnBubbles(remotePlayer.getCenterX(), remotePlayer.getCenterY(), 20, '#ffd700');
                        }
                    } else if (msg.key === 'Q_DETACH') {
                        // Cá Ép đối thủ chủ động tách ra
                        remotePlayer.detachHost();
                    } else if (msg.key === 'W' || msg.key === 'E') {
                        // Kỹ năng nhặt được của đối thủ
                        if (msg.skill) {
                            remotePlayer.executeDroppedSkill(msg.skill, msg.targetX, msg.targetY);
                            if (entityManager) {
                                entityManager.spawnBubbles(remotePlayer.getCenterX(), remotePlayer.getCenterY(), 18, msg.skill.color);
                            }
                        }
                    }
                }
                break;

            case 'CRAW_HOOK_HIT':
                if (localPlayer && String(msg.victimId) === localPlayer.id) {
                    // Bản thân bị Càng Sắt của đối thủ găm trúng và kéo về (xuyên qua mọi bất tử/khiên/mực)!
                    localPlayer.stunTimer = 1.5;
                    if (remotePlayer) {
                        remotePlayer.clawState = 'PULLING';
                        remotePlayer.clawTarget = { type: 'player', entity: localPlayer, id: localPlayer.id };
                    }
                    if (window.showToast) {
                        window.showToast(`⚠️ CÀNG SẮT KHỐNG CHẾ! ${remotePlayer ? remotePlayer.username : 'Cua đối thủ'} đã kẹp xuyên giáp và đang kéo bạn về!`);
                    }
                }
                break;

            case 'ATTACH_HOST':
                if (remotePlayer && String(msg.parasiteId) === remotePlayer.id && localPlayer) {
                    // Đối thủ Cá Ép bám dính vào thân bản thân và điều khiển
                    remotePlayer.attachToHost(localPlayer, 'player');
                    if (window.showToast) {
                        window.showToast(`⚠️ Cá Ép (${remotePlayer.username}) đã ký sinh lên lưng bạn và đang điều khiển bạn!`);
                    }
                }
                break;

            case 'HOST_STEER':
                if (localPlayer && String(msg.hostId) === localPlayer.id) {
                    // Bản thân bị Cá Ép của đối thủ điều khiển hướng đi
                    if (msg.x !== undefined && msg.y !== undefined) {
                        localPlayer.x = Math.max(35, Math.min(MAP_WIDTH - localPlayer.getWidth() - 35, msg.x));
                        localPlayer.y = Math.max(35, Math.min(MAP_HEIGHT - localPlayer.getHeight() - 35, msg.y));
                    }
                    if (msg.angle !== undefined) {
                        localPlayer.angle = msg.angle;
                        localPlayer.facingLeft = Math.cos(msg.angle) < 0;
                    }
                }
                break;

            case 'DETACH_HOST':
                if (localPlayer && String(msg.hostId) === localPlayer.id) {
                    // Bản thân là Host bị Cá Ép tách ra và đẩy văng lùi lại theo hướng ngược lại
                    if (remotePlayer) {
                        remotePlayer.isAttached = false;
                        remotePlayer.attachedHost = null;
                        remotePlayer.attachedTimer = 0;
                    }
                    if (msg.oppositeAngle !== undefined) {
                        localPlayer.x = msg.hostX !== undefined ? msg.hostX : (localPlayer.x + Math.cos(msg.oppositeAngle) * 200);
                        localPlayer.y = msg.hostY !== undefined ? msg.hostY : (localPlayer.y + Math.sin(msg.oppositeAngle) * 200);
                        localPlayer.x = Math.max(40, Math.min(MAP_WIDTH - localPlayer.getWidth() - 40, localPlayer.x));
                        localPlayer.y = Math.max(40, Math.min(MAP_HEIGHT - localPlayer.getHeight() - 40, localPlayer.y));
                        localPlayer.stunTimer = 1.5;
                        if (window.showToast) {
                            window.showToast(`💥 Cá Ép đã tách ra và đẩy văng bạn ra xa!`);
                        }
                    }
                } else if (remotePlayer && String(msg.parasiteId) === remotePlayer.id) {
                    remotePlayer.detachHost();
                }
                break;

            case 'ITEM_SPAWN':
                if (!isHost && entityManager && msg.item) {
                    // Guest nhận vật phẩm được Host sinh
                    const it = new window.SkillItemDrop(msg.item.id, msg.item.x, msg.item.y, msg.item.skillType);
                    entityManager.items.push(it);
                }
                break;

            case 'ITEM_COLLECTED':
                if (entityManager && msg.itemId) {
                    const item = entityManager.items.find(i => i.id === msg.itemId);
                    if (item) {
                        item.collected = true;
                        entityManager.spawnBubbles(item.getCenterX(), item.getCenterY(), 15, item.skill.color);
                    }
                }
                break;

            case 'PLAYER_KILLED':
                if (localPlayer && String(msg.victimId) === localPlayer.id) {
                    // Bản thân bị nuốt chửng
                    localPlayer.dead = true;
                    entityManager.spawnBubbles(localPlayer.getCenterX(), localPlayer.getCenterY(), 30, 'rgba(255, 51, 102, 0.9)');
                    endMatch(false, remotePlayer ? remotePlayer.username : 'Đối thủ');
                } else if (remotePlayer && String(msg.victimId) === remotePlayer.id) {
                    // Đối thủ bị nuốt chửng
                    remotePlayer.dead = true;
                    entityManager.spawnBubbles(remotePlayer.getCenterX(), remotePlayer.getCenterY(), 30, 'rgba(0, 255, 135, 0.9)');
                    endMatch(true, localPlayer ? localPlayer.username : 'Bạn');
                }
                break;

            case 'REMATCH_REQUEST':
                remoteRematchRequested = true;
                if (window.showToast) {
                    window.showToast(`🔥 Đối thủ ${remotePlayer ? remotePlayer.username : ''} muốn tái đấu! Hãy bấm ĐẤU LẠI để bắt đầu.`);
                }
                const btnRetryReq = document.getElementById('btn-retry');
                if (btnRetryReq && !rematchRequested) {
                    btnRetryReq.innerText = '⚡ CHẤP NHẬN TÁI ĐẤU!';
                }
                if (rematchRequested) {
                    startPvPRematch();
                }
                break;

            case 'REMATCH_ACCEPT':
                startPvPRematch();
                break;

            case 'PLAYER_DISCONNECTED':
                if (remotePlayer && String(msg.leaverId) === remotePlayer.id) {
                    if (window.showToast) {
                        window.showToast(`⚠️ Đối thủ ${remotePlayer.username} đã rời phòng hoặc mất kết nối!`);
                    }
                    const btnRetryDis = document.getElementById('btn-retry');
                    if (btnRetryDis) {
                        btnRetryDis.innerText = 'ĐỐI THỦ ĐÃ RỜI PHÒNG';
                        btnRetryDis.disabled = true;
                    }
                    if (isRunning) {
                        // Tự động trao chiến thắng cho người chơi kiên cường ở lại
                        endMatch(true, localPlayer ? localPlayer.username : 'Bạn');
                    }
                }
                break;
        }
    }

    // =========================================================================
    // VÒNG LẶP CHÍNH (GAME LOOP 60 FPS)
    // =========================================================================
    function gameLoop(currentTime) {
        if (!isRunning) return;

        // Xử lý Tạm dừng (Pause): Không cập nhật logic di chuyển / thời gian / cá
        if (isPaused) {
            lastTime = currentTime; // Ngăn chặn nhảy vọt dt khi tiếp tục trận đấu
            render();
            requestAnimationFrame(gameLoop);
            return;
        }

        const dt = Math.min(0.06, (currentTime - lastTime) / 1000);
        lastTime = currentTime;

        update(dt);
        render();

        requestAnimationFrame(gameLoop);
    }

    /**
     * Tìm vị trí xuất phát / hồi sinh an toàn xa các cá lớn hơn và gần cá nhỏ
     */
    function respawnPlayerSafe(player) {
        if (!player) return;
        let bestX = 300;
        let bestY = MAP_HEIGHT / 2;
        let bestScore = -Infinity;
        const mySize = player.getEffectiveSizeLevel();

        if (entityManager && entityManager.fishes) {
            for (let attempt = 0; attempt < 40; attempt++) {
                const cx = 150 + Math.random() * (MAP_WIDTH - 300);
                const cy = 120 + Math.random() * (MAP_HEIGHT - 240);

                let minDistToBigger = Infinity;
                let smallFishCount = 0;

                entityManager.fishes.forEach(f => {
                    if (f.eaten) return;
                    const dist = Math.hypot(f.getCenterX() - cx, f.getCenterY() - cy);
                    if (f.level > mySize) {
                        if (dist < minDistToBigger) minDistToBigger = dist;
                    } else {
                        if (dist < 320) smallFishCount++;
                    }
                });

                if (minDistToBigger === Infinity) minDistToBigger = 1000;
                let score = minDistToBigger + smallFishCount * 80;
                if (minDistToBigger < 240) score -= 3000;

                if (score > bestScore) {
                    bestScore = score;
                    bestX = cx;
                    bestY = cy;
                }
            }
        }

        player.x = Math.max(40, Math.min(MAP_WIDTH - player.getWidth() - 40, bestX - player.getWidth() / 2));
        player.y = Math.max(40, Math.min(MAP_HEIGHT - player.getHeight() - 40, bestY - player.getHeight() / 2));
        if (entityManager) {
            entityManager.spawnBubbles(bestX, bestY, 25, '#00ffff');
        }
    }

    function update(dt) {
        updateWorldMouse();

        // 0. ĐẾM NGƯỢC THỜI GIAN (CHẾ ĐỘ ĐUA ĐIỂM 3 PHÚT)
        if (currentGameMode === 'TIMED') {
            timeRemaining -= dt;
            const mins = Math.floor(Math.max(0, timeRemaining) / 60);
            const secs = Math.floor(Math.max(0, timeRemaining) % 60);
            const timerEl = document.getElementById('hud-timer-text');
            if (timerEl) {
                timerEl.innerText = `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
            }
            if (timeRemaining <= 0) {
                timeRemaining = 0;
                endMatch(true, 'Hết giờ thi đấu');
                return;
            }
        }

        // 0.1 KIỂM TRA ĐIỀU KIỆN THẮNG CHẾ ĐỘ CƠ BẢN (CLASSIC: ĐẠT LV.4)
        if (currentGameMode === 'CLASSIC' && localPlayer && !localPlayer.dead) {
            if (localPlayer.sizeLevel >= 4) {
                endMatch(true, 'Đạt Max LV.4');
                return;
            }
        }

        // 1. Cập nhật Player của máy này bằng Mouse-Aim
        if (localPlayer) {
            localPlayer.updateMouseAim(worldMouseX, worldMouseY, dt);
        }

        // 2. Cập nhật Remote Player / Bot AI PvE
        if (remotePlayer && !remotePlayer.dead) {
            if (remotePlayer.isBot) {
                remotePlayer.updateBotAI(dt, entityManager, localPlayer);
            }
            remotePlayer.updateMouseAim(0, 0, dt);

            // Cá máy săn mồi trong PvE để tăng cấp & điểm số cạnh tranh
            if (remotePlayer.isBot && entityManager) {
                const botSize = remotePlayer.getEffectiveSizeLevel();
                const bx = remotePlayer.getCenterX();
                const by = remotePlayer.getCenterY();
                const br = remotePlayer.getRadius();
                const bMouthX = remotePlayer.getMouthX();
                const bMouthY = remotePlayer.getMouthY();
                const bMouthR = br * 0.65;

                for (let i = 0; i < entityManager.fishes.length; i++) {
                    const f = entityManager.fishes[i];
                    if (f.eaten) continue;
                    if (botSize >= f.level) {
                        const fx = f.getCenterX();
                        const fy = f.getCenterY();
                        const fr = f.getRadius();
                        const distMouth = Math.hypot(bMouthX - fx, bMouthY - fy);
                        const distCenter = Math.hypot(bx - fx, by - fy);

                        if (distMouth < bMouthR + fr || distCenter < br * 0.75 + fr * 0.75) {
                            f.eaten = true;
                            remotePlayer.onEatPrey(f.points, f.level);
                            entityManager.spawnBubbles(fx, fy, 10, 'rgba(255, 0, 119, 0.7)');
                        }
                    }
                }
            }
        }

        // 2.1 THUẬT TOÁN HỖ TRỢ NGƯỜI CHƠI & CÂN BẰNG ĐỘ KHÓ DDA (CHẾ ĐỘ PVE)
        if (isPvE && localPlayer && !localPlayer.dead && remotePlayer && !remotePlayer.dead && entityManager) {
            pveGameTime += dt;
            ddaCheckTimer += dt;
            ddaSpawnNearTimer += dt;

            // Đánh giá nhịp độ mỗi 0.5s để điều tiết thích ứng mượt mà
            if (ddaCheckTimer >= 0.5) {
                ddaCheckTimer = 0.0;

                const pEaten = localPlayer.fishEaten || 0;
                const bEaten = remotePlayer.fishEaten || 0;
                const pLevel = localPlayer.sizeLevel || 1;
                const diff = pEaten - bEaten;

                // Xác định ngưỡng đánh giá theo độ khó lựa chọn (Dễ / Vừa / Khó)
                let slowEvolThresholdDiff = -3;
                let fastEvolThresholdDiff = 3;
                let timeCheckSlow = 20.0;
                let eatenCheckSlow = 4;

                switch (pveDifficulty) {
                    case 'EASY':
                        slowEvolThresholdDiff = -2; // Dễ: Nhạy hơn, chỉ cần thua 2 cá là kích hoạt hỗ trợ
                        fastEvolThresholdDiff = 5;  // Ngưỡng bứt phá rộng hơn
                        timeCheckSlow = 15.0;
                        eatenCheckSlow = 4;
                        break;
                    case 'MEDIUM':
                        slowEvolThresholdDiff = -3; // Vừa: Chuẩn cân bằng
                        fastEvolThresholdDiff = 3;
                        timeCheckSlow = 22.0;
                        eatenCheckSlow = 4;
                        break;
                    case 'HARD':
                        slowEvolThresholdDiff = -4; // Khó: Đòi hỏi cao, chỉ hỗ trợ khi bị áp đảo sâu
                        fastEvolThresholdDiff = 2;  // Rất dễ bị kích hoạt tăng độ khó khi ăn nhanh
                        timeCheckSlow = 28.0;
                        eatenCheckSlow = 3;
                        break;
                }

                // 1. KIỂM TRA TRẠNG THÁI TIẾN HÓA CỦA NGƯỜI CHƠI:
                const isSlowEvolution = (diff <= slowEvolThresholdDiff) || (pveGameTime > timeCheckSlow && pEaten < eatenCheckSlow && pLevel === 1);
                const isFastEvolution = (diff >= fastEvolThresholdDiff) || (pveGameTime < 40.0 && pLevel >= 3) || (pveGameTime < 25.0 && pLevel >= 2);

                if (isSlowEvolution) {
                    // TRƯỜNG HỢP 1: NGƯỜI CHƠI TIẾN HÓA CHẬM / GẶP KHÓ KHĂN
                    ddaStatus = 'ASSISTING';

                    // - Tăng số lượng cá con sinh ra (tỷ lệ cá con level 1-2 chiếm 68-75%)
                    entityManager.smallFishRatio = (pveDifficulty === 'EASY') ? 0.75 : ((pveDifficulty === 'MEDIUM') ? 0.68 : 0.60);
                    entityManager.targetFishCount = (pveDifficulty === 'EASY') ? 34 : 30;
                    entityManager.spawnNearPlayer = true;
                    entityManager.nearPlayerPos = { x: localPlayer.getCenterX(), y: localPlayer.getCenterY() };

                    // - Giảm tốc độ bơi của cá xung quanh để người chơi dễ dàng đuổi bắt mồi
                    if (pveDifficulty === 'EASY') {
                        ddaFishSpeedMult = 0.65; // Giảm 35% tốc độ cá mồi
                    } else if (pveDifficulty === 'MEDIUM') {
                        ddaFishSpeedMult = 0.75; // Giảm 25% tốc độ cá mồi
                    } else {
                        ddaFishSpeedMult = 0.85; // Giảm 15% tốc độ cá mồi
                    }

                    remotePlayer.dynamicSpeedMult = 1.0;
                    localPlayer.dynamicSpeedMult = (pveDifficulty === 'EASY') ? 1.15 : 1.05;

                    // Định kỳ mỗi 3.5s tự động sinh thêm 1 cá con ngay gần người chơi
                    if (ddaSpawnNearTimer >= 3.5) {
                        ddaSpawnNearTimer = 0.0;
                        const curAlive = entityManager.fishes.filter(f => !f.eaten).length;
                        if (curAlive < entityManager.targetFishCount + 2) {
                            entityManager.spawnSmallFishNear(localPlayer.getCenterX(), localPlayer.getCenterY());
                        }
                    }
                } else if (isFastEvolution) {
                    // TRƯỜNG HỢP 2: NGƯỜI CHƠI TIẾN HÓA QUÁ NHANH / ÁP ĐẢO
                    ddaStatus = 'CHALLENGING';

                    // - Giảm số lượng cá con, tăng tỷ lệ cá to level 3-5
                    entityManager.smallFishRatio = (pveDifficulty === 'HARD') ? 0.20 : ((pveDifficulty === 'MEDIUM') ? 0.28 : 0.35);
                    entityManager.targetFishCount = (pveDifficulty === 'HARD') ? 22 : 24;
                    entityManager.spawnNearPlayer = false;
                    entityManager.nearPlayerPos = null;

                    // - Tăng tốc độ bơi của cá xung quanh (cá cảnh giác và né tránh nhanh hơn)
                    if (pveDifficulty === 'HARD') {
                        ddaFishSpeedMult = 1.45; // Tăng 45% tốc độ cá xung quanh!
                    } else if (pveDifficulty === 'MEDIUM') {
                        ddaFishSpeedMult = 1.30; // Tăng 30% tốc độ cá xung quanh
                    } else {
                        ddaFishSpeedMult = 1.15; // Tăng 15% tốc độ cá xung quanh
                    }

                    // Tăng nhẹ tốc độ bot AI để bám đuổi nhịp ván, duy trì thế trận kịch tính
                    remotePlayer.dynamicSpeedMult = (pveDifficulty === 'HARD') ? 1.25 : 1.15;
                    localPlayer.dynamicSpeedMult = 1.0;
                } else {
                    // TRƯỜNG HỢP 3: NHỊP VÁN CÂN BẰNG
                    ddaStatus = 'BALANCED';
                    entityManager.smallFishRatio = (pveDifficulty === 'EASY') ? 0.50 : ((pveDifficulty === 'HARD') ? 0.35 : 0.45);
                    entityManager.targetFishCount = 28;
                    entityManager.spawnNearPlayer = false;
                    entityManager.nearPlayerPos = null;

                    ddaFishSpeedMult = (pveDifficulty === 'EASY') ? 0.9 : ((pveDifficulty === 'HARD') ? 1.1 : 1.0);
                    remotePlayer.dynamicSpeedMult = 1.0;
                    localPlayer.dynamicSpeedMult = 1.0;
                }

                // Cập nhật tốc độ cá tức thì vào EntityManager
                entityManager.fishSpeedMultiplier = ddaFishSpeedMult;
            }
        }

        // 3. Cập nhật đàn cá mồi & bọt nước
        if (entityManager) {
            entityManager.update(dt);
        }

        // 4. KIỂM TRA VA CHẠM TOÀN DIỆN: ĂN CÁ, BỊ CÁ LỚN ĂN & KHIÊN PHẢN ĐÒN
        if (localPlayer && !localPlayer.dead && entityManager) {
            const playerSize = localPlayer.getEffectiveSizeLevel();
            const px = localPlayer.getCenterX();
            const py = localPlayer.getCenterY();
            const pr = localPlayer.getRadius();
            const mouthX = localPlayer.getMouthX();
            const mouthY = localPlayer.getMouthY();
            const mouthR = pr * 0.65;

            for (let i = 0; i < entityManager.fishes.length; i++) {
                const f = entityManager.fishes[i];
                if (f.eaten || localPlayer.dead) continue;

                // [CÁ ÉP - BẢO VỆ VẬT CHỦ]: NẾU ĐANG KÝ SINH LÊN CON CÁ NÀY, TUYỆT ĐỐI KHÔNG ĂN / KHÔNG VA CHẠM VỚI VẬT CHỦ!
                if (localPlayer.isAttached && localPlayer.attachedHost && localPlayer.attachedHost.entity === f) {
                    continue;
                }

                const fx = f.getCenterX();
                const fy = f.getCenterY();
                const fr = f.getRadius();

                const distCenter = Math.hypot(px - fx, py - fy);
                const distMouth = Math.hypot(mouthX - fx, mouthY - fy);

                // [CÁ ÉP - LAO BÁM KÝ SINH VÀO CÁ LỚN]:
                // Nếu đang lao nhanh (isDashing) và chạm cá có level >= level gốc của người chơi: Bám ký sinh ngay lập tức!
                // TUYỆT ĐỐI KHÔNG ĂN, KHÔNG BỊ ĂN, KHÔNG LÀM CHẾT VẬT CHỦ!
                if (localPlayer.isDashing && !localPlayer.isAttached && localPlayer.character && localPlayer.character.id === 4) {
                    if (f.level >= localPlayer.sizeLevel) {
                        if (distCenter < pr + fr + 20) {
                            localPlayer.attachToHost(f, 'fish');
                            entityManager.spawnBubbles(px, py, 25, '#00ffc4');
                            if (window.showToast) {
                                window.showToast(`🔗 ĐÃ KÝ SINH CÁ LỚN LV.${f.level}! Bạn đang trực tiếp điều khiển hướng bơi & mượn kích thước!`);
                            }
                            continue;
                        }
                    }
                }

                // 4.1 RÙA BIỂN BẬT MAI RÙA HỘ THỂ: CHOÁNG & ĐẨY LÙI CÁ TO
                if (localPlayer.shieldTimer > 0 && f.level >= playerSize) {
                    if (distCenter < pr + fr) {
                        f.stunTimer = 1.5;
                        const pushAngle = Math.atan2(fy - py, fx - px);
                        f.x += Math.cos(pushAngle) * 140;
                        f.y += Math.sin(pushAngle) * 140;
                        entityManager.spawnBubbles(fx, fy, 15, '#ffd700');
                        if (window.showToast) window.showToast('🛡️ MAI RÙA PHẢN ĐÒN! Làm choáng cá lớn!');
                    }
                }
                // 4.2 ĂN CÁ NHỎ HƠN HOẶC BẰNG (HOẶC ĐANG BẬT BÁ KHÍ)
                else if (playerSize >= f.level || localPlayer.baKhiTimer > 0) {
                    if (distMouth < mouthR + fr || distCenter < pr * 0.75 + fr * 0.75 || (localPlayer.baKhiTimer > 0 && distCenter < pr + fr)) {
                        f.eaten = true;
                        const evo = localPlayer.onEatPrey(f.points, f.level);
                        entityManager.spawnBubbles(fx, fy, 12, localPlayer.baKhiTimer > 0 ? '#ef4444' : 'rgba(0, 240, 255, 0.85)');

                        if (window.SoundManager) {
                            window.SoundManager.playEat();
                        }

                        if (evo && evo.levelUp) {
                            if (window.SoundManager) {
                                window.SoundManager.playLevelUp();
                            }
                            if (window.showToast) {
                                window.showToast(`🎉 TIẾN HÓA LÊN SIZE ${evo.newSize}! Kích thước & Uy lực tăng vọt!`);
                            }
                            entityManager.spawnBubbles(px, py, 30, '#00ff87');
                        }

                        // Điều kiện thắng chế độ CƠ BẢN (CLASSIC): Đạt LV.4
                        if (currentGameMode === 'CLASSIC' && localPlayer.sizeLevel >= 4) {
                            endMatch(true, 'Đạt Max LV.4 - Bá Chủ Đại Dương');
                            return;
                        }

                        if (realtimeChannel) {
                            realtimeChannel.send('PREY_EATEN', { fishId: f.id, points: f.points });
                        }
                    }
                }
                // 4.3 BỊ CÁ LỚN HƠN ĂN THỊT!
                // Quy tắc: Nếu người chơi đang bất tử (isInvincible bao gồm cả Bạch Tuộc ở trong mực inInkBuff)
                // HOẶC con cá lớn này đang ở trong vùng mực của Bạch Tuộc (bị mù & chặn điều kiện ăn thịt người chơi): BỎ QUA KHÔNG ĂN!
                else if (!localPlayer.isInvincible() && !(f.blindTimer > 0 && localPlayer.character && localPlayer.character.id === 5)) {
                    if (distCenter < pr + fr) {
                        entityManager.spawnBubbles(px, py, 35, 'rgba(255, 51, 102, 0.95)');

                        if (currentGameMode === 'PVE') {
                            if (localPlayer.lives > 1) {
                                localPlayer.lives--;
                                localPlayer.hp = 100;
                                respawnPlayerSafe(localPlayer);
                                localPlayer.shieldTimer = 2.5; // Bất tử nhấp nháy 2.5s khi vừa hồi sinh
                                if (window.showToast) {
                                    window.showToast(`💔 Bị cá lớn Level ${f.level} nuốt! Mất 1 mạng (Còn ${localPlayer.lives} mạng).`);
                                }
                            } else {
                                localPlayer.dead = true;
                                localPlayer.lives = 0;
                                endMatch(false, `Bị cá lớn Level ${f.level} nuốt chửng!`);
                                return;
                            }
                        } else {
                            // Chế độ Cơ Bản (CLASSIC) / Đua Điểm (TIMED): 1 mạng, chạm cá lớn là bị ăn thịt kết thúc ngay
                            localPlayer.dead = true;
                            localPlayer.lives = 0;
                            endMatch(false, `Bị cá lớn Level ${f.level} nuốt chửng!`);
                            return;
                        }
                    }
                }
            }

            // 4.4 KIỂM TRA NHẶT VẬT PHẨM KỸ NĂNG TRÊN BIỂN (PHASE 3)
            const collected = entityManager.checkItemCollection(localPlayer);
            if (collected) {
                if (collected.isInstant) {
                    if (window.showToast) {
                        window.showToast(`🔥 ĂN ĐƯỢC BÁ KHÍ! BẤT TỬ & TĂNG TỐC 60% TRONG ${(collected.item.skill.duration || 5).toFixed(0)}S!`);
                    }
                } else {
                    const slotText = collected.slotName === 'W' ? 'ô [W]' : (collected.slotName === 'E' ? 'ô [E]' : 'ô [E] (ghi đè)');
                    if (window.showToast) {
                        window.showToast(`💎 Đã nhặt [${collected.item.skill.name}] vào ${slotText}! Bấm phím [${collected.slotName.charAt(0)}] để dùng.`);
                    }
                }
                if (realtimeChannel) {
                    realtimeChannel.send('ITEM_COLLECTED', { itemId: collected.item.id });
                }
            }

            // 4.5 KIỂM TRA HIỂM HỌA: BOM BIỂN NỔ & CÁ ĐỘC
            if (entityManager.hazards && entityManager.hazards.length > 0) {
                const hazardHit = entityManager.checkHazardCollision(localPlayer);
                if (hazardHit) {
                    if (hazardHit.type === 'BOMB') {
                        // Thủy lôi nổ tung!
                        if (window.SoundManager) {
                            window.SoundManager.playBomb();
                        }
                        if (!localPlayer.isInvincible()) {
                            if (currentGameMode === 'PVE') {
                                if (localPlayer.lives > 1) {
                                    localPlayer.lives--;
                                    localPlayer.hp = 100;
                                    respawnPlayerSafe(localPlayer);
                                    localPlayer.shieldTimer = 2.5;
                                    if (window.showToast) {
                                        window.showToast(`💥 Va phải thủy lôi! Nổ mất 1 mạng (Còn ${localPlayer.lives} mạng)!`);
                                    }
                                } else {
                                    localPlayer.dead = true;
                                    localPlayer.lives = 0;
                                    endMatch(false, 'Va phải thủy lôi! Bom nổ tung!');
                                    return;
                                }
                            } else {
                                // Chế độ Cơ Bản (CLASSIC) / Đua Điểm (TIMED): Chạm bom nổ tung kết thúc ván đấu ngay
                                localPlayer.dead = true;
                                localPlayer.lives = 0;
                                endMatch(false, 'Va phải thủy lôi! Bom nổ tung!');
                                return;
                            }
                        } else {
                            // ĐANG BẤT TỬ (BÁ KHÍ HOẶC KHIÊN): BOM NỔ VÔ HẠI
                            entityManager.spawnBubbles(hazardHit.hazard.getCenterX(), hazardHit.hazard.getCenterY(), 20, '#ef4444');
                            if (window.showToast) {
                                window.showToast('🛡️ BẤT TỬ! Thủy lôi nổ vô hại!');
                            }
                        }
                    } else if (hazardHit.type === 'POISON') {
                        // Trúng độc cá gai
                        if (!localPlayer.isInvincible()) {
                            const dmg = localPlayer.applyPoison(4.0);
                            if (dmg && dmg.dead) {
                                endMatch(false, 'Trúng độc cá gai tử nạn!');
                                return;
                            } else if (window.showToast) {
                                window.showToast(`☠️ Chạm phải cá độc! Bị làm chậm 50% và mất máu (Còn ${localPlayer.hp} HP)!`);
                            }
                        } else {
                            // ĐANG BẤT TỬ: HOÀN TOÀN MIỄN NHIỄM ĐỘC
                            if (window.showToast) {
                                window.showToast('🛡️ BẤT TỬ! Miễn nhiễm độc cá gai!');
                            }
                        }
                    }
                }
            }
        }

        // 4.2 TƯƠNG TÁC CHIÊU THỨC DIỆN RỘNG (MỰC ĐEN & VÙNG ĐIỆN CÁ ĐUỐI ĐIỆN)
        // 4.2.1 VÙNG ĐIỆN CỦA LOCAL PLAYER (CÁ ĐUỐI ĐIỆN - 5.0S, TICK MỖI 1.0S GÂY CHOÁNG 0.5S)
        if (localPlayer && !localPlayer.dead && localPlayer.shockWaveTimer > 0) {
            localPlayer.shockWaveTickTimer -= dt;
            if (localPlayer.shockWaveTickTimer <= 0) {
                localPlayer.shockWaveTickTimer = 1.0; // Reset tick 1.0s
                const auraRadius = localPlayer.getShockWaveRadius ? localPlayer.getShockWaveRadius() : (localPlayer.getRadius() + 90);
                const aX = localPlayer.getCenterX();
                const aY = localPlayer.getCenterY();

                // 1. Áp dụng choáng 0.5s lên toàn bộ cá AI trong phạm vi bán kính
                if (entityManager && entityManager.fishes) {
                    for (let f of entityManager.fishes) {
                        if (f.eaten) continue;
                        const distFish = Math.hypot(aX - f.getCenterX(), aY - f.getCenterY());
                        if (distFish <= auraRadius + f.getRadius()) {
                            f.stunTimer = 0.5; // Choáng 0.5s
                            entityManager.spawnBubbles(f.getCenterX(), f.getCenterY(), 8, '#facc15');
                        }
                    }
                }

                // 2. Áp dụng choáng 0.5s lên đối thủ PvP trong phạm vi bán kính
                if (remotePlayer && !remotePlayer.dead && !remotePlayer.isInvincible()) {
                    const distRemote = Math.hypot(aX - remotePlayer.getCenterX(), aY - remotePlayer.getCenterY());
                    if (distRemote <= auraRadius + remotePlayer.getRadius()) {
                        remotePlayer.stunTimer = 0.5;
                        if (entityManager) {
                            entityManager.spawnBubbles(remotePlayer.getCenterX(), remotePlayer.getCenterY(), 14, '#facc15');
                        }
                    }
                }

                // Hiệu ứng tia chớp bọt nước kích hoạt mỗi nhịp tick
                if (entityManager) {
                    entityManager.spawnBubbles(aX, aY, 16, '#facc15');
                }
            }
        }

        // 4.2.2 VÙNG ĐIỆN CỦA REMOTE PLAYER (ĐỐI THỦ PVP)
        if (remotePlayer && !remotePlayer.dead && remotePlayer.shockWaveTimer > 0) {
            remotePlayer.shockWaveTickTimer -= dt;
            if (remotePlayer.shockWaveTickTimer <= 0) {
                remotePlayer.shockWaveTickTimer = 1.0;
                const auraRadius = remotePlayer.getShockWaveRadius ? remotePlayer.getShockWaveRadius() : (remotePlayer.getRadius() + 90);
                const raX = remotePlayer.getCenterX();
                const raY = remotePlayer.getCenterY();

                // Áp dụng choáng 0.5s lên Local Player nếu trong phạm vi
                if (localPlayer && !localPlayer.dead && !localPlayer.isInvincible()) {
                    const distLocal = Math.hypot(raX - localPlayer.getCenterX(), raY - localPlayer.getCenterY());
                    if (distLocal <= auraRadius + localPlayer.getRadius()) {
                        localPlayer.stunTimer = 0.5;
                        if (entityManager) {
                            entityManager.spawnBubbles(localPlayer.getCenterX(), localPlayer.getCenterY(), 14, '#facc15');
                        }
                        if (window.showToast) {
                            window.showToast('⚡ Bị sốc điện từ Cá Đuối Điện của đối thủ! Tê liệt 0.5s!');
                        }
                    }
                }

                if (entityManager) {
                    entityManager.spawnBubbles(raX, raY, 16, '#facc15');
                }
            }
        }

        // 4.2.3 VÙNG MỰC HẮC ÁM (BẠCH TUỘC: BÁN KÍNH 220PX, TỒN TẠI 5.0S)
        // 1. Kiểm tra trạng thái người chơi sở hữu khi ở trong vùng mực của mình: Bất tử & Tăng 50% tốc độ
        if (localPlayer && !localPlayer.dead) {
            if (localPlayer.inkTimer > 0) {
                const distToOwnInk = Math.hypot(localPlayer.getCenterX() - localPlayer.inkX, localPlayer.getCenterY() - localPlayer.inkY);
                localPlayer.inInkBuff = (distToOwnInk <= (localPlayer.INK_ZONE_RADIUS || 220));
            } else {
                localPlayer.inInkBuff = false;
            }
        }
        if (remotePlayer && !remotePlayer.dead) {
            if (remotePlayer.inkTimer > 0) {
                const distToRemoteInk = Math.hypot(remotePlayer.getCenterX() - remotePlayer.inkX, remotePlayer.getCenterY() - remotePlayer.inkY);
                remotePlayer.inInkBuff = (distToRemoteInk <= (remotePlayer.INK_ZONE_RADIUS || 220));
            } else {
                remotePlayer.inInkBuff = false;
            }
        }

        // 2. Tác động của vùng mực lên đàn cá mồi / cá AI trên bản đồ (áp dụng cho cả vùng mực của Local lẫn Remote)
        if (entityManager && entityManager.fishes) {
            for (let f of entityManager.fishes) {
                if (f.eaten) continue;
                const fx = f.getCenterX();
                const fy = f.getCenterY();

                let insideAnyInk = false;
                // Vùng mực của local player
                if (localPlayer && localPlayer.inkTimer > 0) {
                    const distF = Math.hypot(fx - localPlayer.inkX, fy - localPlayer.inkY);
                    if (distF <= (localPlayer.INK_ZONE_RADIUS || 220)) {
                        insideAnyInk = true;
                    }
                }
                // Vùng mực của remote player
                if (!insideAnyInk && remotePlayer && remotePlayer.inkTimer > 0) {
                    const distF = Math.hypot(fx - remotePlayer.inkX, fy - remotePlayer.inkY);
                    if (distF <= (remotePlayer.INK_ZONE_RADIUS || 220)) {
                        insideAnyInk = true;
                    }
                }

                if (insideAnyInk) {
                    // Cá ở trong vùng mực: bị làm chậm 50% và bị mù / mất phương hướng
                    f.slowTimer = Math.max(f.slowTimer || 0, 0.4);
                    f.blindTimer = Math.max(f.blindTimer || 0, 0.4);
                }
            }
        }

        // 3. Tác động vùng mực giữa hai người chơi trong PvP
        if (localPlayer && remotePlayer && !localPlayer.dead && !remotePlayer.dead) {
            const p1X = localPlayer.getCenterX();
            const p1Y = localPlayer.getCenterY();
            const p2X = remotePlayer.getCenterX();
            const p2Y = remotePlayer.getCenterY();

            // Đối thủ bơi vào vùng mực của Local Player:
            if (localPlayer.inkTimer > 0) {
                const distP2 = Math.hypot(p2X - localPlayer.inkX, p2Y - localPlayer.inkY);
                if (distP2 <= (localPlayer.INK_ZONE_RADIUS || 220)) {
                    remotePlayer.slowTimer = Math.max(remotePlayer.slowTimer, 0.4);
                }
            }

            // Local Player bơi vào vùng mực của Đối thủ:
            if (remotePlayer.inkTimer > 0) {
                const distP1 = Math.hypot(p1X - remotePlayer.inkX, p1Y - remotePlayer.inkY);
                if (distP1 <= (remotePlayer.INK_ZONE_RADIUS || 220)) {
                    localPlayer.slowTimer = Math.max(localPlayer.slowTimer, 0.4);
                }
            }
        }

        // 4.3 CUA (PHASE 4): KIỂM TRA VA CHẠM CÀNG PHÓNG BẮT MỤC TIÊU
        // Bắt buộc kẹp trúng và xử lý ăn/kéo mục tiêu KHÔNG CÓ NGOẠI LỆ:
        // - Bỏ qua kiểm tra kích thước/cấp độ lớn hơn
        // - Bỏ qua mọi trạng thái bất tử (khiên Rùa, bá khí, mực Bạch tuộc)
        // - Bỏ qua điều kiện bảo vệ/mù/chặn ăn trong vùng mực
        if (localPlayer && localPlayer.clawActive && localPlayer.clawState === 'OUT') {
            const clawX = localPlayer.clawX;
            const clawY = localPlayer.clawY;

            // 1. Kiểm tra móc trúng cá mồi/cá lớn trên bản đồ (bỏ qua mọi cấp độ/kích thước)
            if (entityManager) {
                for (let f of entityManager.fishes) {
                    if (!f.eaten) {
                        const distToFish = Math.hypot(clawX - f.getCenterX(), clawY - f.getCenterY());
                        if (distToFish < f.getRadius() + 22) {
                            localPlayer.clawState = 'PULLING';
                            localPlayer.clawTarget = { type: 'fish', entity: f, id: f.id };
                            f.stunTimer = 1.5; // Khóa chuyển động ngay lập tức
                            entityManager.spawnBubbles(clawX, clawY, 18, '#ff5722');
                            if (window.showToast) {
                                window.showToast(`🎯 Càng sắt kẹp chặt Cá Level ${f.level}! Xuyên giáp & kéo về...`);
                            }
                            break;
                        }
                    }
                }
            }

            // 2. Kiểm tra móc trúng đối thủ PvP / Bot (xuyên mọi khiên, bất tử, vùng mực, size lớn)
            if (localPlayer.clawState === 'OUT' && remotePlayer && !remotePlayer.dead) {
                const distToRemote = Math.hypot(clawX - remotePlayer.getCenterX(), clawY - remotePlayer.getCenterY());
                if (distToRemote < remotePlayer.getRadius() + 24) {
                    localPlayer.clawState = 'PULLING';
                    localPlayer.clawTarget = { type: 'player', entity: remotePlayer, id: remotePlayer.id };
                    remotePlayer.stunTimer = 1.5; // Khóa cứng đối thủ bất kể trạng thái
                    entityManager.spawnBubbles(clawX, clawY, 25, '#ff5722');
                    if (window.showToast) {
                        window.showToast(`🦀 CÀNG SẮT KHỐNG CHẾ! Móc xuyên miễn nhiễm ${remotePlayer.username}!`);
                    }
                    if (realtimeChannel) {
                        realtimeChannel.send('CRAW_HOOK_HIT', {
                            hookerId: localPlayer.id,
                            victimId: remotePlayer.id
                        });
                    }
                }
            }
        }

        // 4.4 CÁ ÉP (PHASE 4): KIỂM TRA VA CHẠM LAO BÁM KÝ SINH
        if (localPlayer && localPlayer.isDashing && !localPlayer.isAttached && localPlayer.character && localPlayer.character.id === 4) {
            const pX = localPlayer.getCenterX();
            const pY = localPlayer.getCenterY();
            const pR = localPlayer.getRadius();

            // 1. Lao bám vào đối thủ PvP (nếu đối thủ có size >= Cá Ép)
            if (remotePlayer && !remotePlayer.dead && remotePlayer.sizeLevel >= localPlayer.sizeLevel) {
                const distToRemote = Math.hypot(pX - remotePlayer.getCenterX(), pY - remotePlayer.getCenterY());
                if (distToRemote < pR + remotePlayer.getRadius() + 25) {
                    localPlayer.attachToHost(remotePlayer, 'player');
                    entityManager.spawnBubbles(pX, pY, 25, '#00ffc4');
                    if (window.showToast) window.showToast(`🔗 ĐÃ KÝ SINH LÊN ${remotePlayer.username}! Bạn đang điều khiển đối thủ & mượn cấp độ!`);
                    if (realtimeChannel) {
                        realtimeChannel.send('ATTACH_HOST', {
                            parasiteId: localPlayer.id,
                            hostId: remotePlayer.id
                        });
                    }
                }
            }

            // 2. Lao bám vào cá lớn trong PvE (level >= localPlayer.sizeLevel)
            if (!localPlayer.isAttached && entityManager) {
                for (let f of entityManager.fishes) {
                    if (!f.eaten && f.level >= localPlayer.sizeLevel) {
                        const distToFish = Math.hypot(pX - f.getCenterX(), pY - f.getCenterY());
                        if (distToFish < pR + f.getRadius() + 25) {
                            localPlayer.attachToHost(f, 'fish');
                            entityManager.spawnBubbles(pX, pY, 25, '#00ffc4');
                            if (window.showToast) window.showToast(`🔗 ĐÃ KÝ SINH CÁ LỚN LV.${f.level}! Bạn đang trực tiếp điều khiển hướng bơi & mượn kích thước!`);
                            break;
                        }
                    }
                }
            }
        }

        // 5. KIỂM TRA VA CHẠM ĐỐI KHÁNG GIỮA 2 NGƯỜI CHƠI (PVP COLLISION)
        checkPvPCollision();

        // 6. GỬI ĐỒNG BỘ TỌA ĐỘ VỊ TRÍ CHO ĐỐI THỦ (TICK RATE ~40Hz CÓ DELTA CHECK)
        syncTimer += dt;
        if (syncTimer >= 0.025 && localPlayer && realtimeChannel) {
            const curX = Math.round(localPlayer.x);
            const curY = Math.round(localPlayer.y);
            const curAngle = Number(localPlayer.angle.toFixed(3));

            const deltaX = Math.abs(curX - lastSentX);
            const deltaY = Math.abs(curY - lastSentY);
            const deltaAngle = Math.abs(curAngle - lastSentAngle);

            // Chỉ gửi khi có di chuyển hoặc xoay góc, hoặc định kỳ mỗi 0.2s để chống flood mạng
            if (deltaX > 1.0 || deltaY > 1.0 || deltaAngle > 0.015 || syncTimer >= 0.2) {
                syncTimer = 0;
                lastSentX = curX;
                lastSentY = curY;
                lastSentAngle = curAngle;

                realtimeChannel.send('PLAYER_MOVE', {
                    x: curX,
                    y: curY,
                    angle: curAngle,
                    sizeLevel: localPlayer.getEffectiveSizeLevel(),
                    score: localPlayer.score,
                    bite: localPlayer.biteAnimTimer > 0
                });
            }
        }

        // 7. CẬP NHẬT CAMERA MƯỢT MÀ BÁM THEO NGƯỜI CHƠI HIỆN TẠI
        if (localPlayer && canvas) {
            const targetCamX = localPlayer.getCenterX() - canvas.width / 2;
            const targetCamY = localPlayer.getCenterY() - canvas.height / 2;
            cameraX += (targetCamX - cameraX) * Math.min(1, 10 * dt);
            cameraY += (targetCamY - cameraY) * Math.min(1, 10 * dt);

            // Giới hạn camera trong lòng bản đồ
            cameraX = Math.max(0, Math.min(MAP_WIDTH - canvas.width, cameraX));
            cameraY = Math.max(0, Math.min(MAP_HEIGHT - canvas.height, cameraY));
        }

        // 8. CẬP NHẬT GIAO DIỆN HUD
        updateHUD();
    }

    // =========================================================================
    // VA CHẠM SINH TỬ GIỮA 2 NGƯỜI CHƠI
    // =========================================================================
    function checkPvPCollision() {
        if (!localPlayer || !remotePlayer || localPlayer.dead || remotePlayer.dead) return;
        if (localPlayer.isAttached || remotePlayer.isAttached) return; // Đang ký sinh bám cá chủ: không bị nuốt
        // Nếu một trong hai người chơi đang là Cá Ép lao bám (isDashing): Không nuốt nhau để ưu tiên bám ký sinh
        if ((localPlayer.character && localPlayer.character.id === 4 && localPlayer.isDashing) ||
            (remotePlayer.character && remotePlayer.character.id === 4 && remotePlayer.isDashing)) {
            return;
        }

        const p1CenterX = localPlayer.getCenterX();
        const p1CenterY = localPlayer.getCenterY();
        const p2CenterX = remotePlayer.getCenterX();
        const p2CenterY = remotePlayer.getCenterY();

        const dx = p1CenterX - p2CenterX;
        const dy = p1CenterY - p2CenterY;
        const dist = Math.hypot(dx, dy);

        const combinedRadius = (localPlayer.getRadius() + remotePlayer.getRadius()) * 0.72;

        if (dist < combinedRadius) {
            // TRƯỜNG HỢP 1: BẰNG CẤP KÍCH THƯỚC (SIZE BẰNG NHAU) -> HÚC NHAU BẬT DỘI RA
            if (localPlayer.sizeLevel === remotePlayer.sizeLevel) {
                const now = Date.now();
                if (now - lastBumpTime > 400) {
                    lastBumpTime = now;
                    const bumpForce = 35;
                    const bumpAngle = Math.atan2(dy, dx);
                    const localBumpX = Math.cos(bumpAngle) * bumpForce;
                    const localBumpY = Math.sin(bumpAngle) * bumpForce;

                    localPlayer.applyBump(localBumpX, localBumpY);
                    entityManager.spawnBubbles((p1CenterX + p2CenterX) / 2, (p1CenterY + p2CenterY) / 2, 16, 'rgba(255, 255, 255, 0.9)');

                    if (realtimeChannel) {
                        realtimeChannel.send('PLAYERS_BUMP', {
                            bumpX: -localBumpX,
                            bumpY: -localBumpY
                        });
                    }
                }
            }
            // TRƯỜNG HỢP 2: CÁ LỚN NUỐT CÁ BÉ (CHÊNH LỆCH CẤP KÍCH THƯỚC)
            else if (localPlayer.sizeLevel > remotePlayer.sizeLevel) {
                // LOCAL PLAYER NUỐT CHỬNG ĐỐI THỦ (nếu đối thủ không bất tử, ví dụ đối thủ Bạch Tuộc đang ở trong mực)
                if (remotePlayer.isInvincible()) return;
                remotePlayer.dead = true;
                localPlayer.onEatPrey(500); // Thưởng 500 điểm khi ăn được người chơi/cá máy
                entityManager.spawnBubbles(p2CenterX, p2CenterY, 35, 'rgba(0, 255, 135, 0.9)');

                if (realtimeChannel) {
                    realtimeChannel.send('PLAYER_KILLED', {
                        killerId: localPlayer.id,
                        victimId: remotePlayer.id
                    });
                }

                if (isPvE && remotePlayer.isBot) {
                    if (window.showToast) {
                        window.showToast(`🏆 Bạn đã nuốt chửng ${remotePlayer.username}! (+500 điểm)`);
                    }
                    // Bot đối thủ sẽ tự động hồi sinh sau 12 giây tại vùng biển an toàn
                    const botRef = remotePlayer;
                    setTimeout(() => {
                        if (isRunning && isPvE && botRef && botRef.isBot && botRef.dead) {
                            botRef.dead = false;
                            botRef.hp = 100;
                            botRef.sizeLevel = 1;
                            botRef.fishEaten = 0;
                            respawnPlayerSafe(botRef);
                            if (window.showToast) {
                                window.showToast(`⚠️ Cá máy ${botRef.username} đã hồi sinh tại vùng biển xa!`);
                            }
                        }
                    }, 12000);
                } else {
                    endMatch(true, localPlayer.username);
                }
            } else if (localPlayer.sizeLevel < remotePlayer.sizeLevel) {
                // ĐỐI THỦ LỚN HƠN NUỐT CHỬNG BẢN THÂN (nếu bản thân không bất tử, ví dụ bản thân Bạch Tuộc đang ở trong mực)
                if (localPlayer.isInvincible()) return;

                if (currentGameMode === 'PVE') {
                    if (localPlayer.lives > 1) {
                        localPlayer.lives--;
                        localPlayer.hp = 100;
                        respawnPlayerSafe(localPlayer);
                        localPlayer.shieldTimer = 2.5;
                        entityManager.spawnBubbles(p1CenterX, p1CenterY, 35, 'rgba(255, 51, 102, 0.9)');
                        if (window.showToast) {
                            window.showToast(`💔 Bị cá máy ${remotePlayer.username} nuốt! Mất 1 mạng (Còn ${localPlayer.lives} mạng).`);
                        }
                        return;
                    }
                }

                localPlayer.dead = true;
                entityManager.spawnBubbles(p1CenterX, p1CenterY, 35, 'rgba(255, 51, 102, 0.9)');

                if (realtimeChannel) {
                    realtimeChannel.send('PLAYER_KILLED', {
                        killerId: remotePlayer.id,
                        victimId: localPlayer.id
                    });
                }
                endMatch(false, remotePlayer.username);
            }
        }
    }

    function updateHUD() {
        if (!localPlayer) return;

        // Cập nhật Avatar Người chơi theo đúng nhân vật đang điều khiển
        const hudAvatar = document.getElementById('hud-avatar');
        if (hudAvatar && localPlayer.character && localPlayer.character.img) {
            if (hudAvatar.getAttribute('src') !== localPlayer.character.img) {
                hudAvatar.src = localPlayer.character.img;
            }
        }

        // Cập nhật Máu & Mạng sống (HP & Lives)
        const hpFill = document.getElementById('hud-hp-fill');
        const hpText = document.getElementById('hud-hp-text');
        if (hpFill && hpText) {
            const hpPct = Math.max(0, Math.min(100, (localPlayer.hp / localPlayer.maxHp) * 100));
            hpFill.style.width = `${hpPct}%`;
            if (currentGameMode === 'PVE') {
                const hearts = '❤️'.repeat(Math.max(0, localPlayer.lives));
                hpText.innerText = `${hearts} ${Math.round(localPlayer.hp)} / ${localPlayer.maxHp} HP`;
            } else if (currentGameMode === 'CLASSIC') {
                hpText.innerText = `❤️ 1 MẠNG - BÁ CHỦ ĐẠI DƯƠNG`;
            } else {
                hpText.innerText = `${Math.round(localPlayer.hp)} / ${localPlayer.maxHp} HP`;
            }
        }

        // Điểm số & Cấp kích cỡ
        const scoreEl = document.getElementById('hud-score');
        if (scoreEl) scoreEl.innerText = localPlayer.score;

        const levelBadge = document.getElementById('hud-level-badge');
        if (levelBadge) {
            const effLevel = localPlayer.getEffectiveSizeLevel();
            levelBadge.innerText = `LV.${effLevel}`;
            if (localPlayer.baKhiTimer > 0) {
                levelBadge.style.background = 'linear-gradient(135deg, #ff0055, #ff5500)';
            } else {
                levelBadge.style.background = 'linear-gradient(135deg, #00f0ff, #0077ff)';
            }
        }

        // Tiến trình tiến hóa kích thước
        const xpFill = document.getElementById('hud-xp-fill');
        const xpText = document.getElementById('hud-xp-text');
        if (xpFill && xpText) {
            const curEaten = localPlayer.fishEaten;
            const req = localPlayer.fishPerSizeLevel || 10;
            const stepEaten = curEaten % req;
            const progress = localPlayer.sizeLevel >= 4 ? 100 : (stepEaten / req) * 100;
            xpFill.style.width = `${progress}%`;
            xpText.innerText = localPlayer.sizeLevel >= 4
                ? `MAX LV.4 (${curEaten} cá)`
                : `TIẾN HÓA: ${stepEaten}/${req} (Đã ăn ${curEaten})`;
        }

        // 1. CẬP NHẬT THANH MANA (PHASE 3)
        const manaBox = document.getElementById('hud-mana-box');
        const manaFill = document.getElementById('hud-mana-fill');
        const manaText = document.getElementById('hud-mana-text');
        if (manaFill && manaText) {
            const pct = Math.min(100, (localPlayer.mana / localPlayer.maxMana) * 100);
            manaFill.style.width = `${pct}%`;

            if (localPlayer.mana >= 5.0) {
                manaText.innerText = `MANA: 5.0 / 5.0 (SẴN SÀNG!)`;
                if (manaBox) manaBox.classList.add('mana-full-glow');
            } else {
                manaText.innerText = `MANA: ${localPlayer.mana.toFixed(1)} / 5.0`;
                if (manaBox) manaBox.classList.remove('mana-full-glow');
            }
        }

        // 2. CẬP NHẬT Ô KỸ NĂNG [Q] (MẶC ĐỊNH)
        const slotQ = document.getElementById('slot-q');
        const iconQ = document.getElementById('hud-skill-icon-q');
        const nameQ = document.getElementById('hud-skill-name-q');
        const cdQ = document.getElementById('skill-cooldown-q');

        if (slotQ && iconQ && nameQ && cdQ) {
            const isRemoraAttached = (localPlayer.character && localPlayer.character.id === 4 && localPlayer.isAttached);

            if (isRemoraAttached) {
                nameQ.innerText = 'TÁCH RA';
                cdQ.style.height = '0%';
                slotQ.classList.add('ready-glow');
                slotQ.title = `Kỹ năng [Q]: Chủ động tách khỏi cá chủ (Bấm Q hoặc click)`;
            } else {
                if (localPlayer.character) {
                    iconQ.src = localPlayer.character.skillImg || 'kĩ năng cá kiếm.jpg';
                    nameQ.innerText = localPlayer.character.skillName || 'Thủy Thần';
                }

                // Bóng mờ Cooldown dựa trên lượng Mana
                const cdPct = Math.max(0, (1 - localPlayer.mana / localPlayer.maxMana) * 100);
                cdQ.style.height = `${cdPct}%`;

                if (localPlayer.mana >= 5.0) {
                    slotQ.classList.add('ready-glow');
                    slotQ.title = `Kỹ năng [Q]: ${nameQ.innerText} (SẴN SÀNG! Bấm Q hoặc click để dùng)`;
                } else {
                    slotQ.classList.remove('ready-glow');
                    slotQ.title = `Kỹ năng [Q]: ${nameQ.innerText} (Cần thêm ${(5.0 - localPlayer.mana).toFixed(1)} Mana)`;
                }
            }
        }

        // 3. CẬP NHẬT Ô KỸ NĂNG [W] (NHẶT ĐƯỢC 1)
        const slotW = document.getElementById('slot-w');
        const iconW = document.getElementById('hud-skill-icon-w');
        const phW = document.getElementById('slot-placeholder-w');
        const nameW = document.getElementById('hud-skill-name-w');

        if (slotW && iconW && phW && nameW) {
            if (localPlayer.slotW) {
                slotW.classList.remove('empty');
                iconW.src = localPlayer.slotW.icon;
                iconW.classList.remove('hidden');
                phW.classList.add('hidden');
                nameW.innerText = localPlayer.slotW.name;
                slotW.title = `Kỹ năng [W]: ${localPlayer.slotW.name} (Bấm phím W hoặc click để kích hoạt)`;
            } else {
                slotW.classList.add('empty');
                iconW.classList.add('hidden');
                phW.classList.remove('hidden');
                nameW.innerText = 'Trống';
                slotW.title = 'Kỹ năng nhặt [W] (Trống)';
            }
        }

        // 4. CẬP NHẬT Ô KỸ NĂNG [E] (NHẶT ĐƯỢC 2)
        const slotE = document.getElementById('slot-e');
        const iconE = document.getElementById('hud-skill-icon-e');
        const phE = document.getElementById('slot-placeholder-e');
        const nameE = document.getElementById('hud-skill-name-e');

        if (slotE && iconE && phE && nameE) {
            if (localPlayer.slotE) {
                slotE.classList.remove('empty');
                iconE.src = localPlayer.slotE.icon;
                iconE.classList.remove('hidden');
                phE.classList.add('hidden');
                nameE.innerText = localPlayer.slotE.name;
                slotE.title = `Kỹ năng [E]: ${localPlayer.slotE.name} (Bấm phím E hoặc click để kích hoạt)`;
            } else {
                slotE.classList.add('empty');
                iconE.classList.add('hidden');
                phE.classList.remove('hidden');
                nameE.innerText = 'Trống';
                slotE.title = 'Kỹ năng nhặt [E] (Trống)';
            }
        }

        // 5. BANNER BÁ KHÍ & KÝ SINH (CÁ ÉP)
        const frenzyBanner = document.getElementById('hud-frenzy-banner');
        if (frenzyBanner) {
            if (localPlayer.baKhiTimer > 0) {
                frenzyBanner.classList.remove('hidden');
                frenzyBanner.innerHTML = `⚡ <b>SIÊU BÁ KHÍ</b> (BẤT TỬ & TĂNG TỐC 60%): <b>${localPlayer.baKhiTimer.toFixed(1)}s</b> ⚡`;
            } else if (localPlayer.character && localPlayer.character.id === 5 && localPlayer.inkTimer > 0) {
                frenzyBanner.classList.remove('hidden');
                const inkStatus = localPlayer.inInkBuff ? '✨ TRONG MỰC: BẤT TỬ & TĂNG TỐC 50%' : '💨 NGOÀI MỰC: TỐC ĐỘ CHUẨN';
                frenzyBanner.innerHTML = `🐙 <b>VÙNG MỰC HẮC ÁM</b> (${inkStatus}): <b>${localPlayer.inkTimer.toFixed(1)}s</b> 🐙`;
            } else if (localPlayer.shockWaveTimer > 0) {
                frenzyBanner.classList.remove('hidden');
                frenzyBanner.innerHTML = `⚡ <b>VÙNG ĐIỆN TÊ LIỆT</b> (CHOÁNG 0.5s MỖI 1s): <b>${localPlayer.shockWaveTimer.toFixed(1)}s</b> ⚡`;
            } else if (localPlayer.character && localPlayer.character.id === 4 && localPlayer.isAttached) {
                const hostLvl = localPlayer.getEffectiveSizeLevel();
                frenzyBanner.classList.remove('hidden');
                frenzyBanner.innerHTML = `🔗 <b>ĐANG KÝ SINH [CÁ CHỦ LV.${hostLvl}]</b> (ĐIỀU KHIỂN & MƯỢN SIZE): <b>${localPlayer.attachedTimer.toFixed(1)}s</b> (Bấm Q: Tách) 🔗`;
            } else {
                frenzyBanner.classList.add('hidden');
            }
        }

        // 5.1 CẬP NHẬT BADGE ĐỘ KHÓ & TRẠNG THÁI HỖ TRỢ DDA TRÊN TOP HUD PILL
        const hudModePill = document.getElementById('hud-mode-pill');
        if (hudModePill && currentGameMode === 'PVE') {
            const diffMap = { 'EASY': 'DỄ', 'MEDIUM': 'VỪA', 'HARD': 'KHÓ' };
            const diffLabel = diffMap[pveDifficulty] || 'VỪA';
            let ddaHint = '⚖️ CÂN BẰNG';
            if (ddaStatus === 'ASSISTING') ddaHint = '💚 HỖ TRỢ BƠI CHẬM';
            else if (ddaStatus === 'CHALLENGING') ddaHint = '⚡ THỬ THÁCH TĂNG TỐC';
            hudModePill.innerText = `🤖 PvE [${diffLabel}] • ${ddaHint}`;
        }

        // 6. CẬP NHẬT KHUNG CHỈ SỐ CÁ MÁY (BOT ĐỐI THỦ CHẾ ĐỘ PVE) GÓC TRÊN BÊN PHẢI
        const hudBotCard = document.getElementById('hud-bot-card');
        if (hudBotCard) {
            if (isPvE && remotePlayer && !remotePlayer.dead && remotePlayer.isBot) {
                hudBotCard.classList.remove('hidden');

                const botAvatar = document.getElementById('hud-bot-avatar');
                if (botAvatar && remotePlayer.character && remotePlayer.character.img) {
                    if (botAvatar.getAttribute('src') !== remotePlayer.character.img) {
                        botAvatar.src = remotePlayer.character.img;
                    }
                }

                const botName = document.getElementById('hud-bot-name');
                if (botName) {
                    botName.innerText = remotePlayer.username || 'Cá Máy';
                }

                const botDiffBadge = document.getElementById('hud-bot-diff-badge');
                if (botDiffBadge) {
                    const diffMap = { 'EASY': 'DỄ', 'MEDIUM': 'VỪA', 'HARD': 'KHÓ' };
                    botDiffBadge.innerText = diffMap[pveDifficulty] || 'VỪA';
                    botDiffBadge.className = `bot-diff-tag diff-tag-${(pveDifficulty || 'medium').toLowerCase()}`;
                }

                const botLevelBadge = document.getElementById('hud-bot-level-badge');
                if (botLevelBadge) {
                    botLevelBadge.innerText = `LV.${remotePlayer.getEffectiveSizeLevel()}`;
                }

                const botHpFill = document.getElementById('hud-bot-hp-fill');
                const botHpText = document.getElementById('hud-bot-hp-text');
                if (botHpFill && botHpText) {
                    const botHpPct = Math.max(0, Math.min(100, (remotePlayer.hp / remotePlayer.maxHp) * 100));
                    botHpFill.style.width = `${botHpPct}%`;
                    botHpText.innerText = `${Math.round(remotePlayer.hp)} / ${remotePlayer.maxHp} HP`;
                }

                const botXpFill = document.getElementById('hud-bot-xp-fill');
                const botXpText = document.getElementById('hud-bot-xp-text');
                if (botXpFill && botXpText) {
                    const curEaten = remotePlayer.fishEaten || 0;
                    const req = remotePlayer.fishPerSizeLevel || 8;
                    const stepEaten = curEaten % req;
                    const progress = remotePlayer.sizeLevel >= 4 ? 100 : (stepEaten / req) * 100;
                    botXpFill.style.width = `${progress}%`;
                    botXpText.innerText = remotePlayer.sizeLevel >= 4
                        ? `MAX LV.4 (${curEaten} cá)`
                        : `TIẾN HÓA: ${stepEaten}/${req} (Đã ăn ${curEaten})`;
                }
            } else {
                hudBotCard.classList.add('hidden');
            }
        }
    }

    // =========================================================================
    // VẼ TOÀN BỘ KHUNG HÌNH (RENDER)
    // =========================================================================
    function render() {
        if (!ctx || !canvas) return;

        ctx.clearRect(0, 0, canvas.width, canvas.height);

        // 1. Vẽ nền đại dương sâu
        renderBackground();

        // 1.1 Vẽ vùng mực hắc ám của Bạch Tuộc (nằm dưới cá mồi và người chơi để tạo chiều sâu)
        renderInkZones();

        // 2. Vẽ đàn cá mồi & bọt nước
        if (entityManager) {
            entityManager.render(ctx, cameraX, cameraY);
        }

        // 3. Vẽ đối thủ từ xa
        if (remotePlayer) {
            remotePlayer.render(ctx, cameraX, cameraY);
        }

        // 4. Vẽ người chơi của máy này
        if (localPlayer) {
            localPlayer.render(ctx, cameraX, cameraY);
        }

        // 5. Vẽ Minimap góc màn hình
        renderMinimap();
    }

    function renderBackground() {
        // 1. Vẽ hình nền bản đồ thế giới đại dương (map.png bao phủ toàn bộ MAP_WIDTH x MAP_HEIGHT)
        if (mapImg.complete && mapImg.naturalWidth > 0) {
            ctx.drawImage(mapImg, -cameraX, -cameraY, MAP_WIDTH, MAP_HEIGHT);
        } else {
            // Nền nước gradient sâu dự phòng nếu ảnh đang load
            const grad = ctx.createLinearGradient(0, 0, 0, canvas.height);
            grad.addColorStop(0, '#001a33');
            grad.addColorStop(1, '#000814');
            ctx.fillStyle = grad;
            ctx.fillRect(0, 0, canvas.width, canvas.height);
        }

        // 2. Khung viền thế giới đại dương (1920x1080)
        ctx.save();
        ctx.strokeStyle = 'rgba(0, 240, 255, 0.45)';
        ctx.lineWidth = 6;
        ctx.strokeRect(-cameraX, -cameraY, MAP_WIDTH, MAP_HEIGHT);

        // 3. Lưới tọa độ hải đồ mờ tinh tế
        ctx.strokeStyle = 'rgba(0, 210, 255, 0.08)';
        ctx.lineWidth = 1;
        for (let x = 0; x <= MAP_WIDTH; x += 120) {
            ctx.beginPath();
            ctx.moveTo(x - cameraX, -cameraY);
            ctx.lineTo(x - cameraX, MAP_HEIGHT - cameraY);
            ctx.stroke();
        }
        for (let y = 0; y <= MAP_HEIGHT; y += 120) {
            ctx.beginPath();
            ctx.moveTo(-cameraX, y - cameraY);
            ctx.lineTo(MAP_WIDTH - cameraX, y - cameraY);
            ctx.stroke();
        }
        ctx.restore();
    }

    /**
     * Vẽ vùng mây mực hắc ám (Bạch Tuộc - 5.0s, R=220px) cho cả Local và Remote Player
     */
    function renderInkZones() {
        const playersWithInk = [];
        if (localPlayer && localPlayer.inkTimer > 0) playersWithInk.push(localPlayer);
        if (remotePlayer && remotePlayer.inkTimer > 0) playersWithInk.push(remotePlayer);

        if (playersWithInk.length === 0) return;

        ctx.save();
        playersWithInk.forEach(p => {
            const inkScrX = p.inkX - cameraX;
            const inkScrY = p.inkY - cameraY;
            const inkR = p.INK_ZONE_RADIUS || 220;
            const alpha = Math.min(1.0, p.inkTimer / 0.8);
            const now = Date.now();

            // Mây mực hắc ám nhiều lớp
            const inkGrad = ctx.createRadialGradient(inkScrX, inkScrY, inkR * 0.15, inkScrX, inkScrY, inkR);
            inkGrad.addColorStop(0, `rgba(5, 5, 15, ${0.94 * alpha})`);
            inkGrad.addColorStop(0.5, `rgba(15, 10, 30, ${0.85 * alpha})`);
            inkGrad.addColorStop(0.8, `rgba(22, 16, 40, ${0.68 * alpha})`);
            inkGrad.addColorStop(1, `rgba(10, 5, 20, 0)`);

            ctx.beginPath();
            ctx.arc(inkScrX, inkScrY, inkR, 0, Math.PI * 2);
            ctx.fillStyle = inkGrad;
            ctx.shadowColor = '#000000';
            ctx.shadowBlur = 35;
            ctx.fill();

            // Vòng viền ma mị xoay tròn
            ctx.beginPath();
            ctx.arc(inkScrX, inkScrY, inkR * 0.98, 0, Math.PI * 2);
            ctx.strokeStyle = `rgba(168, 85, 247, ${0.45 * alpha})`;
            ctx.lineWidth = 2.0;
            ctx.setLineDash([6, 10]);
            ctx.lineDashOffset = -(now / 60);
            ctx.stroke();

            // Các bong bóng khói mực đen trôi lơ lửng bên trong
            ctx.fillStyle = `rgba(30, 20, 50, ${0.5 * alpha})`;
            for (let i = 0; i < 5; i++) {
                const bAngle = (i / 5) * Math.PI * 2 + (now / 1200);
                const bDist = (inkR * 0.45) + Math.sin(now / 400 + i) * 25;
                const bx = inkScrX + Math.cos(bAngle) * bDist;
                const by = inkScrY + Math.sin(bAngle) * bDist;
                ctx.beginPath();
                ctx.arc(bx, by, 18, 0, Math.PI * 2);
                ctx.fill();
            }
        });
        ctx.restore();
    }

    function renderMinimap() {
        if (!minimapCtx || !minimapCanvas) return;

        minimapCtx.clearRect(0, 0, minimapCanvas.width, minimapCanvas.height);
        minimapCtx.fillStyle = 'rgba(3, 15, 35, 0.9)';
        minimapCtx.fillRect(0, 0, minimapCanvas.width, minimapCanvas.height);

        const scaleX = minimapCanvas.width / MAP_WIDTH;
        const scaleY = minimapCanvas.height / MAP_HEIGHT;

        // Vẽ cá mồi (chấm nhỏ màu ngọc bích)
        if (entityManager) {
            minimapCtx.fillStyle = '#00ff87';
            entityManager.fishes.forEach(f => {
                if (!f.eaten) {
                    minimapCtx.fillRect(f.x * scaleX, f.y * scaleY, 2, 2);
                }
            });
        }

        // Vẽ đối thủ: Cá máy (Bot PvE) hoặc Đối thủ người chơi (PvP)
        if (remotePlayer && !remotePlayer.dead) {
            const rx = remotePlayer.getCenterX() * scaleX;
            const ry = remotePlayer.getCenterY() * scaleY;

            if (isPvE && remotePlayer.isBot) {
                // 1. BIỂU TƯỢNG ĐẶC BIỆT RIÊNG CHO CÁ MÁY (BOT ĐỐI THỦ CHẾ ĐỘ PVE):
                // Vòng radar xung kích nhấp nháy phát hiện mục tiêu
                const pulse = (Math.sin(Date.now() / 160) + 1) * 0.5; // 0..1
                minimapCtx.save();
                minimapCtx.strokeStyle = `rgba(255, 0, 119, ${0.4 + pulse * 0.55})`;
                minimapCtx.lineWidth = 1.5;
                minimapCtx.beginPath();
                minimapCtx.arc(rx, ry, 5 + pulse * 2.5, 0, Math.PI * 2);
                minimapCtx.stroke();

                // Biểu tượng hình thoi (kim cương) màu neon đỏ tím sắc nét
                minimapCtx.fillStyle = '#ff0077';
                minimapCtx.beginPath();
                minimapCtx.moveTo(rx, ry - 5.5);
                minimapCtx.lineTo(rx + 5, ry);
                minimapCtx.lineTo(rx, ry + 5.5);
                minimapCtx.lineTo(rx - 5, ry);
                minimapCtx.closePath();
                minimapCtx.fill();

                // Điểm tâm sáng trắng rực rỡ để nổi bật trên nền bản đồ nhỏ
                minimapCtx.fillStyle = '#ffffff';
                minimapCtx.beginPath();
                minimapCtx.arc(rx, ry, 1.8, 0, Math.PI * 2);
                minimapCtx.fill();
                minimapCtx.restore();
            } else {
                // Đối thủ người chơi trong chế độ PvP (chấm tròn đỏ)
                minimapCtx.fillStyle = '#ff3366';
                minimapCtx.beginPath();
                minimapCtx.arc(rx, ry, 3.5, 0, Math.PI * 2);
                minimapCtx.fill();
            }
        }

        // Vẽ người chơi hiện tại (chấm xanh dương phát sáng)
        if (localPlayer && !localPlayer.dead) {
            minimapCtx.fillStyle = '#00d2ff';
            minimapCtx.beginPath();
            minimapCtx.arc(localPlayer.getCenterX() * scaleX, localPlayer.getCenterY() * scaleY, 3.5, 0, Math.PI * 2);
            minimapCtx.fill();
        }
    }

    // =========================================================================
    // KẾT THÚC TRẬN ĐẤU (VICTORY / DEFEAT) - HỖ TRỢ 4 CHẾ ĐỘ
    // =========================================================================
    function endMatch(isVictory, winnerName) {
        isRunning = false;
        if (window.SoundManager) {
            window.SoundManager.playGameOver();
        }

        setTimeout(() => {
            const modalOver = document.getElementById('modal-gameover');
            if (modalOver) {
                const title = modalOver.querySelector('.gameover-title');
                const subtitle = modalOver.querySelector('.gameover-subtitle');
                const score = document.getElementById('final-score');
                const level = document.getElementById('final-level');
                const eaten = document.getElementById('final-eaten');

                if (isVictory) {
                    if (currentGameMode === 'TIMED') {
                        if (title) {
                            title.innerText = '⏱️ HOÀN THÀNH ĐUA ĐIỂM 3 PHÚT!';
                            title.style.color = '#ffaa00';
                        }
                        if (subtitle && localPlayer) {
                            subtitle.innerText = `Kỷ lục đại dương: ${localPlayer.score} điểm - Bắt được ${localPlayer.fishEaten} con cá!`;
                        }
                    } else if (currentGameMode === 'CLASSIC') {
                        if (title) {
                            title.innerText = '🏆 TIẾN HÓA BÁ CHỦ ĐẠI DƯƠNG!';
                            title.style.color = '#00ffcc';
                        }
                        if (subtitle) {
                            subtitle.innerText = 'Chúc mừng bạn đã đạt kích thước Max LV.4 và xưng vương Thủy Cung!';
                        }
                    } else {
                        if (title) {
                            title.innerText = '🏆 CHIẾN THẮNG QUANG VINH!';
                            title.style.color = '#ffd700';
                        }
                        if (subtitle) subtitle.innerText = `Bạn đã thống trị đại dương và giành chiến thắng!`;
                    }
                } else {
                    if (title) {
                        title.innerText = '💀 BẠN ĐÃ BỊ HẠ GỤC!';
                        title.style.color = '#ff3366';
                    }
                    if (subtitle) subtitle.innerText = winnerName || 'Hiểm họa biển sâu đã săn bạn thành công!';
                }

                if (score && localPlayer) score.innerText = localPlayer.score;
                if (level && localPlayer) level.innerText = `LV.${localPlayer.sizeLevel}`;
                if (eaten && localPlayer) eaten.innerText = localPlayer.fishEaten;

                modalOver.classList.remove('hidden');
            }
        }, 600);
    }

    // =========================================================================
    // KHỞI ĐỘNG CÁC CHẾ ĐỘ SOLO (CLASSIC, PVE, TIMED)
    // =========================================================================
    function startSoloMatch(mode, selectedChar, currentUser, diff) {
        isRunning = true;
        isPvE = (mode === 'PVE');
        currentGameMode = mode || 'PVE';
        currentUserRef = currentUser;
        remotePlayer = null;

        if (diff) {
            pveDifficulty = diff.toUpperCase();
        } else if (!pveDifficulty) {
            pveDifficulty = 'MEDIUM';
        }

        pveGameTime = 0.0;
        ddaCheckTimer = 0.0;
        ddaStatus = 'BALANCED';
        ddaSpawnNearTimer = 0.0;
        ddaFishSpeedMult = (pveDifficulty === 'EASY') ? 0.9 : ((pveDifficulty === 'HARD') ? 1.1 : 1.0);

        if (realtimeChannel) {
            realtimeChannel.close();
            realtimeChannel = null;
        }

        // Đóng các modal GameOver / Tạm dừng và ẩn banner cũ nếu có
        const modalGameOver = document.getElementById('modal-gameover');
        if (modalGameOver) modalGameOver.classList.add('hidden');
        const modalPause = document.getElementById('modal-pause');
        if (modalPause) modalPause.classList.add('hidden');
        const modalPveDiff = document.getElementById('modal-pve-difficulty');
        if (modalPveDiff) modalPveDiff.classList.add('hidden');
        const frenzyBanner = document.getElementById('hud-frenzy-banner');
        if (frenzyBanner) frenzyBanner.classList.add('hidden');

        entityManager = new window.EntityManager();
        entityManager.initEcosystem(null, currentGameMode);
        entityManager.fishSpeedMultiplier = ddaFishSpeedMult;
        entityManager.targetFishCount = (pveDifficulty === 'EASY') ? 30 : ((pveDifficulty === 'HARD') ? 26 : 28);
        entityManager.smallFishRatio = (pveDifficulty === 'EASY') ? 0.50 : ((pveDifficulty === 'HARD') ? 0.35 : 0.45);

        // Danh sách toàn bộ 7 Thủy Thần hợp lệ của game
        const allChars = (window.CHARACTERS_DATA && window.CHARACTERS_DATA.length > 0)
            ? window.CHARACTERS_DATA
            : [
                { id: 1, name: "Tôm", title: "Dịch Chuyển Định Hướng", img: "tôm.png", eatImg: "tôm ăn.png", skillImg: "kĩ năng tôm.jpg", skillName: "Dịch Chuyển Định Hướng" },
                { id: 2, name: "Cá Kiếm", title: "Lướt Thần Tốc", img: "cá kiếm.jpg", eatImg: "cá kiếm ăn.png", skillImg: "kĩ năng cá kiếm.jpg", skillName: "Lướt Thần Tốc" },
                { id: 3, name: "Cua", title: "Phóng Càng Đoạt Mạng", img: "cua.jpg", eatImg: "cua ăn.png", skillImg: "kĩ năng cua.jpg", skillName: "Phóng Càng Bắt Mồi" },
                { id: 4, name: "Cá Ép", title: "Lao Bám Ký Sinh", img: "cá ép.png", eatImg: "cá ép ăn.png", skillImg: "kĩ năng cá ép.jpg", skillName: "Lao Bám Ký Sinh" },
                { id: 5, name: "Bạch Tuộc", title: "Vùng Mực Hắc Ám", img: "bạch tuộc.jpg", eatImg: "bạch tuộc ăn.png", skillImg: "kĩ năng bạch tuộc.jpg", skillName: "Vùng Mực Hắc Ám" },
                { id: 6, name: "Cá Đuối Điện", title: "Xung Điện Định Kỳ", img: "cá đuối điện.png", eatImg: "cá đuối điện ăn.png", skillImg: "kĩ năng cá đuối điện.jpg", skillName: "Xung Điện Định Kỳ" },
                { id: 7, name: "Rùa Biển", title: "Mai Rùa Bất Hoại", img: "rùa.jpg", eatImg: "rùa ăn.png", skillImg: "kĩ năng rùa.jpg", skillName: "Mai Rùa Bất Hoại" }
            ];

        // 1. XÁC ĐỊNH NHÂN VẬT CHO NGƯỜI CHƠI:
        // - Chế độ PvE: Chọn HOÀN TOÀN NGẪU NHIÊN 1 Thủy Thần từ danh sách 7 nhân vật mỗi khi vào ván mới.
        // - Các chế độ khác (Classic, Timed, PvP): Giữ nguyên nhân vật người chơi đã chọn tại Carousel.
        let playerChar = selectedChar || allChars[1];
        if (isPvE) {
            const randPlayerIdx = Math.floor(Math.random() * allChars.length);
            playerChar = allChars[randPlayerIdx];
        }

        localPlayer = new window.Player({
            id: 'solo_player',
            username: (currentUser && currentUser.username) || 'Thủy Thần',
            character: playerChar,
            fishPerSizeLevel: 10,
            x: MAP_WIDTH / 2,
            y: MAP_HEIGHT / 2,
            angle: 0,
            isLocal: true
        });

        // Đặt người chơi ở vị trí xuất phát an toàn xa cá lớn (tương tự GameEngine.java)
        respawnPlayerSafe(localPlayer);

        // 2. NẾU LÀ CHẾ ĐỘ PVE: RANDOM HÓA ĐỘC LẬP CHO CÁ MÁY (BOT ĐỐI THỦ THỦY THẦN)
        if (isPvE) {
            // Chọn ngẫu nhiên độc lập 1 Thủy Thần cho Bot (ưu tiên tránh trùng với nhân vật của người chơi)
            const candidateBotChars = allChars.filter(c => c.id !== playerChar.id);
            const botPool = candidateBotChars.length > 0 ? candidateBotChars : allChars;
            const randBotIdx = Math.floor(Math.random() * botPool.length);
            const botChar = botPool[randBotIdx];

            remotePlayer = new window.Player({
                id: 'bot_pve_rival',
                username: `${botChar.name} (Bot)`,
                character: botChar,
                fishPerSizeLevel: 8,
                x: MAP_WIDTH * 0.75,
                y: MAP_HEIGHT * 0.5,
                angle: Math.PI,
                isLocal: false,
                isBot: true
            });
            remotePlayer.botBaseSpeedMult = (pveDifficulty === 'EASY') ? 0.85 : ((pveDifficulty === 'HARD') ? 1.15 : 1.0);
            respawnPlayerSafe(remotePlayer);

            const diffMap = { 'EASY': 'DỄ', 'MEDIUM': 'VỪA', 'HARD': 'KHÓ' };
            const diffLabel = diffMap[pveDifficulty] || 'VỪA';
            if (window.showToast) {
                window.showToast(`🤖 PvE [${diffLabel}]: Bạn là [${playerChar.name}], Bot đối thủ là [${botChar.name}]!`);
            }
        }

        // Camera bám theo người chơi
        cameraX = localPlayer.getCenterX() - canvas.width / 2;
        cameraY = localPlayer.getCenterY() - canvas.height / 2;

        // Callback khi Cua dùng Càng Sắt ăn cá mồi
        localPlayer.onClawEatFish = (fish) => {
            if (entityManager) {
                entityManager.spawnBubbles(fish.getCenterX(), fish.getCenterY(), 10, 'rgba(0, 240, 255, 0.8)');
            }
        };

        // Callback khi Cua cắp kéo đối thủ/bot về tới thân (xuyên mọi miễn nhiễm & kích thước)
        localPlayer.onClawEatPlayer = (victim) => {
            if (!victim || victim.dead) return;
            victim.dead = true;
            localPlayer.onEatPrey(500);
            if (entityManager) {
                entityManager.spawnBubbles(victim.getCenterX(), victim.getCenterY(), 35, 'rgba(255, 87, 34, 0.95)');
            }
            if (window.SoundManager) {
                window.SoundManager.playEat();
            }

            if (isPvE && victim.isBot) {
                if (window.showToast) {
                    window.showToast(`🦀 CÀNG SẮT NGHIỀN NÁT cá máy ${victim.username}! (+500 điểm)`);
                }
                const botRef = victim;
                setTimeout(() => {
                    if (isRunning && isPvE && botRef && botRef.isBot && botRef.dead) {
                        botRef.dead = false;
                        botRef.hp = 100;
                        botRef.sizeLevel = 1;
                        botRef.fishEaten = 0;
                        respawnPlayerSafe(botRef);
                        if (window.showToast) {
                            window.showToast(`⚠️ Cá máy ${botRef.username} đã hồi sinh tại vùng biển xa!`);
                        }
                    }
                }, 12000);
            } else {
                if (window.showToast) {
                    window.showToast(`🦀 CÀNG SẮT NGHIỀN NÁT ${victim.username}! (+500 điểm)`);
                }
                endMatch(true, localPlayer.username);
            }
        };

        // Callback khi Cá Ép kết thúc ký sinh hoặc tách ra: Đẩy lùi vật chủ
        localPlayer.onDetachPush = (host, hostType, oppositeAngle) => {
            if (entityManager) {
                entityManager.spawnBubbles(host.x + (host.width || 80) / 2, host.y + (host.height || 60) / 2, 25, '#00ffc4');
            }
            if (window.showToast) {
                window.showToast('💨 ĐÃ TÁCH KHỎI VẬT CHỦ! Đẩy văng vật chủ ra xa và lướt an toàn!');
            }
        };

        // Đóng toàn bộ modals để không che khuất màn hình chơi
        ['modal-pvp', 'modal-auth', 'modal-guide', 'modal-pause', 'modal-gameover', 'modal-pve-difficulty'].forEach(id => {
            const m = document.getElementById(id);
            if (m) m.classList.add('hidden');
        });

        // Chuyển màn hình từ Sảnh sang Game Canvas
        const lobbyScreen = document.getElementById('lobby-screen');
        const gameScreen = document.getElementById('game-screen');
        if (lobbyScreen) {
            lobbyScreen.classList.remove('active');
            lobbyScreen.classList.add('hidden');
        }
        if (gameScreen) {
            gameScreen.classList.remove('hidden');
            gameScreen.classList.add('active');
        }

        // Cập nhật Avatar & Tên lên HUD
        const hudAvatar = document.getElementById('hud-avatar');
        if (hudAvatar && localPlayer.character) {
            hudAvatar.src = localPlayer.character.img || 'cá kiếm.jpg';
        }

        // Cập nhật Badge Chế độ & Timer trên Top HUD
        const hudModePill = document.getElementById('hud-mode-pill');
        const hudTimerBox = document.getElementById('hud-timer-box');

        if (currentGameMode === 'TIMED') {
            timeRemaining = 180;
            if (hudModePill) hudModePill.innerText = '⏱️ ĐUA ĐIỂM 3 PHÚT';
            if (hudTimerBox) {
                hudTimerBox.classList.remove('hidden');
                const tText = document.getElementById('hud-timer-text');
                if (tText) tText.innerText = '03:00';
            }
            if (window.showToast) {
                window.showToast(`⏱️ Bắt đầu Đua Điểm: 180 giây đếm ngược! Hãy săn mồi lập kỷ lục điểm cao nhất.`);
            }
        } else if (currentGameMode === 'CLASSIC') {
            if (hudModePill) hudModePill.innerText = '🌟 CƠ BẢN: ĐẠT LV.4';
            if (hudTimerBox) hudTimerBox.classList.add('hidden');
            if (window.showToast) {
                window.showToast(`🌟 Bắt đầu Chơi Cơ Bản: Hãy ăn cá mồi để tiến hóa lên LV.4 và chiến thắng!`);
            }
        } else {
            // Chế độ PvE Sinh Tồn
            const diffMap = { 'EASY': 'DỄ', 'MEDIUM': 'VỪA', 'HARD': 'KHÓ' };
            const diffLabel = diffMap[pveDifficulty] || 'VỪA';
            if (hudModePill) hudModePill.innerText = `🤖 PvE [${diffLabel}] • ⚖️ CÂN BẰNG`;
            if (hudTimerBox) hudTimerBox.classList.add('hidden');
        }

        isPaused = false;
        lastTime = performance.now();
        requestAnimationFrame(gameLoop);
    }

    function startPvEMatch(selectedChar, currentUser, diff) {
        startSoloMatch('PVE', selectedChar, currentUser, diff);
    }

    // =========================================================================
    // XỬ LÝ ĐẤU LẠI (REMATCH) - PHASE 5
    // =========================================================================
    function handleRematchClick() {
        const modalGameOver = document.getElementById('modal-gameover');

        if (currentGameMode !== 'PVP') {
            if (modalGameOver) modalGameOver.classList.add('hidden');
            startSoloMatch(currentGameMode, localPlayer ? localPlayer.character : null, currentUserRef, pveDifficulty);
            return;
        }

        // PvP Rematch
        if (!realtimeChannel) {
            if (modalGameOver) modalGameOver.classList.add('hidden');
            returnToLobby();
            return;
        }

        rematchRequested = true;
        const btnRetry = document.getElementById('btn-retry');
        if (btnRetry) {
            btnRetry.innerText = '⏳ ĐANG CHỜ ĐỐI THỦ...';
            btnRetry.disabled = true;
        }

        realtimeChannel.send('REMATCH_REQUEST', {
            requesterId: localPlayer ? localPlayer.id : 'unknown'
        });

        if (window.showToast) window.showToast('⚔️ Đã gửi lời mời tái đấu tới đối thủ!');

        // Nếu đối thủ đã bấm Đấu lại trước đó -> bắt đầu ngay!
        if (remoteRematchRequested) {
            startPvPRematch();
        }
    }

    function startPvPRematch() {
        const modalGameOver = document.getElementById('modal-gameover');
        if (modalGameOver) modalGameOver.classList.add('hidden');

        rematchRequested = false;
        remoteRematchRequested = false;
        isRunning = true;

        const btnRetry = document.getElementById('btn-retry');
        if (btnRetry) {
            btnRetry.innerText = 'CHƠI LẠI';
            btnRetry.disabled = false;
        }

        // Vị trí xuất phát chuẩn
        const p1X = MAP_WIDTH * 0.25;
        const p1Y = MAP_HEIGHT * 0.5;
        const p2X = MAP_WIDTH * 0.75;
        const p2Y = MAP_HEIGHT * 0.5;

        if (isHost) {
            if (localPlayer) localPlayer.resetState(p1X, p1Y, 0);
            if (remotePlayer) remotePlayer.resetState(p2X, p2Y, Math.PI);

            if (entityManager) {
                entityManager.initEcosystem();
                if (realtimeChannel) {
                    realtimeChannel.send('SEED_ECOSYSTEM', { seedList: entityManager.getExportList() });
                }
            }
        } else {
            if (localPlayer) localPlayer.resetState(p2X, p2Y, Math.PI);
            if (remotePlayer) remotePlayer.resetState(p1X, p1Y, 0);
        }

        cameraX = localPlayer.getCenterX() - canvas.width / 2;
        cameraY = localPlayer.getCenterY() - canvas.height / 2;

        if (realtimeChannel) {
            realtimeChannel.send('REMATCH_ACCEPT', {
                hostId: isHost ? localPlayer.id : remotePlayer.id
            });
        }

        if (window.showToast) window.showToast('🔥 Trận tái đấu chính thức bắt đầu!');

        isPaused = false;
        lastTime = performance.now();
        requestAnimationFrame(gameLoop);
    }

    function stopGame() {
        isRunning = false;
        isPaused = false;
        if (localPlayer) {
            localPlayer.resetState();
        }
        if (realtimeChannel) {
            realtimeChannel.close();
            realtimeChannel = null;
        }
        const hudBotCard = document.getElementById('hud-bot-card');
        if (hudBotCard) {
            hudBotCard.classList.add('hidden');
        }
    }

    function returnToLobby() {
        // Thông báo ngắt kết nối cho đối thủ nếu đang trong PvP
        if (currentGameMode === 'PVP' && realtimeChannel && localPlayer) {
            try {
                realtimeChannel.send('PLAYER_DISCONNECTED', {
                    leaverId: localPlayer.id,
                    leaverName: localPlayer.username
                });
            } catch (e) {}
        }

        stopGame();

        ['modal-pvp', 'modal-auth', 'modal-guide', 'modal-pause', 'modal-gameover', 'modal-pve-difficulty'].forEach(id => {
            const m = document.getElementById(id);
            if (m) m.classList.add('hidden');
        });

        const gameScreen = document.getElementById('game-screen');
        const lobbyScreen = document.getElementById('lobby-screen');

        if (gameScreen) {
            gameScreen.classList.remove('active');
            gameScreen.classList.add('hidden');
        }
        if (lobbyScreen) {
            lobbyScreen.classList.remove('hidden');
            lobbyScreen.classList.add('active');
        }

        // Đóng phòng trên server
        const curRoom = window.RoomManager ? window.RoomManager.getCurrentRoom() : null;
        if (curRoom && window.RoomManager) {
            window.RoomManager.leaveRoom();
        }
    }

    return {
        init,
        startPvPMatch,
        startPvEMatch,
        startSoloMatch,
        stopGame,
        returnToLobby
    };
})();

window.GameController = GameController;
document.addEventListener('DOMContentLoaded', () => {
    GameController.init();
});
