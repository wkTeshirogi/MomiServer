-- ===================================================
-- 開発・テスト用 初期データ
-- Flyway管理外のローカル専用初期化スクリプト
-- ===================================================

-- 開発用テストユーザー（パスワード: password123）
-- BCryptハッシュ: $2a$10$...（Spring Security の BCryptPasswordEncoder で生成）
INSERT INTO m_users (email, password, role) VALUES
    ('admin@example.com', '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi', 'ADMIN'),
    ('user@example.com',  '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi', 'USER')
ON CONFLICT (email) DO NOTHING;

INSERT INTO m_services(view_name, service_name, target_machine, status) VALUES
    ('Minecraft JE', 'G_Minecraft', '1', 'watch'),
    ('CoreKeeper', 'G_CoreKeeper', '1', 'watch'),
    ('Rust', 'G_Rust', '1', 'stop'),
    ('DiscordBot', 'O_Discord', '1', 'watch');