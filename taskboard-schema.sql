-- ============================================================
-- Drug Agent 任务看板 - 数据库 Schema
-- Version: 1.0.0
-- Description: 任务看板核心表结构
-- ============================================================

-- -------------------------------------------------------
-- 1. 任务卡片表 (task_card)
-- 核心业务表：存储所有任务卡片信息
-- -------------------------------------------------------
CREATE TABLE IF NOT EXISTS task_card (
    id VARCHAR(36) PRIMARY KEY COMMENT '任务卡片唯一标识(UUID)',
    case_id VARCHAR(36) COMMENT '关联的标书审查Case ID',
    trace_id VARCHAR(36) COMMENT '链路追踪ID',
    task_name VARCHAR(255) NOT NULL COMMENT '任务名称',
    task_type VARCHAR(50) NOT NULL DEFAULT 'TENDER_REVIEW' COMMENT '任务类型: TENDER_REVIEW/CONTRACT_CHECK/COMPLIANCE_ALERT',
    scene VARCHAR(50) COMMENT '场景标识',

    -- 状态与进度
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '任务状态: PENDING/PARSING/PARSED/RUNNING/COMPLETED/FAILED',
    progress INT DEFAULT 0 COMMENT '进度百分比 0-100',
    current_step VARCHAR(100) COMMENT '当前执行步骤',

    -- 风险等级
    risk_level VARCHAR(20) DEFAULT 'UNKNOWN' COMMENT '风险等级: HIGH/MEDIUM/LOW/UNKNOWN',
    risk_tags JSON COMMENT '风险标签列表',

    -- 执行信息
    submitted_by VARCHAR(100) DEFAULT 'system' COMMENT '提交人',
    assigned_to VARCHAR(100) COMMENT '指派人',
    priority INT DEFAULT 5 COMMENT '优先级 1-10 (1最高)',
    deadline DATETIME COMMENT '截止时间',

    -- 结果摘要
    score INT COMMENT '综合评分 0-100',
    summary TEXT COMMENT '任务结果摘要',
    hit_rules INT DEFAULT 0 COMMENT '命中规则数量',

    -- 时间戳
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    started_at DATETIME COMMENT '开始执行时间',
    completed_at DATETIME COMMENT '完成时间',

    -- 软删除
    is_deleted TINYINT DEFAULT 0 COMMENT '软删除标记: 0-未删除, 1-已删除',
    delete_reason VARCHAR(500) COMMENT '删除原因',

    -- 索引
    INDEX idx_case_id (case_id),
    INDEX idx_trace_id (trace_id),
    INDEX idx_status (status),
    INDEX idx_risk_level (risk_level),
    INDEX idx_priority (priority),
    INDEX idx_submitted_by (submitted_by),
    INDEX idx_created_at (created_at),
    INDEX idx_is_deleted (is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务卡片表';

-- -------------------------------------------------------
-- 2. 任务阶段表 (task_phase)
-- 存储任务的详细执行阶段
-- -------------------------------------------------------
CREATE TABLE IF NOT EXISTS task_phase (
    id VARCHAR(36) PRIMARY KEY,
    task_id VARCHAR(36) NOT NULL COMMENT '所属任务ID',
    phase_name VARCHAR(100) NOT NULL COMMENT '阶段名称',
    phase_order INT NOT NULL COMMENT '阶段顺序',
    status VARCHAR(20) DEFAULT 'WAITING' COMMENT '阶段状态: WAITING/RUNNING/COMPLETED/SKIPPED/FAILED',
    progress INT DEFAULT 0 COMMENT '阶段进度 0-100',
    message TEXT COMMENT '阶段消息',
    started_at DATETIME COMMENT '阶段开始时间',
    completed_at DATETIME COMMENT '阶段完成时间',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_task_id (task_id),
    FOREIGN KEY (task_id) REFERENCES task_card(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务阶段表';

-- -------------------------------------------------------
-- 3. 任务操作日志表 (task_operation_log)
-- 记录所有任务操作审计日志
-- -------------------------------------------------------
CREATE TABLE IF NOT EXISTS task_operation_log (
    id VARCHAR(36) PRIMARY KEY,
    task_id VARCHAR(36) NOT NULL COMMENT '任务ID',
    operator VARCHAR(100) COMMENT '操作人',
    operation VARCHAR(50) NOT NULL COMMENT '操作类型: CREATE/UPDATE_STATUS/ASSIGN/PRIORITY_CHANGE/DELETE',
    before_value JSON COMMENT '操作前的值',
    after_value JSON COMMENT '操作后的值',
    remark VARCHAR(500) COMMENT '备注',
    ip_address VARCHAR(50) COMMENT 'IP地址',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_task_id (task_id),
    INDEX idx_operator (operator),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务操作日志表';

-- -------------------------------------------------------
-- 4. 风险项表 (risk_item)
-- 存储识别出的风险项详情
-- -------------------------------------------------------
CREATE TABLE IF NOT EXISTS risk_item (
    id VARCHAR(36) PRIMARY KEY,
    task_id VARCHAR(36) NOT NULL COMMENT '所属任务ID',
    risk_name VARCHAR(200) NOT NULL COMMENT '风险项名称',
    risk_level VARCHAR(20) NOT NULL COMMENT '风险等级: HIGH/MEDIUM/LOW',
    risk_type VARCHAR(50) COMMENT '风险类型',
    description TEXT COMMENT '风险描述',
    evidence JSON COMMENT '证据详情',
    suggestion TEXT COMMENT '处理建议',
    is_dealt TINYINT DEFAULT 0 COMMENT '是否已处理: 0-未处理, 1-已处理',
    dealt_by VARCHAR(100) COMMENT '处理人',
    dealt_at DATETIME COMMENT '处理时间',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_task_id (task_id),
    INDEX idx_risk_level (risk_level),
    INDEX idx_is_dealt (is_dealt)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='风险项表';

-- -------------------------------------------------------
-- 5. 任务统计视图 (v_task_statistics)
-- 任务看板统计数据视图
-- -------------------------------------------------------
CREATE OR REPLACE VIEW v_task_statistics AS
SELECT
    COUNT(*) AS total_count,
    SUM(CASE WHEN status = 'PENDING' THEN 1 ELSE 0 END) AS pending_count,
    SUM(CASE WHEN status = 'PARSING' OR status = 'PARSED' THEN 1 ELSE 0 END) AS parsing_count,
    SUM(CASE WHEN status = 'RUNNING' THEN 1 ELSE 0 END) AS running_count,
    SUM(CASE WHEN status = 'COMPLETED' THEN 1 ELSE 0 END) AS completed_count,
    SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END) AS failed_count,
    SUM(CASE WHEN risk_level = 'HIGH' AND status = 'COMPLETED' THEN 1 ELSE 0 END) AS high_risk_count,
    SUM(CASE WHEN risk_level = 'MEDIUM' AND status = 'COMPLETED' THEN 1 ELSE 0 END) AS medium_risk_count,
    SUM(CASE WHEN risk_level = 'LOW' AND status = 'COMPLETED' THEN 1 ELSE 0 END) AS low_risk_count,
    AVG(CASE WHEN status = 'COMPLETED' THEN score END) AS avg_score
FROM task_card
WHERE is_deleted = 0;

-- -------------------------------------------------------
-- 6. 任务卡片视图 (v_task_card_view)
-- 任务看板主视图，包含关联数据
-- -------------------------------------------------------
CREATE OR REPLACE VIEW v_task_card_view AS
SELECT
    tc.*,
    tp.phase_count,
    tp.completed_phase_count,
    ri.risk_item_count,
    ri.unhandled_risk_count,
    CASE
        WHEN tc.status = 'COMPLETED' THEN '已完成'
        WHEN tc.status = 'RUNNING' THEN '执行中'
        WHEN tc.status = 'PENDING' THEN '待处理'
        WHEN tc.status = 'PARSING' THEN '解析中'
        WHEN tc.status = 'FAILED' THEN '已失败'
        ELSE tc.status
    END AS status_text,
    CASE
        WHEN tc.risk_level = 'HIGH' THEN '高风险'
        WHEN tc.risk_level = 'MEDIUM' THEN '中风险'
        WHEN tc.risk_level = 'LOW' THEN '低风险'
        WHEN tc.risk_level = 'UNKNOWN' THEN '待评估'
        ELSE tc.risk_level
    END AS risk_level_text
FROM task_card tc
LEFT JOIN (
    SELECT task_id,
           COUNT(*) AS phase_count,
           SUM(CASE WHEN status = 'COMPLETED' THEN 1 ELSE 0 END) AS completed_phase_count
    FROM task_phase
    GROUP BY task_id
) tp ON tc.id = tp.task_id
LEFT JOIN (
    SELECT task_id,
           COUNT(*) AS risk_item_count,
           SUM(CASE WHEN is_dealt = 0 THEN 1 ELSE 0 END) AS unhandled_risk_count
    FROM risk_item
    GROUP BY task_id
) ri ON tc.id = ri.task_id
WHERE tc.is_deleted = 0;

-- -------------------------------------------------------
-- 7. 初始化数据：插入测试任务卡片
-- -------------------------------------------------------
INSERT INTO task_card (id, case_id, task_name, task_type, scene, status, progress, risk_level, submitted_by, priority, score, summary, hit_rules, created_at)
VALUES
    ('tc-001', 'case-001', '2024年度药品采购标书审查', 'TENDER_REVIEW', 'TENDER', 'COMPLETED', 100, 'HIGH', '张三', 1, 65, '发现3处高风险项，包括报价异常和资质造假嫌疑', 5, NOW() - INTERVAL 2 DAY),
    ('tc-002', 'case-002', '医疗器械采购合同预审', 'CONTRACT_CHECK', 'CONTRACT', 'RUNNING', 60, 'MEDIUM', '李四', 2, NULL, '合同条款审查中，已发现2处需关注点', 2, NOW() - INTERVAL 1 DAY),
    ('tc-003', 'case-003', '供应商合规性预警', 'COMPLIANCE_ALERT', 'COMPLIANCE', 'PENDING', 0, 'UNKNOWN', '王五', 3, NULL, NULL, 0, NOW()),
    ('tc-004', 'case-004', '药品采购二次议价审查', 'TENDER_REVIEW', 'TENDER', 'COMPLETED', 100, 'LOW', '赵六', 4, 88, '整体合规，仅需轻微优化', 1, NOW() - INTERVAL 3 DAY),
    ('tc-005', 'case-005', '冷链物流配送合同检查', 'CONTRACT_CHECK', 'CONTRACT', 'RUNNING', 30, 'UNKNOWN', '孙七', 2, NULL, '文档解析中...', 0, NOW() - INTERVAL 5 HOUR);

-- 插入阶段数据
INSERT INTO task_phase (id, task_id, phase_name, phase_order, status, progress, started_at, completed_at)
VALUES
    ('tp-001', 'tc-001', '文档解析', 1, 'COMPLETED', 100, NOW() - INTERVAL 2 DAY, NOW() - INTERVAL 2 DAY + INTERVAL 5 MINUTE),
    ('tp-002', 'tc-001', '资质审查', 2, 'COMPLETED', 100, NOW() - INTERVAL 2 DAY + INTERVAL 5 MINUTE, NOW() - INTERVAL 2 DAY + INTERVAL 15 MINUTE),
    ('tp-003', 'tc-001', '风险识别', 3, 'COMPLETED', 100, NOW() - INTERVAL 2 DAY + INTERVAL 15 MINUTE, NOW() - INTERVAL 2 DAY + INTERVAL 30 MINUTE),
    ('tp-004', 'tc-001', '报告生成', 4, 'COMPLETED', 100, NOW() - INTERVAL 2 DAY + INTERVAL 30 MINUTE, NOW() - INTERVAL 2 DAY + INTERVAL 45 MINUTE),
    ('tp-005', 'tc-002', '文档解析', 1, 'COMPLETED', 100, NOW() - INTERVAL 1 DAY, NOW() - INTERVAL 1 DAY + INTERVAL 10 MINUTE),
    ('tp-006', 'tc-002', '条款分析', 2, 'RUNNING', 60, NOW() - INTERVAL 12 HOUR, NULL);

-- 插入风险项
INSERT INTO risk_item (id, task_id, risk_name, risk_level, risk_type, description, suggestion, is_dealt)
VALUES
    ('ri-001', 'tc-001', '报价异常偏高', 'HIGH', 'PRICE_ANOMALY', '三个供应商报价均高于市场均价30%以上', '建议进一步核实成本构成', 0),
    ('ri-002', 'tc-001', '资质文件疑似造假', 'HIGH', 'DOCUMENT_FORGERY', '供应商B的GMP证书编号与官方记录不符', '需立即联系供应商核实', 0),
    ('ri-003', 'tc-001', '授权委托书缺失', 'MEDIUM', 'DOCUMENT_MISSING', '供应商C未提供有效的授权委托书', '补充授权文件', 1),
    ('ri-004', 'tc-002', '违约金条款过重', 'MEDIUM', 'CLAUSE_UNFAIR', '合同规定违约金比例为合同金额的30%', '建议协商调整为20%', 0),
    ('ri-005', 'tc-002', '争议解决条款不明确', 'LOW', 'CLAUSE_AMBIGUOUS', '合同未明确约定争议解决方式', '补充仲裁条款', 0);
