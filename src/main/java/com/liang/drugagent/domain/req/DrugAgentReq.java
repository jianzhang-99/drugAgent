package com.liang.drugagent.domain.req;

import lombok.Getter;
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
public class DrugAgentReq {

    private String sessionId;
    private String userId;
    private String query;
    private String sceneHint;
    private List<String> fileIds;
    private Map<String, Object> metadata;

}
