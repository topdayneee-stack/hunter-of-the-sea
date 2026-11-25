package huntersea;

import huntersea.model.AIBot;
import huntersea.model.CharacterType;
import huntersea.model.Player;
import huntersea.ui.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import javax.swing.*;

/**
 * Điểm khởi chạy chính của trò chơi "Hunter of the Sea".
 * Thiết lập cửa sổ JFrame 16:9, điều phối màn hình Lobby / Game,
 * và điều khiển Game Loop 60 FPS.
 */
public class Main extends JFrame implements LobbyPanel.LobbyActionListener {
    private final GameEngine engine;
    private final CardLayout cardLayout;
    private final JPanel mainContainer;

    private LobbyPanel lobbyPanel;
    private GamePanel gamePanel;
    private Timer gameLoopTimer;

    private boolean gameOverDialogShown = false;

    public Main() {
        super("Hunter of the Sea - Thủy Thần Đại Dương");

        engine = new GameEngine();

        // Cài đặt icon cửa sổ
        BufferedImage icon = AssetManager.getInstance().getImage("swordfish");
        if (icon != null) {
            setIconImage(icon);
        }

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1280, 720); // Tỷ lệ 16:9 chuẩn
        setMinimumSize(new Dimension(1024, 576));
        setLocationRelativeTo(null);

        cardLayout = new CardLayout();
        mainContainer = new JPanel(cardLayout);

        lobbyPanel = new LobbyPanel(this);
        gamePanel = new GamePanel(engine, this);

        mainContainer.add(lobbyPanel, "LOBBY");
        mainContainer.add(gamePanel, "GAME");

        setContentPane(mainContainer);

        // Khởi động Game Loop 60 FPS (16ms mỗi tick)
        initGameLoop();
    }

    private void initGameLoop() {
        gameLoopTimer = new Timer(16, e -> {
            if (engine.getState() == GameEngine.State.PLAYING) {
                gamePanel.processPlayerInput(); // Cập nhật hướng di chuyển mỗi frame
                engine.update(0.016);
                gamePanel.repaint();
                gameOverDialogShown = false;
            } else if (engine.getState() == GameEngine.State.GAME_OVER || engine.getState() == GameEngine.State.VICTORY) {
                gamePanel.repaint();
                if (!gameOverDialogShown) {
                    gameOverDialogShown = true;
                    showGameOverModal();
                }
            }
        });
        gameLoopTimer.start();
    }

    private void showGameOverModal() {
        Player p = engine.getPlayer1();

        GameOverDialog dlg = new GameOverDialog(this, engine, new GameOverDialog.GameOverListener() {
            @Override
            public void onRestart() {
                gamePanel.resetInputStates();
                // Chơi lại chế độ hiện tại
                if (engine.getCurrentMode() == GameEngine.GameMode.BASIC) {
                    onStartBasic(p.getCharacterType());
                } else if (engine.getCurrentMode() == GameEngine.GameMode.HIGH_SCORE_TIMED) {
                    onStartHighScore(true, p.getCharacterType());
                } else if (engine.getCurrentMode() == GameEngine.GameMode.HIGH_SCORE_UNTIMED) {
                    onStartHighScore(false, p.getCharacterType());
                } else if (engine.getCurrentMode() == GameEngine.GameMode.PVE) {
                    onStartPvE(AIBot.Difficulty.MEDIUM);
                } else if (engine.getCurrentMode() == GameEngine.GameMode.PVP) {
                    onStartPvP(engine.getPlayer1().getCharacterType(), 
                        engine.getPlayer2() != null ? engine.getPlayer2().getCharacterType() : CharacterType.SHRIMP);
                }
            }

            @Override
            public void onQuitToLobby() {
                gamePanel.resetInputStates();
                showLobby();
            }
        });
        dlg.setVisible(true);
        if (engine.getState() == GameEngine.State.PLAYING) {
            SwingUtilities.invokeLater(() -> {
                gamePanel.resetInputStates();
                gamePanel.requestFocusInWindow();
            });
        }
    }

    public void showLobby() {
        gamePanel.resetInputStates();
        engine.setState(GameEngine.State.LOBBY);
        cardLayout.show(mainContainer, "LOBBY");
        lobbyPanel.repaint();
    }

    private void switchToGame() {
        gamePanel.resetCamera(); // Snap camera ngay đến vị trí player và resetInputStates
        cardLayout.show(mainContainer, "GAME");
        SwingUtilities.invokeLater(() -> {
            gamePanel.resetInputStates();
            gamePanel.requestFocusInWindow();
        });
    }

    // --- CÁC SỰ KIỆN TỪ LOBBY ---

    @Override
    public void onStartBasic(CharacterType character) {
        engine.startBasicMode(character);
        switchToGame();
    }

    @Override
    public void onStartPvE(AIBot.Difficulty diff) {
        engine.startPvEMode(diff);
        switchToGame();
    }

    @Override
    public void onStartPvP(CharacterType p1, CharacterType p2) {
        engine.startPvPMode(p1, p2);
        switchToGame();
    }

    @Override
    public void onStartHighScore(boolean timed, CharacterType character) {
        if (timed) engine.startHighScoreTimed(character);
        else engine.startHighScoreUntimed(character);
        switchToGame();
    }

    public static void main(String[] args) {
        // Tối ưu hóa render Java 2D trên Windows
        System.setProperty("sun.java2d.opengl", "true");
        System.setProperty("sun.java2d.d3d", "true");
        System.setProperty("file.encoding", "UTF-8");

        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}

            Main app = new Main();
            app.setVisible(true);
        });
    }
}
