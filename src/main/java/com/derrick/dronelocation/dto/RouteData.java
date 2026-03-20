package com.derrick.dronelocation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RouteData {

    private String taskName;
    private String droneSn;
    private BigDecimal flightHeight;
    private BigDecimal flightSpeed;
    private String routeType;
    private List<WaypointPoint> waypoints;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WaypointPoint {
        private Integer sequence;
        private BigDecimal lat;
        private BigDecimal lng;
        private BigDecimal altitude;
        private BigDecimal speed;
    }
}
