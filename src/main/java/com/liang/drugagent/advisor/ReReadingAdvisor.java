package com.liang.drugagent.advisor;

import org.springframework.ai.chat.client.advisor.api.AdvisedRequest;
import org.springframework.ai.chat.client.advisor.api.AdvisedResponse;
import org.springframework.ai.chat.client.advisor.api.CallAroundAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAroundAdvisorChain;
import org.springframework.ai.chat.prompt.PromptTemplate;

import java.util.Map;

/**
 * 自定义Re2 Advisor
 * 提高大模型的推理能力
 *
 * @author liangjiajian
 */
public class ReReadingAdvisor implements CallAroundAdvisor {

	private static final String DEFAULT_RE2_ADVISE_TEMPLATE = """
			{re2_input_query}
			Read the question again: {re2_input_query}
			""";

	private final String re2AdviseTemplate;

	private int order = 0;

	public ReReadingAdvisor() {
		this(DEFAULT_RE2_ADVISE_TEMPLATE);
	}

	public ReReadingAdvisor(String re2AdviseTemplate) {
		this.re2AdviseTemplate = re2AdviseTemplate;
	}

	@Override
	public AdvisedResponse aroundCall(AdvisedRequest advisedRequest, CallAroundAdvisorChain chain) {
		// 从 AdvisedRequest 获取原始用户文本
		String originalUserText = advisedRequest.userText();
		
		// 渲染增强后的文本
		String augmentedUserText = new PromptTemplate(this.re2AdviseTemplate, Map.of("re2_input_query", originalUserText)).render();

		// 在最新版本中，AdvisedRequest 是一个 Record，需要填充其完整的 14 个组件
		// 最后一个参数 metadata 如果无法通过 .metadata() 获取，我们传入一个空的 HashMap
		AdvisedRequest updatedRequest = new AdvisedRequest(
				advisedRequest.chatModel(),
				augmentedUserText,
				advisedRequest.systemText(),
				advisedRequest.chatOptions(),
				advisedRequest.media(),
				advisedRequest.functionNames(),
				advisedRequest.functionCallbacks(),
				advisedRequest.messages(),
				advisedRequest.userParams(),
				advisedRequest.systemParams(),
				advisedRequest.advisors(),
				advisedRequest.adviseContext(),
				advisedRequest.toolContext(),
				new java.util.HashMap<>()
		);

		return chain.nextAroundCall(updatedRequest);
	}

	@Override
	public int getOrder() {
		return this.order;
	}

	public ReReadingAdvisor withOrder(int order) {
		this.order = order;
		return this;
	}

	@Override
	public String getName() {
		return "ReReadingAdvisor";
	}
}