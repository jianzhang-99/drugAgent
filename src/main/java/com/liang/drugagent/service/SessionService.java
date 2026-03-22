package com.liang.drugagent.service;

import com.liang.drugagent.domain.Session;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 会话服务（内存存储）
 *
 * @author liangjiajian
 */
@Slf4j
@Service
public class SessionService {

    private final Map<String, Session> sessionStore = new ConcurrentHashMap<>();
    private final Map<String, List<Session>> dateGroupIndex = new ConcurrentHashMap<>();

    /**
     * 创建新会话
     */
    public Session createSession(String title, String dateGroup, String scene) {
        Session session = new Session();
        session.setId(UUID.randomUUID().toString());
        session.setTitle(title);
        session.setDateGroup(dateGroup);
        session.setScene(scene);
        session.setCreatedAt(LocalDateTime.now());
        session.setUpdatedAt(LocalDateTime.now());

        sessionStore.put(session.getId(), session);
        indexByDateGroup(session);

        log.info("Created new session: id={}, title={}, scene={}", session.getId(), title, scene);
        return session;
    }

    /**
     * 获取所有会话
     */
    public List<Session> getAllSessions() {
        return new ArrayList<>(sessionStore.values());
    }

    /**
     * 根据日期组获取会话
     */
    public List<Session> getSessionsByDateGroup(String dateGroup) {
        return dateGroupIndex.getOrDefault(dateGroup, new ArrayList<>());
    }

    /**
     * 根据ID获取会话
     */
    public Optional<Session> getSessionById(String id) {
        return Optional.ofNullable(sessionStore.get(id));
    }

    /**
     * 删除会话
     */
    public boolean deleteSession(String id) {
        Session session = sessionStore.get(id);
        if (session != null) {
            sessionStore.remove(id);
            removeFromDateGroupIndex(session);
            log.info("Deleted session: id={}", id);
            return true;
        }
        return false;
    }

    /**
     * 添加消息到会话
     */
    public void addMessageToSession(String sessionId, Session.Message message) {
        Session session = sessionStore.get(sessionId);
        if (session != null) {
            session.getMessages().add(message);
            session.setUpdatedAt(LocalDateTime.now());
            log.debug("Added message to session: sessionId={}, role={}", sessionId, message.getRole());
        }
    }

    /**
     * 更新会话索引
     */
    private void indexByDateGroup(Session session) {
        String dateGroup = session.getDateGroup();
        if (!dateGroupIndex.containsKey(dateGroup)) {
            dateGroupIndex.put(dateGroup, new ArrayList<>());
        }
        dateGroupIndex.get(dateGroup).add(session);
    }

    /**
     * 从索引中移除会话
     */
    private void removeFromDateGroupIndex(Session session) {
        String dateGroup = session.getDateGroup();
        if (dateGroupIndex.containsKey(dateGroup)) {
            dateGroupIndex.get(dateGroup).remove(session);
            if (dateGroupIndex.get(dateGroup).isEmpty()) {
                dateGroupIndex.remove(dateGroup);
            }
        }
    }
}