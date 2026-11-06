CREATE TABLE users_v1 (
    user_id INTEGER PRIMARY KEY,
    username VARCHAR(64) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_users_v1_username ON users_v1(username);
