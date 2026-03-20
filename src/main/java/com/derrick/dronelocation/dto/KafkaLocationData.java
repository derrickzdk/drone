package com.derrick.dronelocation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KafkaLocationData {

    private String agentId;
    private Integer dataSource;
    private Double direction;
    private Double height;
    private Double lat;
    private Double lon;
    private Double speed;
    private String time;
}
