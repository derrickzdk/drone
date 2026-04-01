package com.derrick.dronelocation.websocket;

import com.derrick.dronelocation.config.HeartbeatConfig;
import com.derrick.dronelocation.service.HeartbeatManager;
import com.derrick.dronelocation.service.TaskExecutionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class TaskExecutionWebSocketHandler extends TextWebSocketHandler {

    private final HeartbeatManager heartbeatManager;
    private final TaskExecutionService taskExecutionService;
    private final HeartbeatConfig heartbeatConfig;
    private final ObjectMapper objectMapper;

    private final ScheduledExecutorService statusUpdateScheduler = Executors.newScheduledThreadPool(1);

    public TaskExecutionWebSocketHandler(HeartbeatManager heartbeatManager,
                                        TaskExecutionService taskExecutionService,
                                        HeartbeatConfig heartbeatConfig) {
        this.heartbeatManager = heartbeatManager;
        this.taskExecutionService = taskExecutionService;
        this.heartbeatConfig = heartbeatConfig;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String uri = session.getUri().toString();
        Long taskId = extractTaskId(uri);

        if (taskId == null) {
            log.error("无法从 URI 中提取任务ID: {}", uri);
            session.close();
            return;
        }

        if (!taskExecutionService.isTaskExecuting(taskId)) {
            log.warn("任务未在执行中，拒绝连接，任务ID: {}", taskId);
            sendErrorMessage(session, "任务未在执行中");
            session.close();
            return;
        }

        if (heartbeatManager.hasActiveSession(taskId)) {
            log.warn("任务已有活跃连接，拒绝新连接，任务ID: {}", taskId);
            sendErrorMessage(session, "任务已有活跃连接");
            session.close();
            return;
        }

        heartbeatManager.registerSession(taskId, session);
        startStatusUpdate(taskId, session);
        log.info("WebSocket 连接建立，任务ID: {}, 会话ID: {}", taskId, session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        Long taskId = extractTaskId(session.getUri().toString());

        try {
            Map<String, Object> messageMap = objectMapper.readValue(payload, Map.class);
            String type = (String) messageMap.get("type");

            if ("HEARTBEAT".equals(type)) {
                handleHeartbeat(taskId, session);
            } else {
                log.warn("未知消息类型: {}, 任务ID: {}", type, taskId);
            }
        } catch (Exception e) {
            log.error("处理消息失败，任务ID: {}, 消息: {}", taskId, payload, e);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String uri = session.getUri().toString();
        Long taskId = extractTaskId(uri);

        if (taskId != null) {
            heartbeatManager.removeSession(taskId);
            
            if (taskExecutionService.isTaskExecuting(taskId)) {
                log.warn("WebSocket 连接意外关闭，立即停止任务，任务ID: {}, 会话ID: {}, 状态: {}", 
                        taskId, session.getId(), status);
                taskExecutionService.stopTaskExecutionByTimeout(taskId);
            } else {
                log.info("WebSocket 连接正常关闭，任务ID: {}, 会话ID: {}, 状态: {}", 
                        taskId, session.getId(), status);
            }
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        String uri = session.getUri().toString();
        Long taskId = extractTaskId(uri);

        log.error("WebSocket 传输错误，任务ID: {}, 会话ID: {}", taskId, session.getId(), exception);

        if (taskId != null) {
            heartbeatManager.removeSession(taskId);
            
            if (taskExecutionService.isTaskExecuting(taskId)) {
                log.warn("WebSocket 传输错误，立即停止任务，任务ID: {}", taskId);
                taskExecutionService.stopTaskExecutionByTimeout(taskId);
            }
        }
    }

    private void handleHeartbeat(Long taskId, WebSocketSession session) {
        heartbeatManager.updateHeartbeat(taskId);

        Map<String, Object> response = new HashMap<>();
        response.put("type", "HEARTBEAT_ACK");
        response.put("timestamp", System.currentTimeMillis());

        sendMessage(session, response);
        log.debug("处理心跳，任务ID: {}", taskId);
    }

    private void startStatusUpdate(Long taskId, WebSocketSession session) {
        statusUpdateScheduler.scheduleAtFixedRate(() -> {
            if (!session.isOpen()) {
                return;
            }

            try {
                TaskExecutionService.TaskExecutionState state = taskExecutionService.getExecutionState(taskId);
                if (state != null) {
                    Map<String, Object> statusUpdate = new HashMap<>();
                    statusUpdate.put("type", "STATUS_UPDATE");

                    Map<String, Object> data = new HashMap<>();
                    data.put("currentIndex", state.getCurrentIndex());
                    data.put("totalWaypoints", state.getTotalWaypoints());
                    data.put("progress", calculateProgress(state));
                    data.put("loopCount", state.getLoopCount());

                    statusUpdate.put("data", data);
                    sendMessage(session, statusUpdate);
                }
            } catch (Exception e) {
                log.error("发送状态更新失败，任务ID: {}", taskId, e);
            }
        }, 0, 1, TimeUnit.SECONDS);
    }

    private double calculateProgress(TaskExecutionService.TaskExecutionState state) {
        if (state.getTotalWaypoints() == 0) {
            return 0.0;
        }
        return (double) state.getCurrentIndex() / state.getTotalWaypoints() * 100;
    }

    private void sendMessage(WebSocketSession session, Map<String, Object> message) {
        try {
            if (session.isOpen()) {
                String json = objectMapper.writeValueAsString(message);
                session.sendMessage(new TextMessage(json));
            }
        } catch (Exception e) {
            log.error("发送消息失败，会话ID: {}", session.getId(), e);
        }
    }

    private void sendErrorMessage(WebSocketSession session, String errorMessage) {
        Map<String, Object> error = new HashMap<>();
        error.put("type", "ERROR");
        error.put("message", errorMessage);
        sendMessage(session, error);
    }

    private Long extractTaskId(String uri) {
        try {
            String[] parts = uri.split("/");
            String taskIdStr = parts[parts.length - 1];
            return Long.parseLong(taskIdStr);
        } catch (Exception e) {
            log.error("提取任务ID失败，URI: {}", uri, e);
            return null;
        }
    }

    public void shutdown() {
        statusUpdateScheduler.shutdown();
        try {
            if (!statusUpdateScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                statusUpdateScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            statusUpdateScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
