-- =====================================================================
-- DỰ ÁN: HUNTER OF THE SEA (THỦY THẦN ĐẠI DƯƠNG)
-- SCRIPT KHỞI TẠO CƠ SỞ DỮ LIỆU VÀ DỮ LIỆU MẪU (SQL SERVER)
-- =====================================================================

IF NOT EXISTS (SELECT name FROM sys.databases WHERE name = N'hunter_of_the_sea')
BEGIN
    CREATE DATABASE hunter_of_the_sea;
END
GO

USE hunter_of_the_sea;
GO

-- =====================================================================
-- 1. BẢNG NGƯỜI CHƠI (USERS)
-- =====================================================================
IF OBJECT_ID(N'dbo.Users', N'U') IS NULL
BEGIN
    CREATE TABLE Users (
        user_id INT IDENTITY(1,1) PRIMARY KEY,
        username NVARCHAR(50) UNIQUE NOT NULL,
        created_at DATETIME DEFAULT GETDATE()
    );
END
GO

-- =====================================================================
-- 2. BẢNG DANH SÁCH NHÂN VẬT THỦY THẦN (CHARACTERS)
-- =====================================================================
IF OBJECT_ID(N'dbo.Characters', N'U') IS NULL
BEGIN
    CREATE TABLE Characters (
        character_id INT IDENTITY(1,1) PRIMARY KEY,
        name NVARCHAR(50) NOT NULL UNIQUE,
        base_size_width INT NOT NULL,      
        base_size_height INT NOT NULL,     
        base_speed DECIMAL(4, 2) NOT NULL, 
        skill_name NVARCHAR(100) NOT NULL, 
        skill_description NVARCHAR(MAX),   
        mana_cost INT DEFAULT 5,           
        created_at DATETIME DEFAULT GETDATE()
    );
END
GO

-- =====================================================================
-- 3. BẢNG KHO NHÂN VẬT ĐÃ MỞ KHÓA (USER_CHARACTERS)
-- =====================================================================
IF OBJECT_ID(N'dbo.User_Characters', N'U') IS NULL
BEGIN
    CREATE TABLE User_Characters (
        user_id INT NOT NULL,
        character_id INT NOT NULL,
        unlocked_at DATETIME DEFAULT GETDATE(),
        PRIMARY KEY (user_id, character_id),
        FOREIGN KEY (user_id) REFERENCES Users(user_id) ON DELETE CASCADE,
        FOREIGN KEY (character_id) REFERENCES Characters(character_id) ON DELETE CASCADE
    );
END
GO

-- =====================================================================
-- 4. BẢNG KỶ LỤC & BẢNG XẾP HẠNG (LEADERBOARDS)
-- =====================================================================
IF OBJECT_ID(N'dbo.Leaderboards', N'U') IS NULL
BEGIN
    CREATE TABLE Leaderboards (
        record_id INT IDENTITY(1,1) PRIMARY KEY,
        user_id INT NOT NULL,
        character_id INT NOT NULL,
        score INT NOT NULL, 
        game_mode VARCHAR(50) NOT NULL, -- 'time_limit' (3 phút) hoặc 'endless' (Vô tận)
        played_at DATETIME DEFAULT GETDATE(),
        
        FOREIGN KEY (user_id) REFERENCES Users(user_id) ON DELETE CASCADE,
        FOREIGN KEY (character_id) REFERENCES Characters(character_id) ON DELETE CASCADE
    );
END
GO

-- =====================================================================
-- 5. BẢNG LỊCH SỬ ĐẤU ĐỐI KHÁNG 2 NGƯỜI (PVP_MATCH_HISTORY)
-- =====================================================================
IF OBJECT_ID(N'dbo.Pvp_Match_History', N'U') IS NULL
BEGIN
    CREATE TABLE Pvp_Match_History (
        match_id INT IDENTITY(1,1) PRIMARY KEY,
        player1_id INT NOT NULL,
        player1_character_id INT NOT NULL,
        player2_id INT NOT NULL,
        player2_character_id INT NOT NULL,
        winner_id INT NOT NULL,
        match_duration_seconds INT NOT NULL,
        played_at DATETIME DEFAULT GETDATE(),
        
        FOREIGN KEY (player1_id) REFERENCES Users(user_id),
        FOREIGN KEY (player2_id) REFERENCES Users(user_id),
        FOREIGN KEY (winner_id) REFERENCES Users(user_id),
        FOREIGN KEY (player1_character_id) REFERENCES Characters(character_id),
        FOREIGN KEY (player2_character_id) REFERENCES Characters(character_id)
    );
END
GO

-- =====================================================================
-- 6. BẢNG CẤU HÌNH THÔNG SỐ GAME (GAMECONFIGS)
-- =====================================================================
IF OBJECT_ID(N'dbo.GameConfigs', N'U') IS NULL
BEGIN
    CREATE TABLE GameConfigs (
        config_key VARCHAR(50) PRIMARY KEY,
        config_value NVARCHAR(255) NOT NULL,
        description NVARCHAR(MAX),
        updated_at DATETIME DEFAULT GETDATE()
    );
END
GO

-- =====================================================================
-- NẠP DỮ LIỆU BAN ĐẦU (SEED DATA)
-- =====================================================================

-- 1. Nạp danh sách 7 Thủy Thần chuẩn xác theo gameplay
IF NOT EXISTS (SELECT 1 FROM Characters WHERE name = N'Tôm')
    INSERT INTO Characters (name, base_size_width, base_size_height, base_speed, skill_name, skill_description, mana_cost)
    VALUES (N'Tôm', 2, 2, 4.50, N'Búng Lùi Dịch Chuyển', N'Dịch chuyển tức thời tới vùng an toàn: xa cá lớn và gần bầy cá nhỏ.', 5);

IF NOT EXISTS (SELECT 1 FROM Characters WHERE name = N'Cá Kiếm')
    INSERT INTO Characters (name, base_size_width, base_size_height, base_speed, skill_name, skill_description, mana_cost)
    VALUES (N'Cá Kiếm', 3, 2, 4.80, N'Lướt Thần Tốc', N'Tăng mạnh tốc độ bơi gấp 2.2 lần trong 3.5 giây để săn mồi hoặc tẩu thoát.', 5);

IF NOT EXISTS (SELECT 1 FROM Characters WHERE name = N'Cua')
    INSERT INTO Characters (name, base_size_width, base_size_height, base_speed, skill_name, skill_description, mana_cost)
    VALUES (N'Cua', 3, 3, 3.80, N'Kẹp Sắt Đoạt Mạng', N'Tung đôi càng kẹp bắt và nuốt trọn 2 con cá nhỏ gần nhất trong phạm vi.', 5);

IF NOT EXISTS (SELECT 1 FROM Characters WHERE name = N'Cá Ép')
    INSERT INTO Characters (name, base_size_width, base_size_height, base_speed, skill_name, skill_description, mana_cost)
    VALUES (N'Cá Ép', 2, 2, 4.20, N'Ký Sinh Bám Dính', N'Tạm bám vào cá lớn, điều khiển cá chủ và cộng dồn kích thước trong 5s.', 5);

IF NOT EXISTS (SELECT 1 FROM Characters WHERE name = N'Bạch Tuộc')
    INSERT INTO Characters (name, base_size_width, base_size_height, base_speed, skill_name, skill_description, mana_cost)
    VALUES (N'Bạch Tuộc', 3, 3, 3.90, N'Vùng Mực Hắc Ám', N'Phun vùng mây mực: Bản thân bất tử và tăng tốc, cá khác bị làm chậm.', 5);

IF NOT EXISTS (SELECT 1 FROM Characters WHERE name = N'Cá Đuối Điện')
    INSERT INTO Characters (name, base_size_width, base_size_height, base_speed, skill_name, skill_description, mana_cost)
    VALUES (N'Cá Đuối Điện', 3, 3, 4.00, N'Xung Điện Định Kỳ', N'Phóng xung điện trong 3s, làm choáng 0.5s toàn bộ cá ở gần sau mỗi 1s.', 5);

IF NOT EXISTS (SELECT 1 FROM Characters WHERE name = N'Rùa Biển')
    INSERT INTO Characters (name, base_size_width, base_size_height, base_speed, skill_name, skill_description, mana_cost)
    VALUES (N'Rùa Biển', 3, 3, 3.50, N'Mai Rùa Bất Hoại', N'Bất tử trong 4s; cá lớn tấn công sẽ bị đẩy lùi mạnh và làm choáng 1s.', 5);

-- 2. Nạp cấu hình thông số luật chơi
IF NOT EXISTS (SELECT 1 FROM GameConfigs WHERE config_key = 'haki_buff_duration_sec')
    INSERT INTO GameConfigs (config_key, config_value, description)
    VALUES ('haki_buff_duration_sec', '5', N'Thời gian hiệu lực tăng 1 cấp của viên bá khí (5 giây)');

IF NOT EXISTS (SELECT 1 FROM GameConfigs WHERE config_key = 'squid_ink_blind_sec')
    INSERT INTO GameConfigs (config_key, config_value, description)
    VALUES ('squid_ink_blind_sec', '2', N'Thời gian đen màn hình khi ăn phải mực (2 giây)');

IF NOT EXISTS (SELECT 1 FROM GameConfigs WHERE config_key = 'eat_count_to_level_up')
    INSERT INTO GameConfigs (config_key, config_value, description)
    VALUES ('eat_count_to_level_up', '10', N'Số lượng cá nhỏ cần ăn để tăng 1 cấp kích thước');

IF NOT EXISTS (SELECT 1 FROM GameConfigs WHERE config_key = 'game_duration_timed_sec')
    INSERT INTO GameConfigs (config_key, config_value, description)
    VALUES ('game_duration_timed_sec', '180', N'Thời gian thi đấu chế độ 3 phút tính bằng giây');

IF NOT EXISTS (SELECT 1 FROM GameConfigs WHERE config_key = 'poison_fish_stun_sec')
    INSERT INTO GameConfigs (config_key, config_value, description)
    VALUES ('poison_fish_stun_sec', '2', N'Thời gian làm chậm tốc độ di chuyển khi ăn phải cá độc (giây)');

IF NOT EXISTS (SELECT 1 FROM GameConfigs WHERE config_key = 'pve_phase1_duration_sec')
    INSERT INTO GameConfigs (config_key, config_value, description)
    VALUES ('pve_phase1_duration_sec', '90', N'Thời gian giai đoạn 1 của chế độ chơi với máy PvE');

-- 3. Nạp tài khoản người chơi mẫu
IF NOT EXISTS (SELECT 1 FROM Users WHERE username = N'ThuyThuSanMoi')
    INSERT INTO Users (username) VALUES (N'ThuyThuSanMoi');

IF NOT EXISTS (SELECT 1 FROM Users WHERE username = N'HaiTacBien')
    INSERT INTO Users (username) VALUES (N'HaiTacBien');

IF NOT EXISTS (SELECT 1 FROM Users WHERE username = N'VuaDaiDuong')
    INSERT INTO Users (username) VALUES (N'VuaDaiDuong');

-- 4. Nạp quyền mở khóa nhân vật cho người chơi mẫu
DECLARE @u1 INT = (SELECT user_id FROM Users WHERE username = N'ThuyThuSanMoi');
DECLARE @u2 INT = (SELECT user_id FROM Users WHERE username = N'HaiTacBien');
DECLARE @u3 INT = (SELECT user_id FROM Users WHERE username = N'VuaDaiDuong');

DECLARE @c_shrimp INT = (SELECT character_id FROM Characters WHERE name = N'Tôm');
DECLARE @c_sword  INT = (SELECT character_id FROM Characters WHERE name = N'Cá Kiếm');
DECLARE @c_crab   INT = (SELECT character_id FROM Characters WHERE name = N'Cua');
DECLARE @c_turtle INT = (SELECT character_id FROM Characters WHERE name = N'Rùa Biển');

IF @u1 IS NOT NULL AND @c_sword IS NOT NULL AND NOT EXISTS (SELECT 1 FROM User_Characters WHERE user_id = @u1 AND character_id = @c_sword)
    INSERT INTO User_Characters (user_id, character_id) VALUES (@u1, @c_sword);

IF @u1 IS NOT NULL AND @c_shrimp IS NOT NULL AND NOT EXISTS (SELECT 1 FROM User_Characters WHERE user_id = @u1 AND character_id = @c_shrimp)
    INSERT INTO User_Characters (user_id, character_id) VALUES (@u1, @c_shrimp);

IF @u2 IS NOT NULL AND @c_crab IS NOT NULL AND NOT EXISTS (SELECT 1 FROM User_Characters WHERE user_id = @u2 AND character_id = @c_crab)
    INSERT INTO User_Characters (user_id, character_id) VALUES (@u2, @c_crab);

IF @u3 IS NOT NULL AND @c_turtle IS NOT NULL AND NOT EXISTS (SELECT 1 FROM User_Characters WHERE user_id = @u3 AND character_id = @c_turtle)
    INSERT INTO User_Characters (user_id, character_id) VALUES (@u3, @c_turtle);

-- 5. Nạp kỷ lục bảng xếp hạng mẫu
IF @u1 IS NOT NULL AND @c_sword IS NOT NULL AND NOT EXISTS (SELECT 1 FROM Leaderboards WHERE user_id = @u1 AND score = 5600)
    INSERT INTO Leaderboards (user_id, character_id, score, game_mode) VALUES (@u1, @c_sword, 5600, 'time_limit');

IF @u2 IS NOT NULL AND @c_crab IS NOT NULL AND NOT EXISTS (SELECT 1 FROM Leaderboards WHERE user_id = @u2 AND score = 4200)
    INSERT INTO Leaderboards (user_id, character_id, score, game_mode) VALUES (@u2, @c_crab, 4200, 'time_limit');

IF @u3 IS NOT NULL AND @c_turtle IS NOT NULL AND NOT EXISTS (SELECT 1 FROM Leaderboards WHERE user_id = @u3 AND score = 8900)
    INSERT INTO Leaderboards (user_id, character_id, score, game_mode) VALUES (@u3, @c_turtle, 8900, 'endless');

-- 6. Nạp lịch sử trận đối kháng PvP mẫu
IF @u1 IS NOT NULL AND @u2 IS NOT NULL AND @c_sword IS NOT NULL AND @c_crab IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM Pvp_Match_History WHERE player1_id = @u1 AND player2_id = @u2)
    INSERT INTO Pvp_Match_History (player1_id, player1_character_id, player2_id, player2_character_id, winner_id, match_duration_seconds)
    VALUES (@u1, @c_sword, @u2, @c_crab, @u1, 145);
GO

-- =====================================================================
-- VIEW TIỆN ÍCH CHO DEV: XEM BẢNG VÀNG TOP ĐẦY ĐỦ THÔNG TIN
-- =====================================================================
IF OBJECT_ID(N'dbo.vw_TopLeaderboard', N'V') IS NOT NULL
    DROP VIEW dbo.vw_TopLeaderboard;
GO

CREATE VIEW dbo.vw_TopLeaderboard AS
SELECT 
    l.record_id,
    u.username,
    c.name AS character_name,
    c.skill_name,
    l.score,
    l.game_mode,
    l.played_at
FROM Leaderboards l
JOIN Users u ON l.user_id = u.user_id
JOIN Characters c ON l.character_id = c.character_id;
GO

PRINT N'>>> Đã khởi tạo hoàn tất CSDL hunter_of_the_sea và nạp dữ liệu mẫu thành công!';
