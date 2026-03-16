package com.liang.drugagent.controller;

import com.liang.drugagent.domain.GeneralResponse;
import com.liang.drugagent.domain.req.DrugAnalyzeReq;
import com.liang.drugagent.domain.resp.AnalysisReportResp;
import com.liang.drugagent.service.AgentChatService;
import com.liang.drugagent.service.DrugMonitorService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 药品数据监控接口
 */
@RestController
@RequestMapping("/drug")
public class DrugController {

    private final AgentChatService agentChatService;
    private final DrugMonitorService drugMonitorService;

    public DrugController(AgentChatService agentChatService, DrugMonitorService drugMonitorService) {
        this.agentChatService = agentChatService;
        this.drugMonitorService = drugMonitorService;
    }

    @Operation(summary = "AI药品数据分析")
    @PostMapping("/analyze")
    public GeneralResponse<AnalysisReportResp> analyze(@RequestBody DrugAnalyzeReq req) {
        // 保留原有的调用（假设原有的已经写了基础逻辑）
        // 为了配合我们升级后的 SystemPrompt，我们也可以在这里直接走 AgentChatService
        
        String mockStats = "{\"日均用量\": 120.5, \"最大峰值\": 520.0, \"增速\": \"环比增长150%\"}";
        String promptData = "请根据以下药品数据特征进行分析：" + req.getDrugName() + mockStats;
        
        // 传递给 Data Analysis 人设场景，开启对话记忆 (SessionID 用药名区分以便针对性追问)
        String sessionId = "analysis-session-" + req.getDrugName();
        String result = agentChatService.chatWithScene(promptData, "data_analysis", sessionId);
        
        AnalysisReportResp resp = new AnalysisReportResp();
        resp.setTrendSummary(result);
        resp.setRiskLevel("MEDIUM");
        
        return GeneralResponse.success(resp);
    }

    // ----- 保障前端不报 404 的 Mock 接口 -----
    
    @Operation(summary = "Mock药品名称列表")
    @GetMapping("/names")
    public GeneralResponse<List<String>> getNames() {
        return GeneralResponse.success(List.of("阿莫西林", "布洛芬", "头孢克肟", "甲硝唑"));
    }

    @Operation(summary = "Mock药品列表查询")
    @GetMapping("/list")
    public GeneralResponse<Map<String, Object>> getList() {
        return GeneralResponse.success(Map.of(
            "records", List.of(),
            "total", 0
        ));
    }

    @Operation(summary = "Mock上传Excel")
    @PostMapping("/import")
    public GeneralResponse<Map<String, Object>> importExcel(@RequestParam("file") MultipartFile file) {
        return GeneralResponse.success(Map.of("totalRows", 1, "successCount", 1));
    }

    @Operation(summary = "Mock模板下载")
    @GetMapping("/template")
    public GeneralResponse<String> downloadTemplate() {
        return GeneralResponse.success("ok");
    }
}
