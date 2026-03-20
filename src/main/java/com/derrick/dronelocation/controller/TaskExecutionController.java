package com.derrick.dronelocation.controller;

import com.derrick.dronelocation.service.TaskExecutionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/task-execution")
@Slf4j
public class TaskExecutionController {

    private final TaskExecutionService taskExecutionService;

    public TaskExecutionController(TaskExecutionService taskExecutionService) {
        this.taskExecutionService = taskExecutionService;
    }

    @PostMapping("/start/{taskId}")
    public ResponseEntity<Map<String, Object>> startExecution(@PathVariable Long taskId) {
        try {
            taskExecutionService.startTaskExecution(taskId);
            return ResponseEntity.ok(buildSuccessResponse("开始执行任务", null));
        } catch (Exception e) {
            log.error("开始执行任务失败", e);
            return ResponseEntity.status(500).body(buildErrorResponse("开始执行任务失败: " + e.getMessage()));
        }
    }

    @PostMapping("/stop/{taskId}")
    public ResponseEntity<Map<String, Object>> stopExecution(@PathVariable Long taskId) {
        try {
            taskExecutionService.stopTaskExecution(taskId);
            return ResponseEntity.ok(buildSuccessResponse("停止执行任务", null));
        } catch (Exception e) {
            log.error("停止执行任务失败", e);
            return ResponseEntity.status(500).body(buildErrorResponse("停止执行任务失败: " + e.getMessage()));
        }
    }

    @GetMapping("/status/{taskId}")
    public ResponseEntity<Map<String, Object>> getExecutionStatus(@PathVariable Long taskId) {
        try {
            boolean isExecuting = taskExecutionService.isTaskExecuting(taskId);
            TaskExecutionService.TaskExecutionState state = taskExecutionService.getExecutionState(taskId);

            Map<String, Object> dataMap = new HashMap<>();
            dataMap.put("isExecuting", isExecuting);
            dataMap.put("currentIndex", state != null ? state.getCurrentIndex() : 0);
            dataMap.put("totalWaypoints", state != null ? state.getTotalWaypoints() : 0);
            dataMap.put("progress", calculateProgress(state));

            return ResponseEntity.ok(buildSuccessResponse(null, dataMap));
        } catch (Exception e) {
            log.error("获取执行状态失败", e);
            return ResponseEntity.status(500).body(buildErrorResponse("获取执行状态失败: " + e.getMessage()));
        }
    }

    private double calculateProgress(TaskExecutionService.TaskExecutionState state) {
        if (state == null || state.getTotalWaypoints() == 0) {
            return 0.0;
        }
        return (double) state.getCurrentIndex() / state.getTotalWaypoints() * 100;
    }

    private Map<String, Object> buildSuccessResponse(String message, Object data) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        if (message != null) {
            response.put("message", message);
        }
        if (data != null) {
            response.put("data", data);
        }
        return response;
    }

    private Map<String, Object> buildErrorResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", message);
        return response;
    }
}
