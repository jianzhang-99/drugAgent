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
    type VARCHAR(30) COMMENT '消息类型：assistant_text/assistant_clarify/assistant_result_card',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_session_id (session_id),
    INDEX idx_created_at (created_at),
    FOREIGN KEY (session_id) REFERENCES chat_session(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='聊天消息表';

-- OSS文件表
-- 存储上传到腾讯云COS的文件元信息，关联 rag_file 表
CREATE TABLE IF NOT EXISTS oss_file (
    id VARCHAR(36) PRIMARY KEY COMMENT '文件唯一标识（UUID）',
    session_id VARCHAR(36) DEFAULT NULL COMMENT '关联的会话ID（RAG文件可为空）',
    file_name VARCHAR(255) NOT NULL COMMENT '原始文件名',
    file_suffix VARCHAR(50) DEFAULT NULL COMMENT '文件扩展名',
    file_size BIGINT DEFAULT NULL COMMENT '文件大小（字节）',
    oss_url VARCHAR(500) NOT NULL COMMENT 'COS对象路径',
    file_type TINYINT NOT NULL DEFAULT 1 COMMENT '文件类型：1-对话附件，2-RAG知识库',
    upload_status TINYINT NOT NULL DEFAULT 1 COMMENT '上传状态：0-待上传，1-成功，2-失败，3-已删除',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_session_id (session_id),
    INDEX idx_file_type (file_type),
    INDEX idx_upload_status (upload_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OSS文件表（存储上传到COS的文件元信息）';

-- RAG文件表
-- 关联 OSS 文件与向量库 sourceId，支持文件溯源和按源删除
CREATE TABLE IF NOT EXISTS rag_file (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    oss_id VARCHAR(36) NOT NULL COMMENT '对应OSS文件id（关联oss_file.id），存UUID字符串',
    source_id VARCHAR(50) NOT NULL COMMENT '向量库中的sourceId（DOC-XXXXXXXX格式），用于按源删除向量',
    stored_name VARCHAR(255) DEFAULT NULL COMMENT '存储时的文件名（UUID+后缀），入库失败时可空',
    original_name VARCHAR(255) DEFAULT NULL COMMENT '原始文件名，入库失败时可空',
    file_type VARCHAR(20) DEFAULT NULL COMMENT '文件类型（扩展名）',
    file_size DECIMAL(10,0) DEFAULT NULL COMMENT '文件大小（字节）',
    scene VARCHAR(50) DEFAULT NULL COMMENT '场景：tender_review / contract_precheck 等',
    status TINYINT NOT NULL DEFAULT '0' COMMENT '0：刚上传 1：已学习 2：已删除',
    deleted TINYINT DEFAULT '0' COMMENT '软删除标记：0-未删除，1-已删除',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_oss_id (oss_id),
    UNIQUE KEY uk_source_id (source_id),
    KEY idx_deleted (deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='RAG文件表（关联OSS文件与向量库sourceId）';
