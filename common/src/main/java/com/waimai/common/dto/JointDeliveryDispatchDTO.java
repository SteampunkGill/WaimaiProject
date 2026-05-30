package com.waimai.common.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class JointDeliveryDispatchDTO {
    private Long groupId;
    private String groupNo;
    private Long orderId;
    private String orderNo;
    private String merchantName;
    private String address;
    private BigDecimal addressLng;
    private BigDecimal addressLat;
    private int requiredRiderCount;
    private int joinedRiderCount;
    private BigDecimal splitEarnings;
}
