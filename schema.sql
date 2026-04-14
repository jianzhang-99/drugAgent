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

-- 标书案例文档表
-- 负责持久化标书审查案例中的文档元数据，并关联 oss_file
CREATE TABLE IF NOT EXISTS tender_case_document (
    id VARCHAR(36) PRIMARY KEY COMMENT '文档唯一标识（UUID）',
    case_id VARCHAR(36) NOT NULL COMMENT '所属标书案例ID',
    oss_file_id VARCHAR(36) DEFAULT NULL COMMENT '关联的OSS文件ID',
    file_name VARCHAR(255) NOT NULL COMMENT '原始文件名',
    document_name VARCHAR(255) DEFAULT NULL COMMENT '文档展示名',
    file_type VARCHAR(50) DEFAULT NULL COMMENT '文件扩展名',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '文档处理状态',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_case_id (case_id),
    INDEX idx_oss_file_id (oss_file_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='标书案例文档表';

-- 人工抽检样本表
-- 记录从历史case中抽出的待检样本，包含原始审查结果和人工审核结论
CREATE TABLE IF NOT EXISTS manual_review_sample (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    sample_id VARCHAR(36) NOT NULL COMMENT '样本唯一标识（UUID）',
    case_id VARCHAR(36) NOT NULL COMMENT '关联的标书案例ID',
    selection_strategy VARCHAR(50) NOT NULL COMMENT '抽样策略：RANDOM/HIGH_DEVIATION/HIGH_RISK_BOUNDARY',
    llm_confidence DOUBLE DEFAULT NULL COMMENT 'LLM置信度（0.0~1.0）',
    risk_score INT DEFAULT NULL COMMENT '系统风险评分',
    risk_level VARCHAR(20) DEFAULT NULL COMMENT '系统风险等级：HIGH/MEDIUM/LOW',
    review_status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '审核状态：PENDING/APPROVED/REJECTED',
    reviewed_by VARCHAR(100) DEFAULT NULL COMMENT '审核人',
    reviewed_at DATETIME DEFAULT NULL COMMENT '审核时间',
    review_comment TEXT DEFAULT NULL COMMENT '审核意见',
    system_result LONGTEXT DEFAULT NULL COMMENT '系统判定结果（JSON格式，存储RuleHit列表）',
    manual_result LONGTEXT DEFAULT NULL COMMENT '人工判定结果（JSON格式）',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sample_id (sample_id),
    INDEX idx_case_id (case_id),
    INDEX idx_review_status (review_status),
    INDEX idx_selection_strategy (selection_strategy)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='人工抽检样本表';

-- 评测偏差记录表
-- 记录系统判定与人工判定之间的偏差，用于分析各规则的precision/recall/F1
CREATE TABLE IF NOT EXISTS evaluation_deviation_record (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    sample_id VARCHAR(36) NOT NULL COMMENT '关联的抽检样本ID',
    case_id VARCHAR(36) NOT NULL COMMENT '关联的标书案例ID',
    rule_code VARCHAR(50) DEFAULT NULL COMMENT '规则编码（如 W-M1、W-P1 等）',
    rule_name VARCHAR(200) DEFAULT NULL COMMENT '规则名称',
    deviation_type VARCHAR(50) NOT NULL COMMENT '偏差类型：FALSE_POSITIVE/FALSE_NEGATIVE/SCORE_DIFF',
    system_confidence DOUBLE DEFAULT NULL COMMENT '系统判定置信度（0.0~1.0）',
    manual_confidence DOUBLE DEFAULT NULL COMMENT '人工判定置信度（0.0~1.0）',
    system_score INT DEFAULT NULL COMMENT '系统评分',
    manual_score INT DEFAULT NULL COMMENT '人工评分',
    system_judgment LONGTEXT DEFAULT NULL COMMENT '系统判定结果（JSON格式）',
    manual_judgment LONGTEXT DEFAULT NULL COMMENT '人工判定结果（JSON格式）',
    deviation_description TEXT DEFAULT NULL COMMENT '偏差说明',
    prompt_improvement TEXT DEFAULT NULL COMMENT '建议的Prompt改进方向',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    INDEX idx_sample_id (sample_id),
    INDEX idx_case_id (case_id),
    INDEX idx_rule_code (rule_code),
    INDEX idx_deviation_type (deviation_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评测偏差记录表';
