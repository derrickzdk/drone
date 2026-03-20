package com.derrick.dronelocation.controller;

import com.derrick.dronelocation.dto.RouteData;
import com.derrick.dronelocation.service.RouteService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/route")
@Slf4j
public class RouteController {

    private final RouteService routeService;

    public RouteController(RouteService routeService) {
        this.routeService = routeService;
    }

    @PostMapping("/export")
    public ResponseEntity<byte[]> exportRoute(@RequestBody RouteData routeData) {
        try {
            String json = routeService.serializeRouteData(routeData);
            
            String fileName = "route_" + routeData.getTaskName() + "_" + 
                    System.currentTimeMillis() + ".json";
            
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error("导出航线失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/import")
    public ResponseEntity<Map<String, Object>> importRoute(@RequestBody String routeJson) {
        try {
            RouteData routeData = routeService.deserializeRouteData(routeJson);
            return ResponseEntity.ok(buildSuccessResponse("导入航线成功", routeData));
        } catch (Exception e) {
            log.error("导入航线失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(buildErrorResponse("导入航线失败: " + e.getMessage()));
        }
    }

    @PostMapping("/parse-waypoints")
    public ResponseEntity<Map<String, Object>> parseWaypoints(@RequestBody String json) {
        try {
            RouteData.WaypointPoint[] waypoints = routeService.parseWaypointsFromJson(json);
            return ResponseEntity.ok(buildSuccessResponse("解析航点成功", waypoints));
        } catch (Exception e) {
            log.error("解析航点失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(buildErrorResponse("解析航点失败: " + e.getMessage()));
        }
    }

    private Map<String, Object> buildSuccessResponse(String message, Object data) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", message);
        response.put("data", data);
        return response;
    }

    private Map<String, Object> buildErrorResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", message);
        return response;
    }
}
