package com.derrick.dronelocation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.derrick.dronelocation.constants.DeletedStatus;
import com.derrick.dronelocation.constants.RouteType;
import com.derrick.dronelocation.dto.RouteData;
import com.derrick.dronelocation.entity.Waypoint;
import com.derrick.dronelocation.mapper.WaypointMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class RouteService extends ServiceImpl<WaypointMapper, Waypoint> {

    private final ObjectMapper objectMapper;

    public RouteService() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    @Transactional
    public void saveWaypoints(Long taskId, List<RouteData.WaypointPoint> waypointPoints) {
        LambdaQueryWrapper<Waypoint> deleteWrapper = new LambdaQueryWrapper<>();
        deleteWrapper.eq(Waypoint::getTaskId, taskId);
        this.remove(deleteWrapper);

        List<Waypoint> waypoints = waypointPoints.stream()
                .map(point -> Waypoint.builder()
                        .taskId(taskId)
                        .sequenceNum(point.getSequence() != null ? point.getSequence() : (waypointPoints.indexOf(point) + 1))
                        .latitude(point.getLat())
                        .longitude(point.getLng())
                        .altitude(point.getAltitude())
                        .speed(point.getSpeed())
                        .deleted(DeletedStatus.NOT_DELETED)
                        .build())
                .collect(Collectors.toList());

        this.saveBatch(waypoints);

        log.info("保存{}个航点到任务{}", waypoints.size(), taskId);
    }

    public List<Waypoint> getWaypointsByTaskId(Long taskId) {
        LambdaQueryWrapper<Waypoint> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Waypoint::getTaskId, taskId);
        queryWrapper.eq(Waypoint::getDeleted, DeletedStatus.NOT_DELETED);
        queryWrapper.orderByAsc(Waypoint::getSequenceNum);
        return this.list(queryWrapper);
    }

    public String serializeRouteData(RouteData routeData) {
        try {
            return objectMapper.writeValueAsString(routeData);
        } catch (JsonProcessingException e) {
            log.error("序列化航线数据失败", e);
            throw new RuntimeException("序列化航线数据失败", e);
        }
    }

    public RouteData deserializeRouteData(String routeDataJson) {
        try {
            return objectMapper.readValue(routeDataJson, RouteData.class);
        } catch (JsonProcessingException e) {
            log.error("反序列化航线数据失败", e);
            throw new RuntimeException("反序列化航线数据失败", e);
        }
    }

    public RouteData.WaypointPoint[] parseWaypointsFromJson(String json) {
        try {
            return objectMapper.readValue(json, RouteData.WaypointPoint[].class);
        } catch (JsonProcessingException e) {
            log.error("解析航点JSON失败", e);
            throw new RuntimeException("解析航点JSON失败", e);
        }
    }

    public RouteData createRouteDataFromWaypoints(List<Waypoint> waypoints, String taskName, String droneSn,
                                                java.math.BigDecimal flightHeight, java.math.BigDecimal flightSpeed) {
        List<RouteData.WaypointPoint> waypointPoints = waypoints.stream()
                .map(wp -> RouteData.WaypointPoint.builder()
                        .sequence(wp.getSequenceNum())
                        .lat(wp.getLatitude())
                        .lng(wp.getLongitude())
                        .altitude(wp.getAltitude())
                        .speed(wp.getSpeed())
                        .build())
                .collect(Collectors.toList());

        return RouteData.builder()
                .taskName(taskName)
                .droneSn(droneSn)
                .flightHeight(flightHeight)
                .flightSpeed(flightSpeed)
                .routeType(RouteType.MANUAL)
                .waypoints(waypointPoints)
                .build();
    }
}
