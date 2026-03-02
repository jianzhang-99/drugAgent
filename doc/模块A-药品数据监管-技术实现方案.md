# 模块A：药品数据监管 — 技术实现方案

> **版本**：v1.0 | **日期**：2026-03-02 | **技术栈**：Spring Boot 4.0 + MyBatis-Plus + EasyExcel + 千问(DashScope)

---

## 一、模块概述

药品数据监管模块是系统的核心数据入口，负责将医院药品使用记录通过 Excel 批量导入数据库，支持多维度查询筛选，并调用千问大模型进行智能分析（趋势识别、异常检测、风险预警）。

### 核心数据流

```
Excel文件 → 解析校验 → MySQL入库 → 条件查询/统计聚合 → Prompt注入 → 千问分析 → 结构化报告
```

---

## 二、包结构设计

```
com.liang.drugagent
├── controller
│   └── DrugController.java              // 药品监管相关接口
├── service
│   ├── DrugImportService.java           // Excel 导入服务
│   └── DrugMonitorService.java          // 数据统计 + AI分析服务
├── mapper
│   └── DrugUsageRecordMapper.java       // MyBatis-Plus Mapper
├── entity
│   └── DrugUsageRecord.java             // 数据库实体
├── dto
│   ├── DrugQueryDTO.java                // 查询条件DTO
│   └── DrugAnalyzeDTO.java              // 分析请求DTO
├── vo
│   ├── ImportResultVO.java              // 导入结果
│   ├── DrugStatsSummaryVO.java          // 统计摘要
│   └── AnalysisReportVO.java            // AI分析报告
├── listener
│   └── DrugExcelListener.java           // EasyExcel 行监听器
└── prompt
    └── DrugAnalysisPrompt.java          // 药品分析Prompt模板
```

---

## 三、数据库设计

### 表结构：`drug_usage_record`

```sql
CREATE TABLE drug_usage_record (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY  COMMENT '主键',
    drug_name     VARCHAR(200)   NOT NULL             COMMENT '药品名称',
    drug_code     VARCHAR(50)    DEFAULT NULL          COMMENT '药品编码',
    usage_amount  DECIMAL(10,2)  NOT NULL              COMMENT '使用量',
    usage_unit    VARCHAR(20)    DEFAULT '盒'          COMMENT '单位(盒/支/瓶)',
    usage_date    DATE           NOT NULL              COMMENT '使用日期',
    department    VARCHAR(100)   DEFAULT NULL          COMMENT '使用科室',
    drug_category VARCHAR(50)    DEFAULT NULL          COMMENT '药品分类(抗生素/麻醉药品等)',
    operator      VARCHAR(50)    DEFAULT NULL          COMMENT '操作人',
    batch_no      VARCHAR(50)    DEFAULT NULL          COMMENT '导入批次号(同一次导入相同)',
    created_at    DATETIME       DEFAULT CURRENT_TIMESTAMP COMMENT '入库时间',
    
    INDEX idx_drug_name (drug_name),
    INDEX idx_usage_date (usage_date),
    INDEX idx_department (department),
    INDEX idx_drug_category (drug_category),
    INDEX idx_composite (drug_name, usage_date)        -- 分析查询高频组合
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='药品使用记录';
```

> **设计要点**：
> - `batch_no` 字段标记同一次导入的所有记录，方便回滚或追溯
> - 组合索引 `(drug_name, usage_date)` 专门为 AI 分析中的聚合查询优化

---

## 四、功能详细实现

### 4.1 Excel 批量导入（A1）

#### 4.1.1 Excel 模板格式

| 列 | 字段 | 必填 | 校验规则 |
|---|------|:---:|---------|
| A | 药品名称 | ✅ | 非空，≤200字 |
| B | 药品编码 | ❌ | 格式 YPXXX |
| C | 使用量 | ✅ | 正数，≤99999999 |
| D | 单位 | ❌ | 枚举：盒/支/瓶/片/袋，默认"盒" |
| E | 使用日期 | ✅ | 日期格式，不晚于今天 |
| F | 科室 | ❌ | ≤100字 |
| G | 药品分类 | ❌ | 枚举：抗生素/解热镇痛/麻醉药品/心血管/消化系统/其他 |
| H | 操作人 | ❌ | ≤50字 |

#### 4.1.2 实现方案

**选型：EasyExcel**（阿里开源，内存占用低，适合大文件）

```java
// DrugExcelListener.java — 核心逻辑伪代码
public class DrugExcelListener extends AnalysisEventListener<DrugExcelRow> {
    
    private static final int BATCH_SIZE = 500;          // 每500行批量入库
    private List<DrugUsageRecord> buffer = new ArrayList<>();
    private List<ImportError> errors = new ArrayList<>();
    private int successCount = 0;
    
    @Override
    public void invoke(DrugExcelRow row, AnalysisContext context) {
        int rowIndex = context.readRowHolder().getRowIndex();
        
        // 1. 校验
        String error = validate(row);
        if (error != null) {
            errors.add(new ImportError(rowIndex, error));
            return;
        }
        
        // 2. 转换为实体
        DrugUsageRecord record = convert(row);
        record.setBatchNo(currentBatchNo);
        buffer.add(record);
        
        // 3. 达到批次大小时批量入库
        if (buffer.size() >= BATCH_SIZE) {
            flushBatch();
        }
    }
    
    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {
        if (!buffer.isEmpty()) {
            flushBatch();  // 处理剩余数据
        }
    }
    
    private void flushBatch() {
        drugUsageRecordMapper.insertBatchSomeColumn(buffer);
        successCount += buffer.size();
        buffer.clear();
    }
}
```

#### 4.1.3 接口设计

```
POST /api/drug/import
Content-Type: multipart/form-data

请求参数:
  file: Excel文件 (.xlsx)

成功响应:
{
  "code": 200,
  "message": "导入完成",
  "data": {
    "totalRows": 1000,
    "successCount": 985,
    "failCount": 15,
    "batchNo": "BATCH-20260302-001",
    "errors": [
      { "row": 23, "reason": "使用量不能为负数" },
      { "row": 45, "reason": "使用日期格式错误" }
    ]
  }
}
```

---

### 4.2 数据查询与筛选（A2）

#### 4.2.1 接口设计

```
GET /api/drug/list?drugName=阿莫西林&department=内科&startDate=2026-01-01&endDate=2026-02-28&page=1&size=20

成功响应:
{
  "code": 200,
  "data": {
    "records": [ ... ],
    "total": 235,
    "page": 1,
    "size": 20
  }
}
```

#### 4.2.2 动态查询实现

```java
// DrugMonitorService.java
public IPage<DrugUsageRecord> queryDrugRecords(DrugQueryDTO dto) {
    LambdaQueryWrapper<DrugUsageRecord> wrapper = new LambdaQueryWrapper<>();
    
    wrapper.like(StringUtils.hasText(dto.getDrugName()),
                 DrugUsageRecord::getDrugName, dto.getDrugName())
           .eq(StringUtils.hasText(dto.getDepartment()),
               DrugUsageRecord::getDepartment, dto.getDepartment())
           .eq(StringUtils.hasText(dto.getDrugCategory()),
               DrugUsageRecord::getDrugCategory, dto.getDrugCategory())
           .ge(dto.getStartDate() != null,
               DrugUsageRecord::getUsageDate, dto.getStartDate())
           .le(dto.getEndDate() != null,
               DrugUsageRecord::getUsageDate, dto.getEndDate())
           .orderByDesc(DrugUsageRecord::getUsageDate);
    
    return drugUsageRecordMapper.selectPage(
        new Page<>(dto.getPage(), dto.getSize()), wrapper
    );
}
```

#### 4.2.3 药品名称下拉接口（辅助）

```
GET /api/drug/names          → 返回去重药品名称列表（供前端下拉选择）
GET /api/drug/departments    → 返回去重科室列表
GET /api/drug/categories     → 返回去重药品分类列表
```

---

### 4.3 AI 智能分析（A3 — 核心）

#### 4.3.1 数据流详解

```
步骤1: 接收分析请求 (drugName + startDate + endDate)
                    │
步骤2: SQL聚合查询   │
   SELECT usage_date, SUM(usage_amount) as daily_total,
          COUNT(*) as record_count
   FROM drug_usage_record
   WHERE drug_name = ? AND usage_date BETWEEN ? AND ?
   GROUP BY usage_date
   ORDER BY usage_date
                    │
步骤3: Java侧统计    │
   ├── 日均用量      = avg(daily_totals)
   ├── 最大单日用量  = max(daily_totals) + 对应日期
   ├── 最小单日用量  = min(daily_totals) + 对应日期
   ├── 标准差        = std(daily_totals)
   ├── 环比增长率    = (后半段均值 - 前半段均值) / 前半段均值
   ├── 异常点检测    = 超出 均值±2倍标准差 的日期
   └── 趋势方向      = 线性回归斜率正负
                    │
步骤4: 组装Prompt     │
   将统计摘要注入模板 → 发送千问
                    │
步骤5: 解析返回       │
   千问返回JSON → 反序列化为 AnalysisReportVO
```

#### 4.3.2 统计聚合实现

```java
// DrugMonitorService.java
public DrugStatsSummaryVO aggregateStats(String drugName, 
                                          LocalDate startDate, 
                                          LocalDate endDate) {
    // 1. 查询每日聚合数据
    List<DailyUsageVO> dailyData = drugUsageRecordMapper.selectDailyAggregation(
        drugName, startDate, endDate
    );
    
    if (dailyData.isEmpty()) {
        throw new BusinessException("该时间段内无数据");
    }
    
    // 2. 基础统计
    List<Double> amounts = dailyData.stream()
        .map(DailyUsageVO::getDailyTotal)
        .map(BigDecimal::doubleValue)
        .toList();
    
    double avg = amounts.stream().mapToDouble(d -> d).average().orElse(0);
    double max = amounts.stream().mapToDouble(d -> d).max().orElse(0);
    double min = amounts.stream().mapToDouble(d -> d).min().orElse(0);
    double stdDev = calculateStdDev(amounts, avg);
    
    // 3. 环比增长率（前半段 vs 后半段）
    int mid = amounts.size() / 2;
    double firstHalfAvg = amounts.subList(0, mid).stream()
        .mapToDouble(d -> d).average().orElse(0);
    double secondHalfAvg = amounts.subList(mid, amounts.size()).stream()
        .mapToDouble(d -> d).average().orElse(0);
    double growthRate = firstHalfAvg > 0 
        ? (secondHalfAvg - firstHalfAvg) / firstHalfAvg * 100 : 0;
    
    // 4. 异常点检测 (超出2倍标准差)
    double upperBound = avg + 2 * stdDev;
    double lowerBound = avg - 2 * stdDev;
    List<AnomalyPointVO> anomalies = dailyData.stream()
        .filter(d -> d.getDailyTotal().doubleValue() > upperBound 
                  || d.getDailyTotal().doubleValue() < lowerBound)
        .map(d -> new AnomalyPointVO(d.getUsageDate(), d.getDailyTotal(),
             d.getDailyTotal().doubleValue() > upperBound ? "偏高" : "偏低"))
        .toList();
    
    // 5. 组装摘要
    return DrugStatsSummaryVO.builder()
        .drugName(drugName)
        .dateRange(startDate + " ~ " + endDate)
        .totalDays(dailyData.size())
        .dailyAvg(avg)
        .dailyMax(max)
        .dailyMin(min)
        .stdDev(stdDev)
        .growthRate(growthRate)
        .anomalies(anomalies)
        .dailyDetails(dailyData)
        .build();
}
```

#### 4.3.3 Prompt 模板设计

```java
// DrugAnalysisPrompt.java
public class DrugAnalysisPrompt {
    
    public static final String SYSTEM_PROMPT = """
        你是一位资深的医院药品监管分析专家。你的任务是基于提供的药品使用统计数据，
        给出专业的分析报告。
        
        请严格按以下JSON格式返回（不要返回其他内容）：
        {
          "trendSummary": "趋势总结（2-3句话）",
          "anomalyAnalysis": "异常分析（如有异常点，推测可能原因）",
          "riskLevel": "LOW/MEDIUM/HIGH/CRITICAL",
          "riskReason": "风险等级判断依据",
          "suggestions": ["建议1", "建议2", "建议3"]
        }
        """;
    
    public static String buildUserPrompt(DrugStatsSummaryVO stats) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 药品使用数据分析请求\n\n");
        sb.append("**药品名称**：").append(stats.getDrugName()).append("\n");
        sb.append("**分析时段**：").append(stats.getDateRange()).append("\n");
        sb.append("**统计天数**：").append(stats.getTotalDays()).append("天\n\n");
        
        sb.append("### 统计摘要\n");
        sb.append("- 日均用量：").append(String.format("%.1f", stats.getDailyAvg())).append("\n");
        sb.append("- 最大单日用量：").append(String.format("%.1f", stats.getDailyMax())).append("\n");
        sb.append("- 最小单日用量：").append(String.format("%.1f", stats.getDailyMin())).append("\n");
        sb.append("- 标准差：").append(String.format("%.2f", stats.getStdDev())).append("\n");
        sb.append("- 环比增长率：").append(String.format("%.1f%%", stats.getGrowthRate())).append("\n\n");
        
        if (!stats.getAnomalies().isEmpty()) {
            sb.append("### 已检测到的异常点\n");
            for (AnomalyPointVO a : stats.getAnomalies()) {
                sb.append("- ").append(a.getDate()).append("：用量 ")
                  .append(a.getAmount()).append("，").append(a.getType()).append("\n");
            }
        }
        
        sb.append("\n请基于以上数据进行专业分析，给出趋势判断、异常原因推测、风险等级和监管建议。");
        return sb.toString();
    }
}
```

#### 4.3.4 AI 分析 Service

```java
// DrugMonitorService.java
public AnalysisReportVO analyzeDrug(DrugAnalyzeDTO dto) {
    // 1. 聚合统计
    DrugStatsSummaryVO stats = aggregateStats(
        dto.getDrugName(), dto.getStartDate(), dto.getEndDate()
    );
    
    // 2. 构建 Prompt
    String systemPrompt = DrugAnalysisPrompt.SYSTEM_PROMPT;
    String userPrompt = DrugAnalysisPrompt.buildUserPrompt(stats);
    
    // 3. 调用千问
    String aiResponse = qwenService.chatWithSystem(systemPrompt, userPrompt);
    
    // 4. 解析 JSON 响应
    AnalysisReportVO report;
    try {
        report = objectMapper.readValue(aiResponse, AnalysisReportVO.class);
    } catch (JsonProcessingException e) {
        // JSON解析失败，降级处理：直接返回纯文本
        report = new AnalysisReportVO();
        report.setTrendSummary(aiResponse);
        report.setRiskLevel("UNKNOWN");
    }
    
    // 5. 补充统计数据到报告（前端图表需要）
    report.setStats(stats);
    return report;
}
```

#### 4.3.5 接口设计

```
POST /api/drug/analyze
Content-Type: application/json

请求体:
{
  "drugName": "阿莫西林胶囊",
  "startDate": "2026-01-01",
  "endDate": "2026-02-28"
}

成功响应:
{
  "code": 200,
  "data": {
    "stats": {
      "drugName": "阿莫西林胶囊",
      "dateRange": "2026-01-01 ~ 2026-02-28",
      "totalDays": 59,
      "dailyAvg": 150.3,
      "dailyMax": 380.0,
      "dailyMin": 45.0,
      "stdDev": 52.7,
      "growthRate": 12.5,
      "anomalies": [
        { "date": "2026-01-20", "amount": 380.0, "type": "偏高" }
      ],
      "dailyDetails": [ ... ]     // 前端折线图数据
    },
    "trendSummary": "近2个月阿莫西林用量整体呈上升趋势...",
    "anomalyAnalysis": "1月20日用量异常偏高，可能原因...",
    "riskLevel": "HIGH",
    "riskReason": "检测到异常用量峰值，且整体增长率较高",
    "suggestions": [
      "建议核查1月20日当天处方记录",
      "建议关注抗生素使用合理性",
      "建议与内科沟通用药方案"
    ]
  }
}
```

---

### 4.4 模板下载（A5）

```java
// DrugController.java
@GetMapping("/template")
public void downloadTemplate(HttpServletResponse response) throws IOException {
    response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    response.setHeader("Content-Disposition", 
        "attachment;filename=" + URLEncoder.encode("药品数据导入模板.xlsx", "UTF-8"));
    
    // 使用 EasyExcel 动态生成模板（含表头 + 示例行）
    EasyExcel.write(response.getOutputStream(), DrugExcelRow.class)
             .sheet("药品数据")
             .doWrite(List.of(DrugExcelRow.exampleRow()));
}
```

---

## 五、需要新增的依赖

```xml
<!-- pom.xml 新增 -->
<!-- MyBatis-Plus -->
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
    <version>3.5.9</version>
</dependency>

<!-- MySQL 驱动 -->
<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
    <scope>runtime</scope>
</dependency>

<!-- EasyExcel -->
<dependency>
    <groupId>com.alibaba</groupId>
    <artifactId>easyexcel</artifactId>
    <version>4.0.3</version>
</dependency>
```

---

## 六、配置项新增

```yaml
# application.yml 新增
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/drug_agent?useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=utf8mb4
    username: root
    password: your_password
    driver-class-name: com.mysql.cj.jdbc.Driver
  servlet:
    multipart:
      max-file-size: 50MB          # Excel最大50MB
      max-request-size: 50MB

mybatis-plus:
  mapper-locations: classpath:/mapper/**/*.xml
  configuration:
    map-underscore-to-camel-case: true
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl   # 开发环境开启SQL日志

# 药品导入配置
drug:
  import:
    batch-size: 500                # 批量入库行数
    template-path: templates/drug_import_template.xlsx
  upload:
    path: uploads/                 # 文件上传根目录
```

---

## 七、异常场景处理

| 场景 | 处理方式 |
|------|---------|
| Excel 格式不是 .xlsx | Controller 层校验文件后缀，返回 400 |
| 文件大小超限 | Spring 配置 `max-file-size`，框架自动拦截 |
| 表头列名不匹配 | EasyExcel Listener 的 `invokeHeadMap` 中校验 |
| 单行数据校验失败 | 跳过该行，记录到 errors 列表，不影响其他行 |
| 数据库写入失败 | 按批次事务回滚，记录失败批次信息 |
| 千问 API 调用超时 | 设置 30s 超时，超时后降级返回统计摘要（不含AI分析） |
| 千问返回非 JSON | try-catch 解析异常，降级为纯文本报告 |
