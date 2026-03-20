package com.derrick.dronelocation.util;

import com.derrick.dronelocation.dto.RouteData;
import com.derrick.dronelocation.dto.RouteGenerationRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class RouteGenerator {

    private static final double EARTH_RADIUS = 6371000.0;

    public RouteData generateRoute(RouteGenerationRequest request) {
        RouteData routeData = RouteData.builder()
                .taskName("Auto Generated Route")
                .droneSn(request.getDroneSn())
                .flightHeight(request.getFlightHeight())
                .flightSpeed(request.getFlightSpeed())
                .routeType("AUTO")
                .waypoints(new ArrayList<>())
                .build();

        if (request.getBounds() == null || request.getBounds().size() < 2) {
            log.warn("边界点不足，无法生成航线");
            return routeData;
        }

        List<RouteData.WaypointPoint> waypoints;
        if ("GRID".equalsIgnoreCase(request.getPatternType())) {
            waypoints = generateGridRoute(request);
        } else if ("SPIRAL".equalsIgnoreCase(request.getPatternType())) {
            waypoints = generateSpiralRoute(request);
        } else {
            waypoints = generateGridRoute(request);
        }

        routeData.setWaypoints(waypoints);
        return routeData;
    }

    private List<RouteData.WaypointPoint> generateGridRoute(RouteGenerationRequest request) {
        List<RouteData.WaypointPoint> waypoints = new ArrayList<>();
        List<RouteGenerationRequest.BoundPoint> bounds = request.getBounds();

        if (bounds.size() < 2) {
            return waypoints;
        }

        double minLat = bounds.get(0).getLat().doubleValue();
        double maxLat = bounds.get(0).getLat().doubleValue();
        double minLng = bounds.get(0).getLng().doubleValue();
        double maxLng = bounds.get(0).getLng().doubleValue();

        for (RouteGenerationRequest.BoundPoint point : bounds) {
            minLat = Math.min(minLat, point.getLat().doubleValue());
            maxLat = Math.max(maxLat, point.getLat().doubleValue());
            minLng = Math.min(minLng, point.getLng().doubleValue());
            maxLng = Math.max(maxLng, point.getLng().doubleValue());
        }

        double spacing = request.getWaypointSpacing() != null 
                ? request.getWaypointSpacing().doubleValue() 
                : 50.0;

        double latStep = spacing / EARTH_RADIUS * (180.0 / Math.PI);
        double lngStep = spacing / (EARTH_RADIUS * Math.cos(Math.toRadians((minLat + maxLat) / 2))) * (180.0 / Math.PI);

        int sequence = 0;
        boolean leftToRight = true;

        for (double lat = minLat; lat <= maxLat; lat += latStep) {
            if (leftToRight) {
                for (double lng = minLng; lng <= maxLng; lng += lngStep) {
                    waypoints.add(createWaypoint(++sequence, lat, lng, request));
                }
            } else {
                for (double lng = maxLng; lng >= minLng; lng -= lngStep) {
                    waypoints.add(createWaypoint(++sequence, lat, lng, request));
                }
            }
            leftToRight = !leftToRight;
        }

        log.info("生成蛇形航线，共{}个航点", waypoints.size());
        return waypoints;
    }

    private List<RouteData.WaypointPoint> generateSpiralRoute(RouteGenerationRequest request) {
        List<RouteData.WaypointPoint> waypoints = new ArrayList<>();
        List<RouteGenerationRequest.BoundPoint> bounds = request.getBounds();

        if (bounds.size() < 2) {
            return waypoints;
        }

        double minLat = bounds.get(0).getLat().doubleValue();
        double maxLat = bounds.get(0).getLat().doubleValue();
        double minLng = bounds.get(0).getLng().doubleValue();
        double maxLng = bounds.get(0).getLng().doubleValue();

        for (RouteGenerationRequest.BoundPoint point : bounds) {
            minLat = Math.min(minLat, point.getLat().doubleValue());
            maxLat = Math.max(maxLat, point.getLat().doubleValue());
            minLng = Math.min(minLng, point.getLng().doubleValue());
            maxLng = Math.max(maxLng, point.getLng().doubleValue());
        }

        double centerLat = (minLat + maxLat) / 2;
        double centerLng = (minLng + maxLng) / 2;
        double maxRadius = Math.min(maxLat - minLat, maxLng - minLng) / 2;

        double spacing = request.getWaypointSpacing() != null 
                ? request.getWaypointSpacing().doubleValue() 
                : 50.0;

        double latStep = spacing / EARTH_RADIUS * (180.0 / Math.PI);
        double lngStep = spacing / (EARTH_RADIUS * Math.cos(Math.toRadians(centerLat))) * (180.0 / Math.PI);

        int sequence = 0;
        double radius = 0;
        double angle = 0;
        double angleStep = 0.5;

        while (radius <= maxRadius) {
            double lat = centerLat + radius * Math.cos(angle) * (180.0 / Math.PI) / EARTH_RADIUS;
            double lng = centerLng + radius * Math.sin(angle) * (180.0 / Math.PI) / (EARTH_RADIUS * Math.cos(Math.toRadians(centerLat)));

            if (lat >= minLat && lat <= maxLat && lng >= minLng && lng <= maxLng) {
                waypoints.add(createWaypoint(++sequence, lat, lng, request));
            }

            angle += angleStep;
            radius += latStep * angleStep / (2 * Math.PI);

            if (angle >= 2 * Math.PI) {
                angle = 0;
            }
        }

        log.info("生成螺旋形航线，共{}个航点", waypoints.size());
        return waypoints;
    }

    private RouteData.WaypointPoint createWaypoint(int sequence, double lat, double lng, RouteGenerationRequest request) {
        return RouteData.WaypointPoint.builder()
                .sequence(sequence)
                .lat(BigDecimal.valueOf(lat).setScale(8, RoundingMode.HALF_UP))
                .lng(BigDecimal.valueOf(lng).setScale(8, RoundingMode.HALF_UP))
                .altitude(request.getFlightHeight())
                .speed(request.getFlightSpeed())
                .build();
    }
}
