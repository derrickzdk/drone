package com.derrick.dronelocation.controller;

import com.derrick.dronelocation.dto.LocationData;
import com.derrick.dronelocation.service.KafkaProducerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/location")
@Slf4j
public class LocationController {

    private final KafkaProducerService kafkaProducerService;

    public LocationController(KafkaProducerService kafkaProducerService) {
        this.kafkaProducerService = kafkaProducerService;
    }

    @PostMapping("/send")
    public ResponseEntity<Map<String, Object>> sendLocation(@RequestBody LocationData locationData) {
        try {
            if (locationData.getTimestamp() == null) {
                locationData.setTimestamp(LocalDateTime.now());
            }
            
            kafkaProducerService.sendLocationData(locationData);
            
            return ResponseEntity.ok(buildSuccessResponse("位置数据发送成功", locationData));
        } catch (Exception e) {
            log.error("发送位置数据失败", e);
            return ResponseEntity.status(500).body(buildErrorResponse("发送位置数据失败: " + e.getMessage()));
        }
    }

    @PostMapping("/send-simple")
    public ResponseEntity<Map<String, Object>> sendSimpleLocation(
            @RequestParam String sn,
            @RequestParam Double lat,
            @RequestParam Double lng,
            @RequestParam(required = false, defaultValue = "100.0") Double altitude,
            @RequestParam(required = false, defaultValue = "10.0") Double speed,
            @RequestParam(required = false, defaultValue = "8") Integer dataSource, // todo 用的时候要改
            @RequestParam(required = false) Double direction) {
        try {
            kafkaProducerService.sendLocationDataWithNewFormat(
                    sn, lat, lng, altitude, speed, dataSource, direction);
            
            Map<String, Object> dataMap = new HashMap<>();
            dataMap.put("sn", sn);
            dataMap.put("lat", lat);
            dataMap.put("lng", lng);
            dataMap.put("altitude", altitude);
            dataMap.put("speed", speed);
            dataMap.put("timestamp", LocalDateTime.now());
            
            return ResponseEntity.ok(buildSuccessResponse("位置数据发送成功", dataMap));
        } catch (Exception e) {
            log.error("发送位置数据失败", e);
            return ResponseEntity.status(500).body(buildErrorResponse("发送位置数据失败: " + e.getMessage()));
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
