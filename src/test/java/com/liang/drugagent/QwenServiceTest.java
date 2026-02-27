package com.liang.drugagent;

import com.liang.drugagent.service.QwenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 千问接入功能测试
 * 直接调用 QwenService 验证 API Key 和模型调用是否正常
 */
@SpringBootTest
public class QwenServiceTest {

    @Autowired
    private QwenService qwenService;

    @Test
    public void testChat() {
        String prompt = "你好，请用一句话介绍一下你自己";
        System.out.println("===== 发送提示词: " + prompt + " =====");
        String result = qwenService.chat(prompt);
        System.out.println("===== 千问回复: =====");
        System.out.println(result);
        // 如果能正常输出回复内容且不包含"异常"字样，说明接入成功
        assert result != null && !result.contains("调用千问接口异常");
    }
}
