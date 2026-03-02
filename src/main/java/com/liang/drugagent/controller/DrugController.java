package com.liang.drugagent.controller;

import com.liang.drugagent.dto.DrugAnalyzeDTO;
import com.liang.drugagent.service.DrugMonitorService;
import com.liang.drugagent.vo.AnalysisReportVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 药品数据监管接口
 */
@Tag(name = "药品数据监控", description = "提供药品数据的导入、查询与 AI 分析功能")
@RestController
@RequestMapping("/drug")
@RequiredArgsConstructor
public class DrugController {

    private final DrugMonitorService drugMonitorService;

    @Operation(summary = "AI 智能分析", description = "对指定时间段内的药品使用数据进行统计，并通过千问大模型生成分析报告")
    @PostMapping("/analyze")
    public AnalysisReportVO analyze(@RequestBody DrugAnalyzeDTO dto) {
        // 在实际项目中，外层应该包装一个统一的 Result<T> 对象
        return drugMonitorService.analyzeDrug(dto);
    }
}
