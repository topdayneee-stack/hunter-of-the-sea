package huntersea.ui;

import huntersea.SoundManager;
import java.awt.*;
import javax.swing.*;

/**
 * Hộp thoại Tạm dừng (Pause Dialog) với giao diện kính mờ hiện đại:
 * - Tăng / giảm âm lượng & Bật tắt tiếng
 * - Tiếp tục (Resume)
 * - Chơi lại (Restart)
 * - Thoát ra màn hình chính (Back to Lobby)
 */
public class PauseDialog extends JDialog {
    public interface PauseListener {
        void onResume();
        void onQuitToLobby();
    }

    public PauseDialog(JFrame parent, PauseListener listener) {
        super(parent, "TẠM DỪNG", true);
        setUndecorated(true);
        setSize(400, 360);
        setLocationRelativeTo(parent);

        JPanel content = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                // Nền gradient xanh biển sâu & viền phát sáng
                GradientPaint gp = new GradientPaint(0, 0, new Color(10, 30, 60, 245), 
                                                    0, getHeight(), new Color(5, 15, 35, 250));
                g2.setPaint(gp);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 30, 30);

                g2.setColor(new Color(0, 210, 255));
                g2.setStroke(new BasicStroke(3));
                g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 30, 30);
            }
        };
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(25, 30, 25, 30));

        // Tiêu đề
        JLabel titleLabel = new JLabel("TẠM DỪNG");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 22));
        titleLabel.setForeground(new Color(255, 215, 0));
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        content.add(titleLabel);
        content.add(Box.createVerticalStrut(20));

        // Phần chỉnh âm lượng (Volume Control)
        SoundManager sound = SoundManager.getInstance();
        JLabel volLabel = new JLabel("ÂM LƯỢNG: " + (int)(sound.getVolume() * 100) + "%");
        volLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        volLabel.setForeground(new Color(160, 220, 255));
        volLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        content.add(volLabel);
        content.add(Box.createVerticalStrut(10));

        // Hàng điều khiển âm lượng: Nút [-], JSlider (0-100%), Nút [+]
        JPanel volPanel = new JPanel();
        volPanel.setOpaque(false);
        volPanel.setLayout(new BoxLayout(volPanel, BoxLayout.X_AXIS));
        volPanel.setMaximumSize(new Dimension(340, 36));

        JSlider volSlider = new JSlider(0, 100, (int)(sound.getVolume() * 100));
        volSlider.setOpaque(false);
        volSlider.setForeground(new Color(0, 210, 255));

        JButton btnVolDown = createSmallButton("-");
        btnVolDown.addActionListener(e -> {
            sound.volumeDown();
            int val = (int)(sound.getVolume() * 100);
            volSlider.setValue(val);
            volLabel.setText("ÂM LƯỢNG: " + val + "%");
        });

        JButton btnVolUp = createSmallButton("+");
        btnVolUp.addActionListener(e -> {
            sound.volumeUp();
            int val = (int)(sound.getVolume() * 100);
            volSlider.setValue(val);
            volLabel.setText("ÂM LƯỢNG: " + val + "%");
        });

        volSlider.addChangeListener(e -> {
            sound.setVolume(volSlider.getValue() / 100f);
            volLabel.setText("ÂM LƯỢNG: " + volSlider.getValue() + "%");
        });

        volPanel.add(btnVolDown);
        volPanel.add(Box.createHorizontalStrut(8));
        volPanel.add(volSlider);
        volPanel.add(Box.createHorizontalStrut(8));
        volPanel.add(btnVolUp);
        content.add(volPanel);
        content.add(Box.createVerticalStrut(25));

        // Nút Tiếp tục (Resume)
        JButton resumeBtn = createCustomButton("Tiếp tục", new Color(0, 180, 240));
        resumeBtn.addActionListener(e -> {
            dispose();
            listener.onResume();
        });
        content.add(resumeBtn);
        content.add(Box.createVerticalStrut(12));

        // Nút Ra màn hình chính (Main Menu)
        JButton quitBtn = createCustomButton("Ra màn hình chính", new Color(200, 50, 60));
        quitBtn.addActionListener(e -> {
            dispose();
            listener.onQuitToLobby();
        });
        content.add(quitBtn);

        // Phím ESC hoặc P trong PauseDialog -> Tiếp tục (Resume)
        getRootPane().registerKeyboardAction(e -> {
            dispose();
            listener.onResume();
        }, KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        getRootPane().registerKeyboardAction(e -> {
            dispose();
            listener.onResume();
        }, KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_P, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);

        setContentPane(content);
    }

    private JButton createSmallButton(String text) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 16));
        btn.setForeground(Color.WHITE);
        btn.setBackground(new Color(40, 80, 140));
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setPreferredSize(new Dimension(36, 32));
        btn.setMaximumSize(new Dimension(36, 32));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private JButton createCustomButton(String text, Color bg) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btn.setForeground(Color.WHITE);
        btn.setBackground(bg);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setAlignmentX(Component.CENTER_ALIGNMENT);
        btn.setMaximumSize(new Dimension(320, 42));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return btn;
    }
}
