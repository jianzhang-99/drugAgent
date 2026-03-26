package com.liang.drugagent.controller.domain.request.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 更新会话标题请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateSessionTitleReq {

    /** 新标题 */
    private String title;
}
