-- Add parent_comment_id to support nested comments (reply-to-comment)
ALTER TABLE comments
ADD COLUMN parent_comment_id BIGINT;

-- Self-referencing foreign key to maintain comment hierarchy
ALTER TABLE comments
ADD CONSTRAINT fk_comments_parent
FOREIGN KEY (parent_comment_id)
REFERENCES comments(id)
ON DELETE CASCADE;

-- Index to optimize fetching replies by parent comment
CREATE INDEX idx_comments_parent ON comments(parent_comment_id);