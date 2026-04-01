package com.derrick.dronelocation.service;

import com.derrick.dronelocation.constants.DataSource;
import com.derrick.dronelocation.constants.DefaultDirection;
import com.derrick.dronelocation.constants.DefaultValues;
import com.derrick.dronelocation.entity.Waypoint;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.springframework.stereotype.Service;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class TaskExecutionScheduler {

    private final TaskExecutionService taskExecutionService;
    private final KafkaProducerService kafkaProducerService;
    private final ScheduledExecutorService scheduler;

    public TaskExecutionScheduler(TaskExecutionService taskExecutionService, KafkaProducerService kafkaProducerService) {
        this.taskExecutionService = taskExecutionService;
        this.kafkaProducerService = kafkaProducerService;
        this.scheduler = Executors.newScheduledThreadPool(5);
    }

    public void startScheduling() {
        scheduler.scheduleAtFixedRate(this::executeTasks, 0, 1, TimeUnit.SECONDS);
        log.info("任务执行调度器已启动");
    }

    private void executeTasks() {
        var executionStates = taskExecutionService.getExecutionStates();
        
        if (executionStates.isEmpty()) {
            return;
        }
        
        long startTime = System.currentTimeMillis();
        log.info("定时任务开始执行，当前活跃任务数: {}, 时间: {}", executionStates.size(), startTime);
        
        executionStates.forEach((taskId, state) -> {
            try {
                long taskStartTime = System.currentTimeMillis();
                Waypoint waypoint = state.getNextWaypoint();
                if (waypoint != null) {
                    kafkaProducerService.sendLocationDataWithNewFormat(
                            state.getTask().getDroneSn(),
                            waypoint.getLatitude().doubleValue(),
                            waypoint.getLongitude().doubleValue(),
                            waypoint.getAltitude() != null ? waypoint.getAltitude().doubleValue() : DefaultValues.DEFAULT_FLIGHT_HEIGHT.doubleValue(),
                            waypoint.getSpeed() != null ? waypoint.getSpeed().doubleValue() : DefaultValues.DEFAULT_FLIGHT_SPEED.doubleValue(),
                            DataSource.TASK_EXECUTION,
                            DefaultDirection.DEFAULT_DIRECTION
                    );
                    
                    long taskEndTime = System.currentTimeMillis();
                    log.info("发送航点数据成功，任务ID: {}, 序号: {}, 循环轮次: {}, 经度: {}, 纬度: {}, 耗时: {}ms", 
                            taskId, waypoint.getSequenceNum(), state.getLoopCount(),
                            waypoint.getLongitude(), waypoint.getLatitude(), taskEndTime - taskStartTime);
                }
            } catch (Exception e) {
                log.error("执行任务失败，任务ID: {}", taskId, e);
                taskExecutionService.stopTaskExecution(taskId);
            }
        });
        
        long endTime = System.currentTimeMillis();
        log.info("定时任务执行完成，总耗时: {}ms", endTime - startTime);
    }

    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
