package com.derrick.dronelocation.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.derrick.dronelocation.constants.DataSource;
import com.derrick.dronelocation.constants.DefaultDirection;
import com.derrick.dronelocation.dto.KafkaLocationData;
import com.derrick.dronelocation.dto.KafkaLocationMessage;
import com.derrick.dronelocation.dto.LocationData;
import com.derrick.dronelocation.util.GeoUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class KafkaProducerService {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Value("${kafka.topic.drone-location}")
    private String topic;

    private final ObjectMapper objectMapper;

    public KafkaProducerService() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    public void sendLocationData(LocationData locationData) {
        try {
            // locationData 的经纬度从高德地图的 GCJ-02 坐标系改为标准的 WSG84 坐标系
            double[] wgs84Coords = GeoUtils.gcj02ToWgs84(
                    locationData.getLat().doubleValue(),
                    locationData.getLng().doubleValue()
            );
            locationData.setLat(java.math.BigDecimal.valueOf(wgs84Coords[0]));
            locationData.setLng(java.math.BigDecimal.valueOf(wgs84Coords[1]));

            String jsonMessage = objectMapper.writeValueAsString(locationData);
            kafkaTemplate.send(topic, jsonMessage);
            log.info("发送位置数据到 Kafka 成功：{}", jsonMessage);
        } catch (JsonProcessingException e) {
            log.error("序列化位置数据失败", e);
            throw new RuntimeException("序列化位置数据失败", e);
        }
    }

    @Deprecated
    public void sendLocationData(String sn, double lat, double lng, double altitude, double speed) {
        LocationData locationData = LocationData.builder()
                .sn(sn)
                .lat(java.math.BigDecimal.valueOf(lat))
                .lng(java.math.BigDecimal.valueOf(lng))
                .altitude(java.math.BigDecimal.valueOf(altitude))
                .speed(java.math.BigDecimal.valueOf(speed))
                .timestamp(java.time.LocalDateTime.now())
                .build();
        sendLocationData(locationData);
    }

    public void sendKafkaLocationMessage(KafkaLocationMessage message) {
        try {
            String jsonMessage = objectMapper.writeValueAsString(message);
            kafkaTemplate.send(topic, jsonMessage);
            log.info("发送位置数据到Kafka成功: {}", jsonMessage);
        } catch (JsonProcessingException e) {
            log.error("序列化位置数据失败", e);
            throw new RuntimeException("序列化位置数据失败", e);
        }
    }

    public void sendLocationDataWithNewFormat(
            String sn,
            double lat,
            double lng,
            double altitude,
            double speed,
            Integer dataSource,
            Double direction) {

        KafkaLocationData data = KafkaLocationData.builder()
                .agentId(sn)
                .dataSource(dataSource != null ? dataSource : DataSource.MANUAL_INPUT)
                .direction(direction != null ? direction : DefaultDirection.DEFAULT_DIRECTION)
                .height(altitude)
                .lat(lat)
                .lon(lng)
                .speed(speed)
                .time(String.valueOf(System.currentTimeMillis()))
                .build();

        KafkaLocationMessage message = KafkaLocationMessage.builder()
                .data(data)
                .keyId(sn)
                .build();

        sendKafkaLocationMessage(message);
    }
}
