package com.liang.drugagent.controller;

import com.liang.drugagent.domain.Session;
import com.liang.drugagent.service.SessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 会话管理控制器
 *
 * @author liangjiajian
 */
@Slf4j
@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService;

    /**
     * 创建新会话
     */
    @PostMapping
    public ResponseEntity<Session> createSession(@RequestBody SessionCreateRequest request) {
        Session session = sessionService.createSession(
            request.getTitle(),
            request.getDateGroup(),
            request.getScene()
        );
        return ResponseEntity.ok(session);
    }

    /**
     * 获取所有会话
     */
    @GetMapping
    public ResponseEntity<List<Session>> getAllSessions() {
        List<Session> sessions = sessionService.getAllSessions();
        return ResponseEntity.ok(sessions);
    }

    /**
     * 根据日期组获取会话
     */
    @GetMapping("/date-group/{dateGroup}")
    public ResponseEntity<List<Session>> getSessionsByDateGroup(@PathVariable String dateGroup) {
        List<Session> sessions = sessionService.getSessionsByDateGroup(dateGroup);
        return ResponseEntity.ok(sessions);
    }

    /**
     * 根据ID获取会话
     */
    @GetMapping("/{id}")
    public ResponseEntity<Session> getSessionById(@PathVariable String id) {
        return sessionService.getSessionById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 删除会话
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSession(@PathVariable String id) {
        if (sessionService.deleteSession(id)) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    /**
     * 添加消息到会话
     */
    @PostMapping("/{id}/messages")
    public ResponseEntity<Void> addMessage(@PathVariable String id, @RequestBody Session.Message message) {
        sessionService.addMessageToSession(id, message);
        return ResponseEntity.ok().build();
    }

    /**
     * 创建会话请求
     */
    public static class SessionCreateRequest {
        private String title;
        private String dateGroup;
        private String scene;

        // Getters and setters
        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getDateGroup() {
            return dateGroup;
        }

        public void setDateGroup(String dateGroup) {
            this.dateGroup = dateGroup;
        }

        public String getScene() {
            return scene;
        }

        public void setScene(String scene) {
            this.scene = scene;
        }
    }
}