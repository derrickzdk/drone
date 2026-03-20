package com.derrick.dronelocation.controller;

import com.derrick.dronelocation.dto.RouteData;
import com.derrick.dronelocation.dto.RouteGenerationRequest;
import com.derrick.dronelocation.util.RouteGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auto-route")
@Slf4j
public class AutoRouteController {

    private final RouteGenerator routeGenerator;

    public AutoRouteController(RouteGenerator routeGenerator) {
        this.routeGenerator = routeGenerator;
    }

    @PostMapping("/generate")
    public ResponseEntity<Map<String, Object>> generateRoute(@RequestBody RouteGenerationRequest request) {
        try {
            RouteData routeData = routeGenerator.generateRoute(request);
            return ResponseEntity.ok(buildSuccessResponse("生成航线成功", routeData));
        } catch (Exception e) {
            log.error("生成航线失败", e);
            return ResponseEntity.status(500).body(buildErrorResponse("生成航线失败: " + e.getMessage()));
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
