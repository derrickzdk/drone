package com.derrick.dronelocation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskDTO {

    private Long id;
    private String taskName;
    private String droneSn;
    private BigDecimal flightHeight;
    private BigDecimal flightSpeed;
    private String routeType;
    private Integer totalWaypoints;
    private String status;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
