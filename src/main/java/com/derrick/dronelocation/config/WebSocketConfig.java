package com.derrick.dronelocation.config;

import com.derrick.dronelocation.websocket.TaskExecutionWebSocketHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final TaskExecutionWebSocketHandler taskExecutionWebSocketHandler;

    public WebSocketConfig(TaskExecutionWebSocketHandler taskExecutionWebSocketHandler) {
        this.taskExecutionWebSocketHandler = taskExecutionWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(taskExecutionWebSocketHandler, "/ws/task-execution/{taskId}")
                .setAllowedOrigins("*");
    }
}
