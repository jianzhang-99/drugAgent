-- 会话表
CREATE TABLE chat_session (
    id VARCHAR(36) PRIMARY KEY COMMENT '会话唯一标识',
    title VARCHAR(255) COMMENT '会话标题（AI生成或用户定义）',
    scene VARCHAR(50) COMMENT '场景类型：tender/contract/compliance',
    user_id VARCHAR(36) DEFAULT 'default_user' COMMENT '用户ID',
    is_deleted TINYINT DEFAULT 0 COMMENT '软删除标记：0-未删除，1-已删除',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_user_id (user_id),
    INDEX idx_created_at (created_at),
    INDEX idx_is_deleted (is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='聊天会话表';

-- 消息表
CREATE TABLE chat_message (
    id VARCHAR(36) PRIMARY KEY COMMENT '消息唯一标识',
    session_id VARCHAR(36) NOT NULL COMMENT '所属会话ID',
    role VARCHAR(20) NOT NULL COMMENT '角色：user/assistant/system',
    content LONGTEXT COMMENT '消息内容',
    metadata JSON COMMENT '扩展信息（AgentResult等）',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_session_id (session_id),
    INDEX idx_created_at (created_at),
    FOREIGN KEY (session_id) REFERENCES chat_session(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='聊天消息表';
