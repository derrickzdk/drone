package com.derrick.dronelocation.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HeartbeatConfig {

    @Value("${heartbeat.interval:30000}")
    private long heartbeatInterval;

    @Value("${heartbeat.timeout:60000}")
    private long heartbeatTimeout;

    @Value("${heartbeat.check-interval:10000}")
    private long checkInterval;

    @Value("${heartbeat.reconnect-wait-timeout:120000}")
    private long reconnectWaitTimeout;

    public long getHeartbeatInterval() {
        return heartbeatInterval;
    }

    public long getHeartbeatTimeout() {
        return heartbeatTimeout;
    }

    public long getCheckInterval() {
        return checkInterval;
    }

    public long getReconnectWaitTimeout() {
        return reconnectWaitTimeout;
    }
}
