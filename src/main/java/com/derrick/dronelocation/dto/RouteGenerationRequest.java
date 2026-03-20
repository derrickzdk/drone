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
public class RouteGenerationRequest {

    private String droneSn;
    private BigDecimal flightHeight;
    private BigDecimal flightSpeed;
    private String routeType;
    private BigDecimal waypointSpacing;
    private String patternType;
    private List<BoundPoint> bounds;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BoundPoint {
        private BigDecimal lat;
        private BigDecimal lng;
    }
}
