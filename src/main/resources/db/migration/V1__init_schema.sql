-- ============================================================
-- V1__init_schema.sql
-- ============================================================

CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    is_premium BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE bots (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    persona_description TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);


CREATE TABLE posts (
    id BIGSERIAL PRIMARY KEY,
    author_id BIGINT NOT NULL,
    author_type VARCHAR(10) NOT NULL CHECK (author_type IN ('USER', 'BOT')),
    like_count INT NOT NULL DEFAULT 0,
    content TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE comments (
    id BIGSERIAL PRIMARY KEY,
    post_id BIGINT NOT NULL,
    author_id BIGINT NOT NULL,
    author_type VARCHAR(10) NOT NULL CHECK (author_type IN ('USER', 'BOT')),
    content TEXT NOT NULL,
    depth_level INT NOT NULL DEFAULT 0 CHECK (depth_level >= 0 AND depth_level <= 20),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_comments_post
        FOREIGN KEY (post_id)
        REFERENCES posts(id)
        ON DELETE CASCADE
);

-- Indexes for performance
CREATE INDEX idx_posts_author       ON posts(author_id, author_type);
CREATE INDEX idx_comments_post_id   ON comments(post_id);
CREATE INDEX idx_comments_author    ON comments(author_id, author_type);
CREATE INDEX idx_comments_depth     ON comments(depth_level);