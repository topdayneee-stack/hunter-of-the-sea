/**
 * HUNTER OF THE SEA - UI CONTROLLER & SẢNH ĐỐI KHÁNG PVP (PHASE 1)
 * Điều khiển giao diện: Danh sách 7 Thủy Thần, Đăng nhập/Đổi tài khoản,
 * Tạo phòng mã 4 số, Nhập mã vào phòng và Theo dõi vòng đời phòng.
 */

const CHARACTERS_DATA = [
    {
        id: 1,
        name: "Tôm",
        title: "Dịch Chuyển Định Hướng",
        desc: "Dịch chuyển tức thời theo hướng con trỏ chuột tới tầm 380px, kiểm tra an toàn chống kẹt biên.",
        img: "tôm.png",
        eatImg: "tôm ăn.png",
        skillImg: "kĩ năng tôm.jpg",
        skillName: "Dịch Chuyển Định Hướng",
        skillDesc: "Dịch chuyển tức thời theo vector trỏ chuột (tối đa 380px) kèm hiệu ứng sóng chấn lóe sáng và chống kẹt biên."
    },
    {
        id: 2,
        name: "Cá Kiếm",
        title: "Lướt Thần Tốc",
        desc: "Tăng mạnh tốc độ bơi gấp 2.2 lần trong 3.5 giây để săn mồi hoặc tẩu thoát.",
        img: "cá kiếm.jpg",
        eatImg: "cá kiếm ăn.png",
        skillImg: "kĩ năng cá kiếm.jpg",
        skillName: "Lướt Thần Tốc",
        skillDesc: "Lướt về phía trước xé toạc làn nước, nuốt trọn mọi con mồi nhỏ trên đường đi."
    },
    {
        id: 3,
        name: "Cua",
        title: "Phóng Càng Đoạt Mạng",
        desc: "Phóng càng sắt bay thẳng theo hướng chuột tới 480px, găm và kéo mục tiêu về phía mình kèm làm choáng.",
        img: "cua.jpg",
        eatImg: "cua ăn.png",
        skillImg: "kĩ năng cua.jpg",
        skillName: "Phóng Càng Bắt Mồi",
        skillDesc: "Phóng đôi càng sắt xích bay thẳng theo hướng chuột (480px), găm và kéo mạnh con mồi hoặc đối thủ về miệng."
    },
    {
        id: 4,
        name: "Cá Ép",
        title: "Lao Bám Ký Sinh",
        desc: "Bấm Q lao nhanh theo chuột bám vào cá lớn: trực tiếp điều khiển hướng bơi và mượn kích thước/cấp độ vật chủ (5s); hết giờ tách ra đẩy văng vật chủ.",
        img: "cá ép.png",
        eatImg: "cá ép ăn.png",
        skillImg: "kĩ năng cá ép.jpg",
        skillName: "Lao Bám Ký Sinh",
        skillDesc: "Lao nhanh theo chuột bám cá lớn: trực tiếp điều khiển vật chủ và mượn cấp độ/kích thước ăn mồi (5s); hết giờ tách ra an toàn và đẩy văng vật chủ."
    },
    {
        id: 5,
        name: "Bạch Tuộc",
        title: "Vùng Mực Hắc Ám",
        desc: "Phun vùng mây mực: Bản thân bất tử và tăng tốc, kẻ thù bị làm chậm và mù màn hình.",
        img: "bạch tuộc.jpg",
        eatImg: "bạch tuộc ăn.png",
        skillImg: "kĩ năng bạch tuộc.jpg",
        skillName: "Vùng Mực Hắc Ám",
        skillDesc: "Bắn ra màn sương mực đen che phủ tầm nhìn kẻ địch và tăng tốc tẩu thoát."
    },
    {
        id: 6,
        name: "Cá Đuối Điện",
        title: "Xung Điện Định Kỳ",
        desc: "Phóng xung điện định kỳ trong 3s, làm choáng toàn bộ cá ở cự ly gần.",
        img: "cá đuối điện.png",
        eatImg: "cá đuối điện ăn.png",
        skillImg: "kĩ năng cá đuối điện.jpg",
        skillName: "Xung Điện Định Kỳ",
        skillDesc: "Phóng ra các làn sóng điện từ làm tê liệt thần kinh mọi sinh vật xung quanh."
    },
    {
        id: 7,
        name: "Rùa Biển",
        title: "Mai Rùa Bất Hoại",
        desc: "Bất tử trong 4s; kẻ tấn công sẽ bị đẩy lùi mạnh và làm choáng 1s.",
        img: "rùa.jpg",
        eatImg: "rùa ăn.png",
        skillImg: "kĩ năng rùa.jpg",
        skillName: "Mai Rùa Bất Hoại",
        skillDesc: "Thu mình vào mai thần hộ thể, tạo lớp giáp phản chấn hất văng mọi đòn hiểm."
    }
];

// Xuất danh sách 7 Thủy Thần ra phạm vi toàn cục để game.js và các module truy cập
window.CHARACTERS_DATA = CHARACTERS_DATA;

let selectedCharacter = CHARACTERS_DATA[1]; // Mặc định Cá Kiếm

document.addEventListener('DOMContentLoaded', () => {
    initCharacterCarousel();
    initAuthUI();
    initPvPLobbyUI();
    initGeneralButtons();
});

// =========================================================================
// 1. CAROUSEL CHỌN NHÂN VẬT THỦY THẦN
// =========================================================================
function initCharacterCarousel() {
    const container = document.getElementById('character-carousel');
    if (!container) return;

    container.innerHTML = '';
    CHARACTERS_DATA.forEach(char => {
        const item = document.createElement('div');
        item.className = `char-item carousel-item ${char.id === selectedCharacter.id ? 'active' : ''}`;
        item.dataset.id = char.id;
        item.innerHTML = `
            <div class="char-thumb">
                <img src="${char.img}" alt="${char.name}">
            </div>
            <span class="char-thumb-name">${char.name}</span>
        `;
        item.addEventListener('click', () => selectCharacter(char));
        container.appendChild(item);
    });

    selectCharacter(selectedCharacter);
}

function selectCharacter(char) {
    selectedCharacter = char;

    document.querySelectorAll('.char-item, .carousel-item').forEach(el => {
        el.classList.toggle('active', parseInt(el.dataset.id) === char.id);
    });

    const charImg = document.getElementById('char-img');
    const charName = document.getElementById('char-name');
    const charDesc = document.getElementById('char-desc');
    const skillImg = document.getElementById('char-skill-img');
    const skillName = document.getElementById('char-skill-name');
    const skillDesc = document.getElementById('char-skill-desc');
    const avatar = document.getElementById('lobby-current-avatar');

    if (charImg) charImg.src = char.img;
    if (charName) charName.innerText = `${char.name} (${char.title})`;
    if (charDesc) charDesc.innerText = char.desc;
    if (skillImg) skillImg.src = char.skillImg;
    if (skillName) skillName.innerText = char.skillName;
    if (skillDesc) skillDesc.innerText = char.skillDesc;
    if (avatar) avatar.src = char.img;
}

// =========================================================================
// 2. MODAL & LUỒNG TÀI KHOẢN (PHÂN BIỆT PLAYER A & PLAYER B)
// =========================================================================
function initAuthUI() {
    window.AuthManager.init();

    const profileBadge = document.getElementById('player-profile-badge');
    const authModal = document.getElementById('modal-auth');
    const authClose = document.getElementById('close-auth');
    const authForm = document.getElementById('auth-form');
    const authInput = document.getElementById('auth-username-input');

    if (profileBadge) {
        profileBadge.addEventListener('click', () => {
            const cur = window.AuthManager.getCurrentUser();
            if (authInput) authInput.value = cur ? cur.username : '';
            openModal('modal-auth');
        });
    }

    if (authClose) {
        authClose.addEventListener('click', () => closeModal('modal-auth'));
    }

    // Nút chọn nhanh Player A và Player B để test tiện lợi
    const btnPickA = document.getElementById('btn-pick-player-a');
    const btnPickB = document.getElementById('btn-pick-player-b');
    if (btnPickA && authInput) {
        btnPickA.addEventListener('click', () => { authInput.value = 'Player A'; });
    }
    if (btnPickB && authInput) {
        btnPickB.addEventListener('click', () => { authInput.value = 'Player B'; });
    }

    if (authForm) {
        authForm.addEventListener('submit', async (e) => {
            e.preventDefault();
            const val = authInput.value.trim();
            if (!val) return showToast('Vui lòng nhập tên người chơi!');
            await window.AuthManager.login(val);
            closeModal('modal-auth');
            showToast(`Chào mừng Thủy Thủ: ${val}!`);
        });
    }
}

// =========================================================================
// 3. SẢNH ĐỐI KHÁNG PVP: TẠO PHÒNG MÃ 4 SỐ, NHẬP MÃ & THEO DÕI VÒNG ĐỜI
// =========================================================================
function initPvPLobbyUI() {
    const btnPvPMode = document.getElementById('btn-pvp-mode');
    const modalPvP = document.getElementById('modal-pvp');
    const closePvP = document.getElementById('close-pvp');

    if (btnPvPMode) {
        btnPvPMode.addEventListener('click', () => {
            resetPvPModalViews();
            openModal('modal-pvp');
        });
    }

    if (closePvP) {
        closePvP.addEventListener('click', async () => {
            if (window.RoomManager && window.RoomManager.getCurrentRoom()) {
                await window.RoomManager.leaveRoom();
            }
            resetPvPModalViews();
            closeModal('modal-pvp');
        });
    }

    // TAB SWITCHING: TẠO PHÒNG vs NHẬP MÃ
    const tabCreate = document.getElementById('pvp-tab-create');
    const tabJoin = document.getElementById('pvp-tab-join');
    const panelCreate = document.getElementById('pvp-panel-create');
    const panelJoin = document.getElementById('pvp-panel-join');

    if (tabCreate && tabJoin) {
        tabCreate.addEventListener('click', () => {
            tabCreate.classList.add('active');
            tabJoin.classList.remove('active');
            if (panelCreate) panelCreate.classList.remove('hidden');
            if (panelJoin) panelJoin.classList.add('hidden');
            clearPvPError();
        });

        tabJoin.addEventListener('click', () => {
            tabJoin.classList.add('active');
            tabCreate.classList.remove('active');
            if (panelJoin) panelJoin.classList.remove('hidden');
            if (panelCreate) panelCreate.classList.add('hidden');
            clearPvPError();
            const codeInput = document.getElementById('input-room-code');
            if (codeInput) codeInput.focus();
        });
    }

    // HÀNH ĐỘNG 1: BẤM TẠO PHÒNG
    const btnDoCreate = document.getElementById('btn-do-create-room');
    if (btnDoCreate) {
        btnDoCreate.addEventListener('click', async () => {
            clearPvPError();
            btnDoCreate.disabled = true;
            btnDoCreate.innerText = '⏳ Đang tạo phòng...';

            try {
                const room = await window.RoomManager.createRoom(selectedCharacter);
                showRoomView(room);
                showToast(`Đã tạo phòng [${room.roomCode}]. Hãy chia sẻ mã cho đối thủ!`);
            } catch (err) {
                showPvPError(err.message || 'Không thể tạo phòng lúc này');
            } finally {
                btnDoCreate.disabled = false;
                btnDoCreate.innerText = '👑 TẠO PHÒNG MÃ 4 SỐ';
            }
        });
    }

    // HÀNH ĐỘNG 2: BẤM NHẬP MÃ ĐỂ VÀO PHÒNG
    const btnDoJoin = document.getElementById('btn-do-join-room');
    const inputCode = document.getElementById('input-room-code');

    if (inputCode) {
        // Chỉ cho phép nhập chữ số, tối đa 4 ký tự
        inputCode.addEventListener('input', (e) => {
            e.target.value = e.target.value.replace(/\D/g, '').slice(0, 4);
        });

        inputCode.addEventListener('keydown', (e) => {
            if (e.key === 'Enter') {
                if (btnDoJoin) btnDoJoin.click();
            }
        });
    }

    if (btnDoJoin && inputCode) {
        btnDoJoin.addEventListener('click', async () => {
            clearPvPError();
            const code = inputCode.value.trim();

            if (!code || code.length !== 4) {
                return showPvPError('Vui lòng nhập đầy đủ mã phòng 4 chữ số (1000 - 9999)!');
            }

            btnDoJoin.disabled = true;
            btnDoJoin.innerText = '🚀 Đang vào phòng...';

            try {
                const room = await window.RoomManager.joinRoom(code, selectedCharacter);
                showRoomView(room);
                showToast(`Đã vào phòng [${room.roomCode}] thành công!`);
            } catch (err) {
                showPvPError(err.message || 'Lỗi khi vào phòng');
            } finally {
                btnDoJoin.disabled = false;
                btnDoJoin.innerText = '🚀 VÀO PHÒNG NGAY';
            }
        });
    }

    // SAO CHÉP MÃ PHÒNG (COPY TO CLIPBOARD)
    const btnCopyCode = document.getElementById('btn-copy-code');
    if (btnCopyCode) {
        btnCopyCode.addEventListener('click', () => {
            const ok = window.RoomManager.copyCodeToClipboard();
            if (ok) {
                showToast('📋 Đã sao chép mã phòng vào bộ nhớ tạm!');
            } else {
                showToast('Không thể tự động sao chép, hãy bôi đen mã để copy.');
            }
        });
    }

    // RỜI PHÒNG
    const btnLeaveRoom = document.getElementById('btn-leave-room');
    if (btnLeaveRoom) {
        btnLeaveRoom.addEventListener('click', async () => {
            if (window.RoomManager && window.RoomManager.getCurrentRoom()) {
                await window.RoomManager.leaveRoom();
            }
            resetPvPModalViews();
            closeModal('modal-pvp');
            showToast('Đã đóng phòng và về sảnh.');
        });
    }

    // CHƠI THỬ SOLO TỪ PHÒNG
    const btnStartSolo = document.getElementById('btn-start-pvp-solo');
    if (btnStartSolo) {
        btnStartSolo.addEventListener('click', async () => {
            if (window.RoomManager && window.RoomManager.getCurrentRoom()) {
                await window.RoomManager.leaveRoom();
            }
            resetPvPModalViews();
            closeModal('modal-pvp');
            const curUser = window.AuthManager ? window.AuthManager.getCurrentUser() : { username: 'Ngư Thủ' };
            if (window.GameController) {
                window.GameController.startSoloMatch('CLASSIC', selectedCharacter, curUser);
            }
        });
    }

    // BẮT ĐẦU TRẬN ĐẤU KHI 2 NGƯỜI CHƠI ĐÃ SẴN SÀNG
    const btnStartGame = document.getElementById('btn-start-pvp-game');
    if (btnStartGame) {
        btnStartGame.addEventListener('click', () => {
            const curRoom = window.RoomManager.getCurrentRoom();
            const curUser = window.AuthManager.getCurrentUser();
            if (curRoom && curUser) {
                closeModal('modal-pvp');
                if (window.GameApi) {
                    window.GameApi.broadcastGameStart(curRoom.roomCode);
                }
                if (window.GameController) {
                    window.GameController.startPvPMatch(curRoom, curUser);
                }
            }
        });
    }

    // LẮNG NGHE TÍN HIỆU BẮT ĐẦU TRẬN TỪ ĐỐI THỦ
    if (window.GameApi) {
        window.GameApi.onRoomEvent((code, room, eventType) => {
            if (eventType === 'GAME_START_SIGNAL') {
                const curRoom = window.RoomManager.getCurrentRoom();
                const curUser = window.AuthManager.getCurrentUser();
                if (curRoom && curRoom.roomCode === code && curUser) {
                    closeModal('modal-pvp');
                    if (window.GameController) {
                        window.GameController.startPvPMatch(curRoom, curUser);
                    }
                }
            }
        });
    }

    // LẮNG NGHE THAY ĐỔI TRẠNG THÁI PHÒNG TỪ ROOM MANAGER
    window.RoomManager.onRoomChange((room, eventType, extra) => {
        if (!room) {
            resetPvPModalViews();
            if (eventType === 'ROOM_CLOSED') {
                showPvPError(extra || 'Phòng đã kết thúc hoặc đối thủ đã rời đi.');
            }
            return;
        }
        showRoomView(room);
    });
}

function showRoomView(room) {
    const setupView = document.getElementById('pvp-setup-view');
    const roomView = document.getElementById('pvp-room-view');
    if (setupView) setupView.classList.add('hidden');
    if (roomView) roomView.classList.remove('hidden');

    const codeEl = document.getElementById('room-code-display-text');
    if (codeEl) codeEl.innerText = room.roomCode;

    const statusBadge = document.getElementById('room-status-badge');
    const p1Name = document.getElementById('room-p1-name');
    const p1Char = document.getElementById('room-p1-char');
    const p1Img = document.getElementById('room-p1-img');

    const p2WaitingBox = document.getElementById('room-p2-waiting');
    const p2JoinedBox = document.getElementById('room-p2-joined');
    const p2Name = document.getElementById('room-p2-name');
    const p2Char = document.getElementById('room-p2-char');
    const p2Img = document.getElementById('room-p2-img');

    // Host Player A
    if (room.host) {
        if (p1Name) p1Name.innerText = room.host.username;
        const char = room.hostCharacter || selectedCharacter;
        if (p1Char) p1Char.innerText = char.name || 'Thủy Thần';
        if (p1Img) p1Img.src = char.img || 'cá kiếm.jpg';
    }

    // Guest Player B
    if (room.guest) {
        if (p2WaitingBox) p2WaitingBox.classList.add('hidden');
        if (p2JoinedBox) p2JoinedBox.classList.remove('hidden');

        if (p2Name) p2Name.innerText = room.guest.username;
        const gChar = room.guestCharacter || selectedCharacter;
        if (p2Char) p2Char.innerText = gChar.name || 'Thủy Thần';
        if (p2Img) p2Img.src = gChar.img || 'tôm.png';

        if (statusBadge) {
            statusBadge.className = 'room-status-badge status-ready';
            statusBadge.innerHTML = '🟢 CẢ 2 ĐÃ SẴN SÀNG! (2/2) — ĐÃ KẾT NỐI';
        }

        const banner = document.getElementById('pvp-ready-banner');
        if (banner) banner.classList.remove('hidden');

        const btnStart = document.getElementById('btn-start-pvp-game');
        if (btnStart) btnStart.classList.remove('hidden');
    } else {
        if (p2WaitingBox) p2WaitingBox.classList.remove('hidden');
        if (p2JoinedBox) p2JoinedBox.classList.add('hidden');

        if (statusBadge) {
            statusBadge.className = 'room-status-badge status-waiting';
            statusBadge.innerHTML = '⏳ ĐANG CHỜ ĐỐI THỦ NHẬP MÃ (1/2)...';
        }

        const banner = document.getElementById('pvp-ready-banner');
        if (banner) banner.classList.add('hidden');

        const btnStart = document.getElementById('btn-start-pvp-game');
        if (btnStart) btnStart.classList.add('hidden');
    }
}

function resetPvPModalViews() {
    const setupView = document.getElementById('pvp-setup-view');
    const roomView = document.getElementById('pvp-room-view');
    if (setupView) setupView.classList.remove('hidden');
    if (roomView) roomView.classList.add('hidden');
    clearPvPError();
}

function showPvPError(msg) {
    const errBox = document.getElementById('pvp-error-banner');
    if (errBox) {
        errBox.innerText = `⚠️ ${msg}`;
        errBox.classList.remove('hidden');
    }
}

function clearPvPError() {
    const errBox = document.getElementById('pvp-error-banner');
    if (errBox) {
        errBox.innerText = '';
        errBox.classList.add('hidden');
    }
}

// =========================================================================
// 4. TIỆN ÍCH MODAL & TOAST
// =========================================================================
function openModal(id) {
    const modal = document.getElementById(id);
    if (modal) {
        modal.classList.remove('hidden');
    }
}

function closeModal(id) {
    const modal = document.getElementById(id);
    if (modal) {
        modal.classList.add('hidden');
        if (id === 'modal-pvp') {
            resetPvPModalViews();
            if (window.RoomManager && window.RoomManager.getCurrentRoom()) {
                window.RoomManager.leaveRoom();
            }
        }
    }
}

function showToast(message) {
    let toast = document.getElementById('hos-toast');
    if (!toast) {
        toast = document.createElement('div');
        toast.id = 'hos-toast';
        toast.className = 'hos-toast';
        document.body.appendChild(toast);
    }
    toast.innerText = message;
    toast.classList.add('show');
    clearTimeout(toast._timer);
    toast._timer = setTimeout(() => {
        toast.classList.remove('show');
    }, 3200);
}

function initGeneralButtons() {
    const btnGuide = document.getElementById('btn-guide');
    const closeGuide = document.getElementById('close-guide');
    const btnGuideOk = document.getElementById('btn-guide-ok');

    if (btnGuide) btnGuide.addEventListener('click', () => openModal('modal-guide'));
    if (closeGuide) closeGuide.addEventListener('click', () => closeModal('modal-guide'));
    if (btnGuideOk) btnGuideOk.addEventListener('click', () => closeModal('modal-guide'));

    const btnSound = document.getElementById('btn-sound');
    if (btnSound) {
        let soundOn = true;
        btnSound.addEventListener('click', () => {
            soundOn = !soundOn;
            btnSound.querySelector('.icon').innerText = soundOn ? '🔊' : '🔇';
            showToast(soundOn ? 'Âm thanh: BẬT' : 'Âm thanh: TẮT');
        });
    }

    const getCurUser = () => {
        return window.AuthManager ? window.AuthManager.getCurrentUser() : { username: 'Ngư Thủ' };
    };

    // 1. CHẾ ĐỘ CHƠI CƠ BẢN (CLASSIC - TIẾN HÓA ĐẠT LV.4)
    const btnClassic = document.getElementById('btn-classic-mode');
    if (btnClassic) {
        btnClassic.addEventListener('click', (e) => {
            e.stopPropagation();
            if (window.GameController) {
                window.GameController.startSoloMatch('CLASSIC', selectedCharacter, getCurUser());
            }
        });
    }

    // 2. CHẾ ĐỘ SINH TỒN PvE (NÉ BOM & CÁ ĐỘC) - MỞ MODAL CHỌN ĐỘ KHÓ DDA
    const modalPveDiff = document.getElementById('modal-pve-difficulty');
    const closePveDiff = document.getElementById('close-pve-diff');
    if (closePveDiff && modalPveDiff) {
        closePveDiff.addEventListener('click', () => {
            modalPveDiff.classList.add('hidden');
        });
    }

    function startPvEWithDiff(diff) {
        if (modalPveDiff) modalPveDiff.classList.add('hidden');
        if (window.GameController) {
            window.GameController.startSoloMatch('PVE', selectedCharacter, getCurUser(), diff);
        }
    }

    const btnDiffEasy = document.getElementById('btn-diff-easy');
    if (btnDiffEasy) {
        btnDiffEasy.addEventListener('click', () => startPvEWithDiff('EASY'));
    }
    const btnDiffMedium = document.getElementById('btn-diff-medium');
    if (btnDiffMedium) {
        btnDiffMedium.addEventListener('click', () => startPvEWithDiff('MEDIUM'));
    }
    const btnDiffHard = document.getElementById('btn-diff-hard');
    if (btnDiffHard) {
        btnDiffHard.addEventListener('click', () => startPvEWithDiff('HARD'));
    }

    const btnPve = document.getElementById('btn-pve-mode');
    if (btnPve) {
        btnPve.addEventListener('click', (e) => {
            e.stopPropagation();
            if (modalPveDiff) {
                modalPveDiff.classList.remove('hidden');
            } else if (window.GameController) {
                window.GameController.startSoloMatch('PVE', selectedCharacter, getCurUser(), 'MEDIUM');
            }
        });
    }

    // 3. CHẾ ĐỘ ĐUA ĐIỂM 3 PHÚT (TIMED - 180s ĐẾM NGƯỢC)
    const btnTimed = document.getElementById('btn-timed-mode');
    if (btnTimed) {
        btnTimed.addEventListener('click', (e) => {
            e.stopPropagation();
            if (window.GameController) {
                window.GameController.startSoloMatch('TIMED', selectedCharacter, getCurUser());
            }
        });
    }

    const cardPve = document.getElementById('card-pve');
    if (cardPve) {
        cardPve.addEventListener('click', () => {
            if (modalPveDiff) {
                modalPveDiff.classList.remove('hidden');
            } else if (window.GameController) {
                window.GameController.startSoloMatch('PVE', selectedCharacter, getCurUser(), 'MEDIUM');
            }
        });
    }

    // Tự động đóng modal khi click ra vùng nền đen bên ngoài (Backdrop click)
    document.querySelectorAll('.modal-overlay').forEach(overlay => {
        overlay.addEventListener('click', (e) => {
            if (e.target === overlay) {
                closeModal(overlay.id);
            }
        });
    });

    // Tự động đóng modal khi bấm phím Escape
    window.addEventListener('keydown', (e) => {
        if (e.key === 'Escape') {
            document.querySelectorAll('.modal-overlay:not(.hidden)').forEach(modal => {
                closeModal(modal.id);
            });
        }
    });
}
