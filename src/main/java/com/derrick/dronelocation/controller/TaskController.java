package com.derrick.dronelocation.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.derrick.dronelocation.constants.DefaultValues;
import com.derrick.dronelocation.constants.RouteType;
import com.derrick.dronelocation.dto.RouteData;
import com.derrick.dronelocation.dto.TaskDTO;
import com.derrick.dronelocation.entity.Task;
import com.derrick.dronelocation.service.TaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/task")
@Slf4j
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping("/create")
    public ResponseEntity<Map<String, Object>> createTask(@RequestBody Map<String, Object> request) {
        return saveTask(request);
    }

    @PostMapping("/save")
    public ResponseEntity<Map<String, Object>> saveTask(@RequestBody Map<String, Object> request) {
        try {
            TaskDTO taskDTO = buildTaskDTO(request);
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> waypointsList = (List<Map<String, Object>>) request.get("waypoints");
            
            RouteData.WaypointPoint[] waypoints = buildWaypoints(waypointsList, taskDTO);
            
            RouteData routeData = RouteData.builder()
                    .taskName(taskDTO.getTaskName())
                    .droneSn(taskDTO.getDroneSn())
                    .flightHeight(taskDTO.getFlightHeight())
                    .flightSpeed(taskDTO.getFlightSpeed())
                    .routeType(request.get("routeType") != null ? (String) request.get("routeType") : RouteType.MANUAL)
                    .waypoints(java.util.Arrays.asList(waypoints))
                    .build();
            
            Task task = taskService.createTask(taskDTO, routeData);
            
            return ResponseEntity.ok(buildSuccessResponse("创建任务成功", task));
        } catch (Exception e) {
            log.error("创建任务失败", e);
            return ResponseEntity.status(500).body(buildErrorResponse("创建任务失败: " + e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getTask(@PathVariable Long id) {
        try {
            TaskDTO taskDTO = taskService.getTaskDTOById(id);
            return ResponseEntity.ok(buildSuccessResponse(null, taskDTO));
        } catch (Exception e) {
            log.error("获取任务失败", e);
            return ResponseEntity.status(404).body(buildErrorResponse("获取任务失败: " + e.getMessage()));
        }
    }

    @GetMapping("/list")
    public ResponseEntity<Map<String, Object>> getAllTasks() {
        try {
            List<TaskDTO> tasks = taskService.getAllTasks();
            return ResponseEntity.ok(buildSuccessResponse(null, tasks));
        } catch (Exception e) {
            log.error("获取任务列表失败", e);
            return ResponseEntity.status(500).body(buildErrorResponse("获取任务列表失败: " + e.getMessage()));
        }
    }

    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> searchTasks(
            @RequestParam(required = false) String taskName,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdTime") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        try {
            IPage<TaskDTO> taskPage = taskService.searchTasks(taskName, startTime, endTime, page, size, sortBy, sortDir);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", taskPage.getRecords());
            response.put("totalElements", taskPage.getTotal());
            response.put("totalPages", taskPage.getPages());
            response.put("currentPage", taskPage.getCurrent());
            response.put("pageSize", taskPage.getSize());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("搜索任务失败", e);
            return ResponseEntity.status(500).body(buildErrorResponse("搜索任务失败: " + e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteTask(@PathVariable Long id) {
        try {
            taskService.deleteTask(id);
            return ResponseEntity.ok(buildSuccessResponse("删除任务成功", null));
        } catch (Exception e) {
            log.error("删除任务失败", e);
            return ResponseEntity.status(500).body(buildErrorResponse("删除任务失败: " + e.getMessage()));
        }
    }

    private TaskDTO buildTaskDTO(Map<String, Object> request) {
        return TaskDTO.builder()
                .taskName((String) request.get("taskName"))
                .droneSn((String) request.get("droneSn"))
                .flightHeight(extractBigDecimalWithAlternative(request, "flightHeight", "altitude", DefaultValues.DEFAULT_FLIGHT_HEIGHT))
                .flightSpeed(extractBigDecimalWithAlternative(request, "flightSpeed", "speed", DefaultValues.DEFAULT_FLIGHT_SPEED))
                .build();
    }

    private RouteData.WaypointPoint[] buildWaypoints(List<Map<String, Object>> waypointsList, TaskDTO taskDTO) {
        if (waypointsList == null || waypointsList.isEmpty()) {
            return new RouteData.WaypointPoint[0];
        }
        
        return waypointsList.stream()
                .map(wp -> RouteData.WaypointPoint.builder()
                        .sequence((Integer) wp.get("sequence"))
                        .lat(new BigDecimal(wp.get("lat").toString()))
                        .lng(new BigDecimal(wp.get("lng").toString()))
                        .altitude(wp.get("altitude") != null 
                                ? new BigDecimal(wp.get("altitude").toString()) 
                                : taskDTO.getFlightHeight())
                        .speed(wp.get("speed") != null 
                                ? new BigDecimal(wp.get("speed").toString()) 
                                : taskDTO.getFlightSpeed())
                        .build())
                .toArray(RouteData.WaypointPoint[]::new);
    }

    private BigDecimal extractBigDecimalWithAlternative(Map<String, Object> request, String primaryField, String alternativeField, BigDecimal defaultValue) {
        Object value = request.get(primaryField);
        if (value == null) {
            value = request.get(alternativeField);
        }
        if (value == null) {
            return defaultValue;
        }
        return new BigDecimal(value.toString());
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
