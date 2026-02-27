package com.liang.drugagent.controller;

import com.liang.drugagent.service.QwenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 千问Demo测试接口
 * 用于验证 DashScope API 接入是否正常
 */
@Tag(name = "千问Demo测试", description = "用于验证千问(Qwen)大模型接入是否正常")
@RestController
@RequestMapping("/qwen")
public class QwenDemoController {

    private final QwenService qwenService;

    public QwenDemoController(QwenService qwenService) {
        this.qwenService = qwenService;
    }

    @Operation(summary = "对话测试", description = "发送一段文本给千问模型，返回模型的回复")
    @GetMapping("/chat")
    public String chat(
            @Parameter(description = "用户输入的提示词")
            @RequestParam(value = "prompt", defaultValue = "你好，请介绍一下你自己") String prompt) {
        return qwenService.chat(prompt);
    }
}
