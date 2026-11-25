package huntersea.ui;

import huntersea.AssetManager;
import huntersea.BackendClient;
import huntersea.GameEngine;
import huntersea.HighScoreManager;
import huntersea.model.Player;
import java.awt.*;
import java.awt.image.BufferedImage;
import javax.swing.*;

/**
 * Hộp thoại Kết Thúc Trận Đấu (GameOver / Victory / PvP Result).
 * - Chế độ Đơn: Hiển thị Thắng/Thua, thống kê điểm số và cho phép lưu vào Bảng Vàng.
 * - Chế độ PvP: Hiển thị bảng đối chiếu rõ ràng Ai Thắng - Ai Thua hoặc Hòa giữa 2 người chơi.
 */
public class GameOverDialog extends JDialog {
    public interface GameOverListener {
        void onRestart();
        void onQuitToLobby();
    }

    private final GameOverListener listener;

    public GameOverDialog(JFrame parent, GameEngine engine, GameOverListener listener) {
        this(parent,
             engine.getCurrentMode() == GameEngine.GameMode.PVP,
             engine.getPvpWinner(),
             engine.getState() == GameEngine.State.VICTORY,
             engine.getEndMessage(),
             engine.getPlayer1(),
             engine.getPlayer2(),
             listener);
    }

    // Constructor tương thích ngược cho chế độ đơn
    public GameOverDialog(JFrame parent, boolean isVictory, String titleMsg, String reasonMsg, 
                          Player player, GameOverListener listener) {
        this(parent, false, 0, isVictory, reasonMsg, player, null, listener);
    }

    public GameOverDialog(JFrame parent, boolean isPvP, int pvpWinner, boolean isVictory, 
                          String reasonMsg, Player player1, Player player2, GameOverListener listener) {
        super(parent, "KẾT QUẢ TRẬN ĐẤU", true);
        this.listener = listener;
        setUndecorated(true);

        if (isPvP) {
            setSize(590, 520);
        } else {
            setSize(460, 520);
        }
        setLocationRelativeTo(parent);

        JPanel content = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                Color topCol;
                if (isPvP) {
                    if (pvpWinner == 1) topCol = new Color(10, 45, 65, 250);
                    else if (pvpWinner == 2) topCol = new Color(55, 15, 60, 250);
                    else topCol = new Color(45, 40, 15, 250);
                } else {
                    topCol = isVictory ? new Color(10, 50, 40, 245) : new Color(50, 15, 25, 245);
                }
                Color botCol = new Color(5, 10, 25, 250);
                GradientPaint gp = new GradientPaint(0, 0, topCol, 0, getHeight(), botCol);
                g2.setPaint(gp);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 30, 30);

                Color borderColor;
                if (isPvP) {
                    if (pvpWinner == 1) borderColor = new Color(0, 240, 255);
                    else if (pvpWinner == 2) borderColor = new Color(255, 80, 200);
                    else borderColor = new Color(255, 215, 0);
                } else {
                    borderColor = isVictory ? new Color(0, 255, 135) : new Color(255, 51, 102);
                }

                g2.setColor(borderColor);
                g2.setStroke(new BasicStroke(3));
                g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 30, 30);
            }
        };
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(22, 28, 22, 28));

        // 1. Tiêu đề chính nổi bật
        JLabel titleLabel;
        if (isPvP) {
            if (pvpWinner == 1) {
                titleLabel = new JLabel("🎉 NGƯỜI CHƠI 1 CHIẾN THẮNG! 🎉");
                titleLabel.setForeground(new Color(0, 240, 255));
            } else if (pvpWinner == 2) {
                titleLabel = new JLabel("🎉 NGƯỜI CHƠI 2 CHIẾN THẮNG! 🎉");
                titleLabel.setForeground(new Color(255, 100, 220));
            } else {
                titleLabel = new JLabel("🤝 TRẬN ĐẤU HÒA! 🤝");
                titleLabel.setForeground(new Color(255, 215, 0));
            }
        } else {
            titleLabel = new JLabel(isVictory ? "🎉 CHIẾN THẮNG! 🎉" : "💀 BẠN ĐÃ THUA! 💀");
            titleLabel.setForeground(isVictory ? new Color(0, 255, 135) : new Color(255, 51, 102));
        }
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 22));
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        content.add(titleLabel);
        content.add(Box.createVerticalStrut(8));

        // 2. Lý do / Thông điệp kết thúc
        String safeReason = (reasonMsg != null && !reasonMsg.isEmpty()) ? reasonMsg : "Trận đấu kết thúc!";
        JLabel reasonLabel = new JLabel("<html><center>" + safeReason + "</center></html>");
        reasonLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        reasonLabel.setForeground(new Color(200, 220, 240));
        reasonLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        content.add(reasonLabel);
        content.add(Box.createVerticalStrut(16));

        // 3. Nội dung thống kê kết quả
        if (isPvP && player1 != null && player2 != null) {
            // Chế độ PvP: Bảng đối chiếu 2 người chơi
            JPanel pvpPanel = new JPanel(new GridLayout(1, 2, 16, 0));
            pvpPanel.setOpaque(false);
            pvpPanel.setMaximumSize(new Dimension(530, 220));

            boolean p1Win = (pvpWinner == 1);
            boolean p2Win = (pvpWinner == 2);
            boolean draw = (pvpWinner == 0);

            pvpPanel.add(createPvPPlayerCard("PLAYER 1 (WASD)", player1, p1Win, draw, new Color(0, 200, 255)));
            pvpPanel.add(createPvPPlayerCard("PLAYER 2 (IJKL)", player2, p2Win, draw, new Color(255, 80, 200)));

            content.add(pvpPanel);
            content.add(Box.createVerticalStrut(20));

        } else if (player1 != null) {
            // Chế độ Đơn: Thống kê 1 người chơi
            JPanel statsPanel = new JPanel(new GridLayout(3, 2, 10, 8));
            statsPanel.setOpaque(false);
            statsPanel.setMaximumSize(new Dimension(380, 100));

            addStatRow(statsPanel, "Điểm Số:", String.valueOf(player1.getScore()));
            addStatRow(statsPanel, "Kích Thước Đạt Được:", "Size " + player1.getSizeLevel());
            addStatRow(statsPanel, "Số Mồi Đã Ăn:", String.valueOf(player1.getFishEatenCount()));
            content.add(statsPanel);
            content.add(Box.createVerticalStrut(15));

            // Nhập tên lưu kỷ lục
            JPanel namePanel = new JPanel();
            namePanel.setOpaque(false);
            namePanel.setMaximumSize(new Dimension(380, 40));
            JLabel nameLbl = new JLabel("Tên lưu kỷ lục: ");
            nameLbl.setForeground(Color.WHITE);
            String initialName = BackendClient.getInstance().getCurrentUsername();
            if (initialName == null || initialName.isEmpty()) initialName = player1.getName();
            JTextField nameField = new JTextField(initialName, 12);
            namePanel.add(nameLbl);
            namePanel.add(nameField);
            content.add(namePanel);
            content.add(Box.createVerticalStrut(15));

            // Nút Lưu Điểm
            JButton saveBtn = createCustomButton("LƯU VÀO BẢNG VÀNG 🏆", new Color(220, 160, 20));
            saveBtn.addActionListener(e -> {
                String pName = nameField.getText().trim();
                if (!pName.isEmpty()) {
                    BackendClient.getInstance().setCurrentUsername(pName);
                    HighScoreManager.getInstance().addUntimedScore(
                        pName, player1.getScore(), player1.getSizeLevel(), player1.getCharacterType().getDisplayName());
                    JOptionPane.showMessageDialog(this, 
                        "Đã lưu kỷ lục thành công vào máy và đồng bộ lên CSDL Backend!", 
                        "BẢNG VÀNG KỶ LỤC", JOptionPane.INFORMATION_MESSAGE);
                    saveBtn.setEnabled(false);
                }
            });
            content.add(saveBtn);
            content.add(Box.createVerticalStrut(10));
        }

        // 4. Hàng nút chức năng (Chơi lại & Về Sảnh)
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 14, 0));
        btnRow.setOpaque(false);

        JButton restartBtn = createCustomButton("CHƠI LẠI TRẬN MỚI 🔄", new Color(0, 180, 240));
        restartBtn.addActionListener(e -> {
            dispose();
            listener.onRestart();
        });
        btnRow.add(restartBtn);

        JButton lobbyBtn = createCustomButton("VỀ SẢNH CHÍNH 🏠", new Color(70, 80, 100));
        lobbyBtn.addActionListener(e -> {
            dispose();
            listener.onQuitToLobby();
        });
        btnRow.add(lobbyBtn);

        content.add(btnRow);
        setContentPane(content);
    }

    private JPanel createPvPPlayerCard(String slotName, Player p, boolean isWinner, boolean isDraw, Color accent) {
        JPanel card = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(8, 22, 48, 220));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
                g2.setColor(isWinner ? new Color(0, 255, 128) : (isDraw ? new Color(255, 215, 0) : new Color(255, 60, 60, 140)));
                g2.setStroke(new BasicStroke(isWinner ? 2.5f : 1.5f));
                g2.drawRoundRect(1, 1, getWidth() - 2, getHeight() - 2, 20, 20);
            }
        };
        card.setOpaque(false);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));

        // Nhãn Slot
        JLabel slotLbl = new JLabel(slotName);
        slotLbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
        slotLbl.setForeground(accent);
        slotLbl.setAlignmentX(Component.CENTER_ALIGNMENT);
        card.add(slotLbl);
        card.add(Box.createVerticalStrut(6));

        // Huy hiệu Thắng/Thua/Hòa
        JLabel badgeLbl = new JLabel();
        badgeLbl.setFont(new Font("Segoe UI", Font.BOLD, 14));
        badgeLbl.setAlignmentX(Component.CENTER_ALIGNMENT);
        if (isWinner) {
            badgeLbl.setText("🏆 CHIẾN THẮNG");
            badgeLbl.setForeground(new Color(0, 255, 135));
        } else if (isDraw) {
            badgeLbl.setText("🤝 HÒA");
            badgeLbl.setForeground(new Color(255, 215, 0));
        } else {
            badgeLbl.setText("💀 THẤT BẠI");
            badgeLbl.setForeground(new Color(255, 70, 70));
        }
        card.add(badgeLbl);
        card.add(Box.createVerticalStrut(10));

        // Avatar Tướng
        BufferedImage img = AssetManager.getInstance().getImage(p.getCharacterType().getSpriteKey());
        if (img != null) {
            Image scaled = img.getScaledInstance(55, 55, Image.SCALE_SMOOTH);
            JLabel imgLbl = new JLabel(new ImageIcon(scaled));
            imgLbl.setAlignmentX(Component.CENTER_ALIGNMENT);
            card.add(imgLbl);
            card.add(Box.createVerticalStrut(8));
        }

        // Tên tướng & Chỉ số
        JLabel nameLbl = new JLabel(p.getCharacterType().getDisplayName());
        nameLbl.setFont(new Font("Segoe UI", Font.BOLD, 13));
        nameLbl.setForeground(Color.WHITE);
        nameLbl.setAlignmentX(Component.CENTER_ALIGNMENT);
        card.add(nameLbl);
        card.add(Box.createVerticalStrut(4));

        JLabel sizeLbl = new JLabel("Kích cỡ: Size " + p.getSizeLevel());
        sizeLbl.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        sizeLbl.setForeground(new Color(255, 215, 0));
        sizeLbl.setAlignmentX(Component.CENTER_ALIGNMENT);
        card.add(sizeLbl);

        JLabel scoreLbl = new JLabel("Điểm số: " + p.getScore() + " (" + p.getFishEatenCount() + " cá)");
        scoreLbl.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        scoreLbl.setForeground(new Color(190, 220, 250));
        scoreLbl.setAlignmentX(Component.CENTER_ALIGNMENT);
        card.add(scoreLbl);

        return card;
    }

    private void addStatRow(JPanel panel, String label, String value) {
        JLabel l = new JLabel(label);
        l.setForeground(new Color(170, 200, 230));
        l.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        JLabel v = new JLabel(value);
        v.setForeground(new Color(255, 215, 0));
        v.setFont(new Font("Segoe UI", Font.BOLD, 15));
        panel.add(l);
        panel.add(v);
    }

    private JButton createCustomButton(String text, Color bg) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btn.setForeground(Color.WHITE);
        btn.setBackground(bg);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setAlignmentX(Component.CENTER_ALIGNMENT);
        btn.setMaximumSize(new Dimension(210, 36));
        btn.setPreferredSize(new Dimension(210, 36));
        return btn;
    }
}
