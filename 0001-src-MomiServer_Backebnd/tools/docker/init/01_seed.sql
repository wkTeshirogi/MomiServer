-- ===================================================
-- 開発・テスト用 初期データ
-- Flyway管理外のローカル専用初期化スクリプト
-- ===================================================

-- 開発用テストユーザー（パスワード: password123）
-- BCryptハッシュ: $2a$10$...（Spring Security の BCryptPasswordEncoder で生成）
CREATE TABLE IF NOT EXISTS users (
    id SERIAL PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL
);
INSERT INTO users (email, password, role) VALUES
    ('admin@example.com', '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi', 'ADMIN'),
    ('user@example.com',  '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi', 'USER')
ON CONFLICT (email) DO NOTHING;
