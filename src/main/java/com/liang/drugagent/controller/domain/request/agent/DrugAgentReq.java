package com.liang.drugagent.controller.domain.request.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.Map;

/**
 * Drug Agent 请求对象。
 *
 * @author liangjiajian
 */
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DrugAgentReq {

    private String sessionId;
    private String userId;
    private String query;
    private String sceneHint;
    private List<String> fileIds;
    private Map<String, Object> metadata;

}
