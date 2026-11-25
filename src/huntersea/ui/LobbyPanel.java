package huntersea.ui;

import huntersea.*;
import huntersea.model.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.swing.*;

/**
 * Giao diện Sảnh Chờ (Lobby Screen) hiện đại phong cách Glassmorphism:
 * - Chọn 4 Chế độ chơi: Basic, PvE (Dễ/Vừa/Khó), PvP (2 người), High Score (3 phút/Vô tận)
 * - Nút chọn Thủy Thần nổi bật mở Modal/Overlay riêng biệt
 * - Hiển thị trạng thái kết nối Backend REST API (Online / Offline Fallback)
 * - Quản lý tài khoản, đồng bộ mở khóa 7 Thủy Thần và bảng xếp hạng online
 * - Cài đặt âm lượng & Hướng dẫn kỹ năng
 */
public class LobbyPanel extends JPanel {
    public interface LobbyActionListener {
        void onStartBasic(CharacterType character);
        void onStartPvE(AIBot.Difficulty diff);
        void onStartPvP(CharacterType p1, CharacterType p2);
        void onStartHighScore(boolean timed, CharacterType character);
    }

    private final LobbyActionListener listener;
    private final AssetManager assets = AssetManager.getInstance();
    private final HighScoreManager highScores = HighScoreManager.getInstance();
    private final BackendClient backend = BackendClient.getInstance();

    private CharacterType selectedCharacter = CharacterType.SWORDFISH;

    // Các thành phần giao diện chính
    private JPanel mainLobbyView;
    private JPanel topBar;
    private JPanel modesRow;
    private JButton charSelectBtn;

    // Thành phần Top Bar
    private JLabel recordLabel;
    private JLabel userProfileLabel;
    private JLabel serverStatusLabel;

    // Thành phần Modal chọn Thủy Thần
    private JPanel charSelectOverlay;
    private JPanel modalBox;
    private final List<JButton> charThumbnailButtons = new ArrayList<>();

    // Components hiển thị thông tin nhân vật trong Modal
    private JLabel charNameLabel;
    private JLabel charDescLabel;
    private JLabel charUnlockStatusLabel;
    private JLabel skillNameLabel;
    private JLabel skillDescLabel;
    private JLabel charPortraitLabel;
    private JLabel skillIconLabel;
    private JButton unlockCharBtn;
    private JButton selectCharActionBtn;

    public LobbyPanel(LobbyActionListener listener) {
        this.listener = listener;
        setLayout(null);
        setOpaque(false);

        buildUI();

        // Định kỳ kiểm tra kết nối Server mỗi 5 giây
        Timer statusTimer = new Timer(5000, e -> {
            backend.checkServerStatusAsync(online -> updateTopBarInfo());
        });
        statusTimer.start();
    }

    @Override
    public void doLayout() {
        int w = getWidth();
        int h = getHeight();
        if (mainLobbyView != null) {
            mainLobbyView.setBounds(0, 0, w, h);
        }
        if (charSelectOverlay != null) {
            charSelectOverlay.setBounds(0, 0, w, h);
        }
        super.doLayout();
    }

    private void buildUI() {
        // 1. Tạo modal chọn nhân vật (ẩn theo mặc định)
        charSelectOverlay = buildCharacterSelectionModal();
        charSelectOverlay.setVisible(false);

        // 2. Tạo giao diện chính của sảnh chờ
        mainLobbyView = buildMainLobbyView();

        // Cập nhật thông tin ban đầu
        updateTopBarInfo();
        updateCharSelectButton();
        updateCharacterDetails(selectedCharacter);

        // Thứ tự hiển thị: charSelectOverlay (trên cùng), mainLobbyView (bên dưới)
        add(charSelectOverlay);
        add(mainLobbyView);
    }

    private JPanel buildMainLobbyView() {
        JPanel view = new JPanel(null) {
            @Override
            public void doLayout() {
                super.doLayout();
                int w = getWidth();
                int h = getHeight();

                // 1. Top bar: neo ở đỉnh
                if (topBar != null) {
                    int topH = topBar.getPreferredSize().height;
                    topBar.setBounds(0, 0, w, Math.max(50, topH));
                }

                // 2. Hàng 4 Card Chế độ chơi: Dời xuống khu vực khoảng 52% - 56% chiều cao
                if (modesRow != null) {
                    Dimension modesPref = modesRow.getPreferredSize();
                    int modesY = (int) (h * 0.54);
                    if (modesY + modesPref.height + 75 > h) {
                        modesY = Math.max(70, h - modesPref.height - 80);
                    }
                    modesRow.setBounds(0, modesY, w, modesPref.height);

                    // 3. Nút Thủy Thần Săn Mồi: căn giữa ngay dưới hàng card chế độ
                    if (charSelectBtn != null) {
                        Dimension btnPref = charSelectBtn.getPreferredSize();
                        int btnW = Math.max(480, btnPref.width + 40);
                        int btnH = Math.max(48, btnPref.height);
                        int btnX = (w - btnW) / 2;
                        int btnY = modesY + modesPref.height + 22;
                        if (btnY + btnH > h - 12) {
                            btnY = h - btnH - 12;
                        }
                        charSelectBtn.setBounds(btnX, btnY, btnW, btnH);
                    }
                }
            }
        };
        view.setOpaque(false);

        // 1. TOP BAR: Kỷ lục, Trạng thái Server, Tài khoản, Bảng vàng, Cài đặt
        topBar = new JPanel(new BorderLayout());
        topBar.setOpaque(false);
        topBar.setBorder(BorderFactory.createEmptyBorder(12, 25, 8, 25));

        JPanel profileBox = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 4));
        profileBox.setOpaque(false);

        recordLabel = new JLabel("🏆 KỶ LỤC: " + highScores.getBestScore() + " ĐIỂM");
        recordLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        recordLabel.setForeground(new Color(255, 215, 0));
        profileBox.add(recordLabel);

        userProfileLabel = new JLabel("👤 " + backend.getCurrentUsername());
        userProfileLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        userProfileLabel.setForeground(new Color(180, 230, 255));
        profileBox.add(userProfileLabel);

        serverStatusLabel = new JLabel("⏳ Đang kết nối...");
        serverStatusLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        profileBox.add(serverStatusLabel);

        topBar.add(profileBox, BorderLayout.WEST);

        JPanel actionBox = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 4));
        actionBox.setOpaque(false);

        JButton accountBtn = createSmallIconButton("🌐 TÀI KHOẢN / CSDL");
        accountBtn.addActionListener(e -> showAccountDialog());
        actionBox.add(accountBtn);

        JButton boardBtn = createSmallIconButton("📜 BẢNG VÀNG");
        boardBtn.addActionListener(e -> showLeaderboardDialog());
        actionBox.add(boardBtn);

        JButton settingsBtn = createSmallIconButton("⚙️ CÀI ĐẶT & KỸ NĂNG");
        settingsBtn.addActionListener(e -> showSettingsDialog());
        actionBox.add(settingsBtn);

        topBar.add(actionBox, BorderLayout.EAST);
        view.add(topBar);

        // 2. Hàng 4 Card Chế độ chơi
        modesRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 10));
        modesRow.setOpaque(false);

        modesRow.add(createModeCard("CHƠI CƠ BẢN", "Ăn 10 cá lên 1 size. Né bom, ăn bá khí. Max size win!", 
            new Color(0, 180, 240), () -> checkAndStartMode(() -> listener.onStartBasic(selectedCharacter))));

        modesRow.add(createModeCard("CHƠI VỚI MÁY (PvE)", "Random nhân vật, bỏ bom. 3 mạng -> 1 mạng tử chiến!", 
            new Color(255, 100, 120), this::showPvEDifficultyDialog));

        modesRow.add(createModeCard("CHƠI 2 NGƯỜI (PvP)", "Player 1 vs Player 2. Nhặt kỹ năng rơi kịch tính!", 
            new Color(180, 70, 240), this::showPvPSelectionDialog));

        modesRow.add(createModeCard("CHƠI HIGH SCORE", "Chạy đua 3 phút hoặc sinh tồn vô hạn lập kỷ lục!", 
            new Color(255, 180, 0), this::showHighScoreModeDialog));

        view.add(modesRow);

        // 3. Nút nổi bật mở Modal chọn Thủy Thần
        charSelectBtn = createCharacterSelectButton();
        view.add(charSelectBtn);

        return view;
    }

    private void updateTopBarInfo() {
        if (recordLabel != null) {
            recordLabel.setText("🏆 KỶ LỤC: " + highScores.getBestScore() + " ĐIỂM");
        }
        if (userProfileLabel != null) {
            String uidStr = backend.getCurrentUserId() != null ? " (ID: #" + backend.getCurrentUserId() + ")" : "";
            userProfileLabel.setText("👤 " + backend.getCurrentUsername() + uidStr);
        }
        if (serverStatusLabel != null) {
            boolean online = backend.isServerOnline();
            if (online) {
                serverStatusLabel.setText("🟢 Online - Đã kết nối Server");
                serverStatusLabel.setForeground(new Color(0, 240, 120));
            } else {
                serverStatusLabel.setText("🟠 Offline Mode");
                serverStatusLabel.setForeground(new Color(255, 170, 50));
            }
        }
    }

    private void checkAndStartMode(Runnable startAction) {
        if (!backend.isCharacterUnlocked(selectedCharacter)) {
            int choice = JOptionPane.showConfirmDialog(this,
                "Thủy Thần " + selectedCharacter.getDisplayName() + " hiện chưa được mở khóa!\n" +
                "Bạn có muốn mở khóa nhân vật này ngay trên Server Backend không?",
                "NHÂN VẬT ĐANG BỊ KHÓA 🔒",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (choice == JOptionPane.YES_OPTION) {
                openCharacterSelectionModal();
            }
            return;
        }
        startAction.run();
    }

    private JButton createCharacterSelectButton() {
        JButton btn = new JButton() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(6, 26, 60, 220));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 24, 24);
                g2.setColor(new Color(0, 220, 255, 190));
                g2.setStroke(new BasicStroke(2.0f));
                g2.drawRoundRect(1, 1, getWidth() - 2, getHeight() - 2, 24, 24);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setOpaque(false);
        btn.setContentAreaFilled(false);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btn.setForeground(new Color(0, 240, 255));
        btn.setIconTextGap(12);
        btn.setBorder(BorderFactory.createEmptyBorder(6, 20, 6, 20));
        btn.addActionListener(e -> openCharacterSelectionModal());
        return btn;
    }

    private void updateCharSelectButton() {
        if (charSelectBtn == null) return;
        boolean unlocked = backend.isCharacterUnlocked(selectedCharacter);
        String lockStatus = unlocked ? "" : " [🔒 CHƯA MỞ]";
        charSelectBtn.setText("THỦY THẦN SĂN MỒI (Đang chọn: " + selectedCharacter.getDisplayName() + lockStatus + ")");
        BufferedImage img = assets.getImage(selectedCharacter.getSpriteKey());
        if (img != null) {
            Image scaled = img.getScaledInstance(34, 34, Image.SCALE_SMOOTH);
            charSelectBtn.setIcon(new ImageIcon(scaled));
        } else {
            charSelectBtn.setIcon(null);
        }
    }

    private JPanel buildCharacterSelectionModal() {
        JPanel overlay = new JPanel(null) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.setColor(new Color(2, 8, 20, 225));
                g.fillRect(0, 0, getWidth(), getHeight());
            }

            @Override
            public void doLayout() {
                super.doLayout();
                int w = getWidth();
                int h = getHeight();
                int modalW = Math.min(880, w - 40);
                int modalH = Math.min(500, h - 30);
                int modalX = (w - modalW) / 2;
                int modalY = (h - modalH) / 2;
                if (modalBox != null) {
                    modalBox.setBounds(modalX, modalY, modalW, modalH);
                }
            }
        };
        overlay.setOpaque(false);
        overlay.addMouseListener(new MouseAdapter() {});
        overlay.addMouseMotionListener(new MouseAdapter() {});

        modalBox = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(8, 24, 52, 245));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 24, 24);
                g2.setColor(new Color(0, 220, 255, 200));
                g2.setStroke(new BasicStroke(2.5f));
                g2.drawRoundRect(1, 1, getWidth() - 2, getHeight() - 2, 24, 24);
                g2.dispose();
            }
        };
        modalBox.setOpaque(false);
        modalBox.setLayout(new BoxLayout(modalBox, BoxLayout.Y_AXIS));
        modalBox.setBorder(BorderFactory.createEmptyBorder(18, 25, 18, 25));

        JLabel modalTitle = new JLabel("CHỌN THỦY THẦN ĐẠI DƯƠNG");
        modalTitle.setFont(new Font("Segoe UI", Font.BOLD, 22));
        modalTitle.setForeground(new Color(0, 240, 255));
        modalTitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        modalBox.add(modalTitle);
        modalBox.add(Box.createVerticalStrut(4));

        JLabel modalSubtitle = new JLabel("Đồng bộ 7 Thủy Thần với CSDL Backend. Người chơi mới được mở sẵn Tôm & Cá Kiếm.");
        modalSubtitle.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        modalSubtitle.setForeground(new Color(180, 220, 250));
        modalSubtitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        modalBox.add(modalSubtitle);
        modalBox.add(Box.createVerticalStrut(12));

        // Carousel 7 Thủy Thần
        JPanel carousel = new JPanel(new FlowLayout(FlowLayout.CENTER, 14, 5));
        carousel.setOpaque(false);
        charThumbnailButtons.clear();
        for (CharacterType ct : CharacterType.values()) {
            JButton charBtn = createCharThumbnailButton(ct);
            charThumbnailButtons.add(charBtn);
            carousel.add(charBtn);
        }
        modalBox.add(carousel);
        modalBox.add(Box.createVerticalStrut(12));

        // Thẻ thông tin chi tiết nhân vật & kỹ năng
        JPanel detailCard = buildCharacterDetailCard();
        modalBox.add(detailCard);
        modalBox.add(Box.createVerticalStrut(14));

        // Hàng nút tương tác
        JPanel bottomBtnRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 0));
        bottomBtnRow.setOpaque(false);

        selectCharActionBtn = new JButton("CHỌN LÀM THỦY THẦN CHIẾN ĐẤU");
        selectCharActionBtn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        selectCharActionBtn.setForeground(Color.WHITE);
        selectCharActionBtn.setBackground(new Color(0, 180, 240));
        selectCharActionBtn.setFocusPainted(false);
        selectCharActionBtn.setBorderPainted(false);
        selectCharActionBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        selectCharActionBtn.setPreferredSize(new Dimension(270, 36));
        selectCharActionBtn.addActionListener(e -> {
            if (!backend.isCharacterUnlocked(selectedCharacter)) {
                JOptionPane.showMessageDialog(this,
                    "Nhân vật này chưa mở khóa! Hãy bấm 'MỞ KHÓA THỦY THẦN' trước khi xuất trận.",
                    "CHƯA MỞ KHÓA", JOptionPane.WARNING_MESSAGE);
                return;
            }
            closeCharacterSelectionModal();
        });
        bottomBtnRow.add(selectCharActionBtn);

        unlockCharBtn = new JButton("MỞ KHÓA THỦY THẦN 🔓");
        unlockCharBtn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        unlockCharBtn.setForeground(Color.BLACK);
        unlockCharBtn.setBackground(new Color(255, 200, 40));
        unlockCharBtn.setFocusPainted(false);
        unlockCharBtn.setBorderPainted(false);
        unlockCharBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        unlockCharBtn.setPreferredSize(new Dimension(220, 36));
        unlockCharBtn.addActionListener(e -> handleUnlockSelectedCharacter());
        bottomBtnRow.add(unlockCharBtn);

        JButton closeBtn = new JButton("ĐÓNG");
        closeBtn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        closeBtn.setForeground(Color.WHITE);
        closeBtn.setBackground(new Color(70, 80, 100));
        closeBtn.setFocusPainted(false);
        closeBtn.setBorderPainted(false);
        closeBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        closeBtn.setPreferredSize(new Dimension(100, 36));
        closeBtn.addActionListener(e -> closeCharacterSelectionModal());
        bottomBtnRow.add(closeBtn);

        modalBox.add(bottomBtnRow);
        overlay.add(modalBox);
        return overlay;
    }

    private JPanel buildCharacterDetailCard() {
        JPanel detailCard = new JPanel(new BorderLayout(15, 10)) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(5, 20, 45, 210));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
                g2.setColor(new Color(0, 210, 255, 120));
                g2.setStroke(new BasicStroke(2));
                g2.drawRoundRect(1, 1, getWidth() - 2, getHeight() - 2, 20, 20);
                g2.dispose();
            }
        };
        detailCard.setOpaque(false);
        detailCard.setMaximumSize(new Dimension(780, 130));
        detailCard.setPreferredSize(new Dimension(780, 130));
        detailCard.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));
        detailCard.setAlignmentX(Component.CENTER_ALIGNMENT);

        charPortraitLabel = new JLabel();
        charPortraitLabel.setPreferredSize(new Dimension(85, 85));
        detailCard.add(charPortraitLabel, BorderLayout.WEST);

        JPanel infoMid = new JPanel();
        infoMid.setOpaque(false);
        infoMid.setLayout(new BoxLayout(infoMid, BoxLayout.Y_AXIS));

        JPanel nameStatusRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        nameStatusRow.setOpaque(false);

        charNameLabel = new JLabel();
        charNameLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        charNameLabel.setForeground(new Color(255, 215, 0));
        nameStatusRow.add(charNameLabel);

        charUnlockStatusLabel = new JLabel();
        charUnlockStatusLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        nameStatusRow.add(charUnlockStatusLabel);

        infoMid.add(nameStatusRow);
        infoMid.add(Box.createVerticalStrut(4));

        charDescLabel = new JLabel();
        charDescLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        charDescLabel.setForeground(new Color(200, 230, 255));
        infoMid.add(charDescLabel);
        infoMid.add(Box.createVerticalStrut(6));

        JPanel skillBox = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        skillBox.setOpaque(false);

        skillIconLabel = new JLabel();
        skillIconLabel.setPreferredSize(new Dimension(36, 36));
        skillBox.add(skillIconLabel);

        JPanel skillTxt = new JPanel();
        skillTxt.setOpaque(false);
        skillTxt.setLayout(new BoxLayout(skillTxt, BoxLayout.Y_AXIS));

        skillNameLabel = new JLabel();
        skillNameLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        skillNameLabel.setForeground(new Color(0, 240, 255));
        skillTxt.add(skillNameLabel);

        skillDescLabel = new JLabel();
        skillDescLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        skillDescLabel.setForeground(new Color(170, 210, 240));
        skillTxt.add(skillDescLabel);

        skillBox.add(skillTxt);
        infoMid.add(skillBox);

        detailCard.add(infoMid, BorderLayout.CENTER);
        return detailCard;
    }

    private void updateCharacterDetails(CharacterType ct) {
        this.selectedCharacter = ct;
        boolean unlocked = backend.isCharacterUnlocked(ct);

        if (charNameLabel != null) {
            charNameLabel.setText(ct.getDisplayName() + " [ID: " + ct.getDbId() + "]");
            charDescLabel.setText("Thông số chuẩn: Kích cỡ 2x2. Ăn " + backend.getConfigs().eatCountToLevelUp + " cá nhỏ để tiến hóa 1 cấp.");
            skillNameLabel.setText("Chiêu thức độc quyền: " + ct.getSkillName());
            skillDescLabel.setText(ct.getSkillDesc());
        }

        if (charUnlockStatusLabel != null) {
            if (unlocked) {
                charUnlockStatusLabel.setText("✅ ĐÃ MỞ KHÓA");
                charUnlockStatusLabel.setForeground(new Color(0, 255, 128));
            } else {
                charUnlockStatusLabel.setText("🔒 CHƯA MỞ KHÓA");
                charUnlockStatusLabel.setForeground(new Color(255, 180, 40));
            }
        }

        if (unlockCharBtn != null) {
            unlockCharBtn.setVisible(!unlocked);
        }
        if (selectCharActionBtn != null) {
            selectCharActionBtn.setEnabled(unlocked);
            selectCharActionBtn.setText(unlocked ? "CHỌN LÀM THỦY THẦN CHIẾN ĐẤU" : "KHÓA - HÃY MỞ KHÓA");
        }

        if (charPortraitLabel != null) {
            BufferedImage pImg = assets.getImage(ct.getSpriteKey());
            if (pImg != null) {
                Image scaled = pImg.getScaledInstance(75, 75, Image.SCALE_SMOOTH);
                charPortraitLabel.setIcon(new ImageIcon(scaled));
            }
        }

        if (skillIconLabel != null) {
            BufferedImage sImg = assets.getImage(ct.getSkillIconKey());
            if (sImg != null) {
                Image scaledS = sImg.getScaledInstance(34, 34, Image.SCALE_SMOOTH);
                skillIconLabel.setIcon(new ImageIcon(scaledS));
            }
        }

        updateThumbnailBorders();
        updateCharSelectButton();
    }

    private void handleUnlockSelectedCharacter() {
        int charId = selectedCharacter.getDbId();
        if (backend.isCharacterUnlocked(charId)) {
            JOptionPane.showMessageDialog(this, "Nhân vật này đã được mở khóa rồi!", "THÔNG BÁO", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        if (!backend.isServerOnline()) {
            int c = JOptionPane.showConfirmDialog(this,
                "Máy chủ Backend hiện đang Offline (hoặc chưa bật)!\n" +
                "Bạn có muốn mở khóa tạm thời nhân vật '" + selectedCharacter.getDisplayName() + "' ở chế độ Offline không?",
                "MỞ KHÓA OFFLINE", JOptionPane.YES_NO_OPTION);
            if (c == JOptionPane.YES_OPTION) {
                backend.getUnlockedCharacterIds().add(charId);
                updateCharacterDetails(selectedCharacter);
                JOptionPane.showMessageDialog(this, "Đã mở khóa Offline thành công!", "THÀNH CÔNG", JOptionPane.INFORMATION_MESSAGE);
            }
            return;
        }

        unlockCharBtn.setEnabled(false);
        unlockCharBtn.setText("Đang mở khóa...");

        backend.unlockCharacterAsync(charId, success -> {
            unlockCharBtn.setEnabled(true);
            unlockCharBtn.setText("MỞ KHÓA THỦY THẦN 🔓");
            if (success) {
                JOptionPane.showMessageDialog(this,
                    "Chúc mừng! Đã mở khóa thành công Thủy Thần: " + selectedCharacter.getDisplayName() + "\n" +
                    "Dữ liệu đã được lưu trữ an toàn trên CSDL Backend SQL Server!",
                    "MỞ KHÓA THÀNH CÔNG 🎉", JOptionPane.INFORMATION_MESSAGE);
                updateCharacterDetails(selectedCharacter);
            } else {
                JOptionPane.showMessageDialog(this,
                    "Không thể mở khóa trên Server! Vui lòng thử lại sau.",
                    "LỖI MỞ KHÓA", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    private void updateThumbnailBorders() {
        for (JButton b : charThumbnailButtons) {
            b.repaint();
        }
    }

    private void openCharacterSelectionModal() {
        // Tự động đồng bộ danh sách mở khóa mới nhất từ server nếu online
        backend.fetchUnlockedCharactersAsync(unlockedSet -> {
            updateCharacterDetails(selectedCharacter);
        });
        updateCharacterDetails(selectedCharacter);
        charSelectOverlay.setVisible(true);
        revalidate();
        repaint();
    }

    private void closeCharacterSelectionModal() {
        charSelectOverlay.setVisible(false);
        updateCharSelectButton();
        revalidate();
        repaint();
    }

    private JButton createCharThumbnailButton(CharacterType ct) {
        JButton btn = new JButton() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                boolean unlocked = backend.isCharacterUnlocked(ct);

                if (selectedCharacter == ct) {
                    g2.setColor(new Color(0, 220, 255, 70));
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                    g2.setColor(new Color(0, 240, 255));
                    g2.setStroke(new BasicStroke(2.2f));
                    g2.drawRoundRect(1, 1, getWidth() - 2, getHeight() - 2, 16, 16);
                } else {
                    g2.setColor(new Color(10, 30, 60, 150));
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                    g2.setColor(unlocked ? new Color(0, 180, 220, 90) : new Color(120, 130, 140, 60));
                    g2.setStroke(new BasicStroke(1.2f));
                    g2.drawRoundRect(1, 1, getWidth() - 2, getHeight() - 2, 16, 16);
                }

                super.paintComponent(g);

                // Nếu chưa mở khóa, vẽ icon ổ khóa 🔒 ở góc
                if (!unlocked) {
                    g2.setColor(new Color(0, 0, 0, 140));
                    g2.fillRoundRect(getWidth() - 22, 2, 20, 20, 8, 8);
                    g2.setColor(new Color(255, 200, 50));
                    g2.setFont(new Font("Segoe UI", Font.BOLD, 11));
                    g2.drawString("🔒", getWidth() - 20, 16);
                }
                g2.dispose();
            }
        };
        btn.setPreferredSize(new Dimension(65, 65));
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));

        BufferedImage img = assets.getImage(ct.getSpriteKey());
        if (img != null) {
            Image iconImg = img.getScaledInstance(55, 55, Image.SCALE_SMOOTH);
            btn.setIcon(new ImageIcon(iconImg));
        } else {
            btn.setText(ct.getDisplayName());
        }

        btn.addActionListener(e -> updateCharacterDetails(ct));
        return btn;
    }

    private JPanel createModeCard(String title, String desc, Color accent, Runnable action) {
        JPanel card = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(8, 25, 55, 220));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 22, 22);
                g2.setColor(accent);
                g2.setStroke(new BasicStroke(2));
                g2.drawRoundRect(1, 1, getWidth() - 2, getHeight() - 2, 22, 22);
            }
        };
        card.setOpaque(false);
        card.setPreferredSize(new Dimension(210, 150));
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(BorderFactory.createEmptyBorder(15, 12, 12, 12));

        JLabel tLbl = new JLabel("<html><center>" + title + "</center></html>");
        tLbl.setFont(new Font("Segoe UI", Font.BOLD, 15));
        tLbl.setForeground(accent);
        tLbl.setAlignmentX(Component.CENTER_ALIGNMENT);
        card.add(tLbl);
        card.add(Box.createVerticalStrut(8));

        JLabel dLbl = new JLabel("<html><center>" + desc + "</center></html>");
        dLbl.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        dLbl.setForeground(new Color(200, 225, 250));
        dLbl.setAlignmentX(Component.CENTER_ALIGNMENT);
        card.add(dLbl);
        card.add(Box.createVerticalGlue());

        JButton playBtn = new JButton("VÀO CHƠI ▶");
        playBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        playBtn.setForeground(Color.WHITE);
        playBtn.setBackground(accent);
        playBtn.setFocusPainted(false);
        playBtn.setBorderPainted(false);
        playBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        playBtn.setMaximumSize(new Dimension(170, 32));
        playBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        playBtn.addActionListener(e -> action.run());
        card.add(playBtn);

        return card;
    }

    private JButton createSmallIconButton(String text) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btn.setForeground(Color.WHITE);
        btn.setBackground(new Color(15, 45, 85));
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createLineBorder(new Color(0, 210, 255), 1, true));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return btn;
    }

    // --- POPUP DIALOGS ---

    private void showPvEDifficultyDialog() {
        String[] options = {"DỄ (Easy)", "VỪA (Medium)", "KHÓ (Hard)"};
        int choice = JOptionPane.showOptionDialog(this, 
            "CHẾ ĐỘ CHƠI VỚI MÁY (PvE)\n- Nhân vật người chơi và máy sẽ là ngẫu nhiên.\n- Bỏ cơ chế bom.\n- Giai đoạn 1 (" + (int)backend.getConfigs().pvePhase1DurationSec + "s): 3 mạng -> Giai đoạn 2: Tử chiến 1 mạng!\n\nHãy chọn độ khó:", 
            "CHỌN ĐỘ KHÓ PvE", 
            JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[1]);

        if (choice == 0) listener.onStartPvE(AIBot.Difficulty.EASY);
        else if (choice == 1) listener.onStartPvE(AIBot.Difficulty.MEDIUM);
        else if (choice == 2) listener.onStartPvE(AIBot.Difficulty.HARD);
    }

    private void showPvPSelectionDialog() {
        CharacterType[] chars = CharacterType.values();
        String[] names = new String[chars.length];
        for (int i = 0; i < chars.length; i++) {
            names[i] = chars[i].getDisplayName() + (backend.isCharacterUnlocked(chars[i]) ? " (Sẵn sàng)" : " (🔒)");
        }

        JComboBox<String> p1Combo = new JComboBox<>(names);
        JComboBox<String> p2Combo = new JComboBox<>(names);
        p2Combo.setSelectedIndex(1);

        JPanel panel = new JPanel(new GridLayout(2, 2, 10, 10));
        panel.add(new JLabel("Player 1 (Phím W,A,S,D + Q,E,C):"));
        panel.add(p1Combo);
        panel.add(new JLabel("Player 2 (Phím I,J,K,L + O,U,N):"));
        panel.add(p2Combo);

        int result = JOptionPane.showConfirmDialog(this, panel, "CHỌN NHÂN VẬT ĐẤU 2 NGƯỜI (PvP)", 
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            CharacterType p1 = chars[p1Combo.getSelectedIndex()];
            CharacterType p2 = chars[p2Combo.getSelectedIndex()];
            listener.onStartPvP(p1, p2);
        }
    }

    private void showHighScoreModeDialog() {
        checkAndStartMode(() -> {
            String[] options = {(int)backend.getConfigs().gameDurationTimedSec + " GIÂY (Có Thời Gian)", "VÔ TẬN (Không Giới Hạn)"};
            int choice = JOptionPane.showOptionDialog(this, 
                "Chọn thể thức tranh tài điểm cao:", "CHẾ ĐỘ HIGH SCORE", 
                JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);

            if (choice == 0) listener.onStartHighScore(true, selectedCharacter);
            else if (choice == 1) listener.onStartHighScore(false, selectedCharacter);
        });
    }

    private void showLeaderboardDialog() {
        JTabbedPane tabs = new JTabbedPane();

        // TAB 1: KỶ LỤC 3 PHÚT (ONLINE ƯU TIÊN, FALLBACK LOCAL)
        JTextArea taTimed = new JTextArea("Đang tải Bảng Vàng Online (time_limit)...", 14, 42);
        taTimed.setEditable(false);
        taTimed.setFont(new Font("Monospaced", Font.PLAIN, 12));
        tabs.addTab("⏱️ Top 3 Phút", new JScrollPane(taTimed));

        // TAB 2: KỶ LỤC VÔ HẠN (ONLINE ƯU TIÊN, FALLBACK LOCAL)
        JTextArea taEndless = new JTextArea("Đang tải Bảng Vàng Online (endless)...", 14, 42);
        taEndless.setEditable(false);
        taEndless.setFont(new Font("Monospaced", Font.PLAIN, 12));
        tabs.addTab("♾️ Top Vô Hạn", new JScrollPane(taEndless));

        // TAB 3: LỊCH SỬ PVP (ONLINE)
        JTextArea taPvp = new JTextArea("Đang tải lịch sử đấu PvP...", 14, 42);
        taPvp.setEditable(false);
        taPvp.setFont(new Font("Monospaced", Font.PLAIN, 12));
        tabs.addTab("⚔️ Lịch Sử PvP", new JScrollPane(taPvp));

        // TAB 4: FILE DỮ LIỆU CỤC BỘ (OFFLINE)
        StringBuilder sbLocal = new StringBuilder();
        sbLocal.append("=== KỶ LỤC CỤC BỘ: 3 PHÚT (highscores.dat) ===\n");
        for (HighScoreManager.Entry e : highScores.getTimedScores()) {
            sbLocal.append(String.format("• %-15s: %6d điểm | Size %d | %-12s [%s]\n", e.getPlayerName(), e.getScore(), e.getSizeLevel(), e.getCharacterName(), e.getDateStr()));
        }
        sbLocal.append("\n=== KỶ LỤC CỤC BỘ: VÔ HẠN (highscores.dat) ===\n");
        for (HighScoreManager.Entry e : highScores.getUntimedScores()) {
            sbLocal.append(String.format("• %-15s: %6d điểm | Size %d | %-12s [%s]\n", e.getPlayerName(), e.getScore(), e.getSizeLevel(), e.getCharacterName(), e.getDateStr()));
        }
        JTextArea taLocal = new JTextArea(sbLocal.toString(), 14, 42);
        taLocal.setEditable(false);
        taLocal.setFont(new Font("Monospaced", Font.PLAIN, 12));
        tabs.addTab("💾 Local File", new JScrollPane(taLocal));

        // Tải dữ liệu bất đồng bộ
        backend.fetchLeaderboardAsync("time_limit", true, timedEntries -> {
            StringBuilder sb = new StringBuilder();
            if (timedEntries != null && !timedEntries.isEmpty()) {
                sb.append("=== TOP 10 ONLINE: CHẾ ĐỘ 3 PHÚT (SQL SERVER) ===\n\n");
                int rank = 1;
                for (BackendClient.OnlineScoreEntry e : timedEntries) {
                    sb.append(String.format("%2d. %-15s: %6d điểm (%s) [%s]\n", rank++, e.username, e.score, e.characterName, e.playedAt));
                }
            } else {
                sb.append("=== TOP 10: CHẾ ĐỘ 3 PHÚT (OFFLINE FALLBACK) ===\n");
                sb.append("(Server Backend tắt hoặc chưa có bản ghi, hiển thị Local)\n\n");
                int rank = 1;
                for (HighScoreManager.Entry e : highScores.getTimedScores()) {
                    sb.append(String.format("%2d. %-15s: %6d điểm (%s) [%s]\n", rank++, e.getPlayerName(), e.getScore(), e.getCharacterName(), e.getDateStr()));
                }
            }
            taTimed.setText(sb.toString());
        });

        backend.fetchLeaderboardAsync("endless", true, endlessEntries -> {
            StringBuilder sb = new StringBuilder();
            if (endlessEntries != null && !endlessEntries.isEmpty()) {
                sb.append("=== TOP 10 ONLINE: CHẾ ĐỘ VÔ HẠN (SQL SERVER) ===\n\n");
                int rank = 1;
                for (BackendClient.OnlineScoreEntry e : endlessEntries) {
                    sb.append(String.format("%2d. %-15s: %6d điểm (%s) [%s]\n", rank++, e.username, e.score, e.characterName, e.playedAt));
                }
            } else {
                sb.append("=== TOP 10: CHẾ ĐỘ VÔ HẠN (OFFLINE FALLBACK) ===\n");
                sb.append("(Server Backend tắt hoặc chưa có bản ghi, hiển thị Local)\n\n");
                int rank = 1;
                for (HighScoreManager.Entry e : highScores.getUntimedScores()) {
                    sb.append(String.format("%2d. %-15s: %6d điểm (%s) [%s]\n", rank++, e.getPlayerName(), e.getScore(), e.getCharacterName(), e.getDateStr()));
                }
            }
            taEndless.setText(sb.toString());
        });

        backend.fetchPvpHistoryAsync(pvpEntries -> {
            StringBuilder sb = new StringBuilder();
            sb.append("=== LỊCH SỬ THI ĐẤU ĐỐI KHÁNG 2 NGƯỜI (PVP) ===\n\n");
            if (pvpEntries != null && !pvpEntries.isEmpty()) {
                for (BackendClient.PvpMatchEntry m : pvpEntries) {
                    sb.append(String.format("Trận #%d: [%s (%s)] VS [%s (%s)]\n", m.matchId, m.p1Name, m.p1Char, m.p2Name, m.p2Char));
                    sb.append(String.format("   🏆 Thắng: %-12s | ⏱️ %ds | 📅 %s\n\n", m.winnerName, m.durationSec, m.playedAt));
                }
            } else {
                sb.append("Chưa có lịch sử đấu PvP hoặc Server Backend chưa kết nối.\n");
            }
            taPvp.setText(sb.toString());
        });

        JOptionPane.showMessageDialog(this, tabs, "BẢNG VÀNG KỶ LỤC ĐẠI DƯƠNG", JOptionPane.INFORMATION_MESSAGE);
    }

    private void showAccountDialog() {
        JPanel p = new JPanel(new GridLayout(0, 1, 6, 6));
        p.add(new JLabel("Tên Người Chơi Của Bạn (Đăng nhập / Đăng ký tự động):"));
        JTextField nameField = new JTextField(backend.getCurrentUsername(), 15);
        p.add(nameField);

        boolean isOnline = backend.isServerOnline();
        JLabel statusLbl = new JLabel("Trạng thái CSDL: " + (isOnline ? "🟢 ONLINE (Đã kết nối Server 8080)" : "⚪ OFFLINE (Chưa bật Backend Spring Boot)"));
        statusLbl.setForeground(isOnline ? new Color(0, 200, 100) : Color.LIGHT_GRAY);
        p.add(statusLbl);

        if (backend.getCurrentUserId() != null) {
            p.add(new JLabel("Mã Người Chơi Hiện Tại (User ID): #" + backend.getCurrentUserId()));
        }
        p.add(new JLabel("Server Backend: " + backend.getBaseUrl()));

        int res = JOptionPane.showConfirmDialog(this, p, "TÀI KHOẢN & KẾT NỐI CSDL", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (res == JOptionPane.OK_OPTION) {
            String newName = nameField.getText().trim();
            if (!newName.isEmpty()) {
                backend.setCurrentUsername(newName);
                backend.loginOrRegisterAsync(newName, id -> {
                    updateTopBarInfo();
                    updateCharacterDetails(selectedCharacter);
                    JOptionPane.showMessageDialog(this,
                        "Đăng nhập CSDL thành công!\n" +
                        "Mã User ID: #" + id + "\n" +
                        "Tên người chơi: " + newName + "\n" +
                        "Đã đồng bộ kho Thủy Thần đã mở khóa.",
                        "KẾT NỐI THÀNH CÔNG", JOptionPane.INFORMATION_MESSAGE);
                }, err -> {
                    updateTopBarInfo();
                    JOptionPane.showMessageDialog(this,
                        "Đã lưu tên người chơi cục bộ: " + newName + "\n" +
                        "(Server đang offline: " + err + ")",
                        "LƯU THÔNG TIN CỤC BỘ", JOptionPane.WARNING_MESSAGE);
                });
            }
        }
    }

    private void showSettingsDialog() {
        JPanel p = new JPanel(new GridLayout(0, 1, 5, 5));
        SoundManager snd = SoundManager.getInstance();

        JLabel volLbl = new JLabel("Âm Lượng: " + (int)(snd.getVolume() * 100) + "%");
        JSlider volSlider = new JSlider(0, 100, (int)(snd.getVolume() * 100));
        volSlider.addChangeListener(e -> {
            snd.setVolume(volSlider.getValue() / 100f);
            volLbl.setText("Âm Lượng: " + volSlider.getValue() + "%");
        });
        p.add(volLbl);
        p.add(volSlider);

        JCheckBox muteBox = new JCheckBox("Tắt âm thanh (Mute)", snd.isMuted());
        muteBox.addActionListener(e -> snd.setMuted(muteBox.isSelected()));
        p.add(muteBox);

        p.add(new JSeparator());
        p.add(new JLabel("CẤU HÌNH LUẬT CHƠI (TỪ SERVER BACKEND):"));
        BackendClient.GameConfigs cf = backend.getConfigs();
        p.add(new JLabel("• Thời gian Bá Khí: " + cf.hakiBuffDurationSec + "s"));
        p.add(new JLabel("• Mù do mực: " + cf.squidInkBlindSec + "s"));
        p.add(new JLabel("• Ăn cá lên cấp: " + cf.eatCountToLevelUp + " con"));
        p.add(new JLabel("• Thời gian chế độ Timed: " + (int)cf.gameDurationTimedSec + "s"));
        p.add(new JLabel("• Làm chậm do cá độc (giảm 50% tốc): " + cf.poisonFishStunSec + "s"));
        p.add(new JLabel("• Thời gian PvE Giai đoạn 1: " + (int)cf.pvePhase1DurationSec + "s"));

        p.add(new JSeparator());
        p.add(new JLabel("HỆ THỐNG 7 THỦY THẦN:"));
        p.add(new JLabel("• 1. Tôm: Búng lùi dịch chuyển tức thời"));
        p.add(new JLabel("• 2. Cá Kiếm: Lướt thần tốc 3.5s"));
        p.add(new JLabel("• 3. Cua: Kẹp sắt đoạt mạng 2 cá nhỏ"));
        p.add(new JLabel("• 4. Cá Ép: Ký sinh điều khiển cá to 5s"));
        p.add(new JLabel("• 5. Bạch Tuộc: Vùng mực hắc ám bất tử & làm chậm"));
        p.add(new JLabel("• 6. Cá Đuối Điện: Xung điện định kỳ gây choáng"));
        p.add(new JLabel("• 7. Rùa Biển: Mai rùa bất hoại phản đòn"));

        JOptionPane.showMessageDialog(this, p, "CÀI ĐẶT & KỸ NĂNG", JOptionPane.PLAIN_MESSAGE);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        // Vẽ hình nền sảnh chờ lobby_bg.jpg
        BufferedImage bg = assets.getImage("lobby_bg");
        if (bg != null) {
            g.drawImage(bg, 0, 0, getWidth(), getHeight(), null);
        } else {
            g.setColor(new Color(3, 10, 25));
            g.fillRect(0, 0, getWidth(), getHeight());
        }

        // Lớp phủ tối nhẹ tạo độ sâu cho giao diện
        g.setColor(new Color(2, 10, 24, 100));
        g.fillRect(0, 0, getWidth(), getHeight());
    }
}
