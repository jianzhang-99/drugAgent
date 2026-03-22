package com.liang.drugagent.domain;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 会话实体类
 *
 * @author liangjiajian
 */
@Data
@Accessors(chain = true)
public class Session {

    private String id;
    private String title;
    private String dateGroup;
    private String scene;
    private List<Message> messages = new ArrayList<>();
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 消息实体
     */
    @Data
    @Accessors(chain = true)
    public static class Message {
        private String role;
        private String content;
        private Map<String, Object> metadata;
    }
}