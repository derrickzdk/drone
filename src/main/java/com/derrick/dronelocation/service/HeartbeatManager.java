package com.derrick.dronelocation.service;

import com.derrick.dronelocation.config.HeartbeatConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class HeartbeatManager {

    private final HeartbeatConfig heartbeatConfig;
    private final TaskExecutionService taskExecutionService;

    private final Map<Long, SessionInfo> activeSessions = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public HeartbeatManager(HeartbeatConfig heartbeatConfig, TaskExecutionService taskExecutionService) {
        this.heartbeatConfig = heartbeatConfig;
        this.taskExecutionService = taskExecutionService;
        startTimeoutCheck();
    }

    public void registerSession(Long taskId, WebSocketSession session) {
        SessionInfo sessionInfo = new SessionInfo(session, System.currentTimeMillis());
        activeSessions.put(taskId, sessionInfo);
        log.info("注册 WebSocket 会话，任务ID: {}, 会话ID: {}", taskId, session.getId());
    }

    public void updateHeartbeat(Long taskId) {
        SessionInfo sessionInfo = activeSessions.get(taskId);
        if (sessionInfo != null) {
            sessionInfo.setLastHeartbeatTime(System.currentTimeMillis());
            log.debug("更新心跳时间，任务ID: {}", taskId);
        }
    }

    public void removeSession(Long taskId) {
        SessionInfo removed = activeSessions.remove(taskId);
        if (removed != null) {
            log.info("移除 WebSocket 会话，任务ID: {}", taskId);
        }
    }

    public boolean hasActiveSession(Long taskId) {
        return activeSessions.containsKey(taskId);
    }

    private void startTimeoutCheck() {
        scheduler.scheduleAtFixedRate(this::checkTimeouts, 
                heartbeatConfig.getCheckInterval(), 
                heartbeatConfig.getCheckInterval(), 
                TimeUnit.MILLISECONDS);
        log.info("启动心跳超时检查，检查间隔: {}ms", heartbeatConfig.getCheckInterval());
    }

    private void checkTimeouts() {
        long currentTime = System.currentTimeMillis();
        long timeout = heartbeatConfig.getHeartbeatTimeout();

        activeSessions.entrySet().removeIf(entry -> {
            Long taskId = entry.getKey();
            SessionInfo sessionInfo = entry.getValue();
            long elapsed = currentTime - sessionInfo.getLastHeartbeatTime();

            if (elapsed > timeout) {
                log.warn("检测到心跳超时，任务ID: {}, 超时时间: {}ms", taskId, elapsed);
                try {
                    sessionInfo.getSession().close();
                    taskExecutionService.stopTaskExecutionByTimeout(taskId);
                } catch (Exception e) {
                    log.error("处理超时会话失败，任务ID: {}", taskId, e);
                }
                return true;
            }
            return false;
        });
    }

    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private static class SessionInfo {
        private final WebSocketSession session;
        private volatile long lastHeartbeatTime;

        public SessionInfo(WebSocketSession session, long lastHeartbeatTime) {
            this.session = session;
            this.lastHeartbeatTime = lastHeartbeatTime;
        }

        public WebSocketSession getSession() {
            return session;
        }

        public long getLastHeartbeatTime() {
            return lastHeartbeatTime;
        }

        public void setLastHeartbeatTime(long lastHeartbeatTime) {
            this.lastHeartbeatTime = lastHeartbeatTime;
        }
    }
}
