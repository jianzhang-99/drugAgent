package com.liang.drugagent.infrastructure.llm.facade;

import com.liang.drugagent.domain.req.DrugAgentReq;
import com.liang.drugagent.domain.resp.DrugAgentResp;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * LLM Facade 接口。
 *
 * <p>抽象大语言模型交互，提供可替换的LLM实现。</p>
 *
 * @author liangjiajian
 */
public interface LlmFacade {

    /**
     * 同步对话。
     */
    DrugAgentResp chat(DrugAgentReq req);

    /**
     * 流式对话。
     */
    SseEmitter streamChat(DrugAgentReq req);

    /**
     * 获取提供商名称。
     */
    String getProvider();
}
