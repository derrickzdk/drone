package com.derrick.dronelocation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KafkaLocationMessage {

    private KafkaLocationData data;
    private String keyId;
}
