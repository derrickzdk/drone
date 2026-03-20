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
public class LocationData {

    /**
     {"data":{"agentId":"ZP260131064", ——sn码
     "dataSource":8, ——数据来源
     "direction":268.81365015472295, ——航向
     "height":60.0, ——高度
     "lat":23.114665714285714, ——纬度
     "lon":113.31688, ——经度
     "speed":20.0, ——速度
     "time":"1773992692464" ——时间戳
     },
     "keyId":"ZP260131064" ——唯一标识sn码
     }
     */

    private String sn;
    private BigDecimal lat;
    private BigDecimal lng;
    private BigDecimal altitude;
    private BigDecimal speed;
    private LocalDateTime timestamp;
}
