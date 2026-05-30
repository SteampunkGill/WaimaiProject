package com.waimai.common.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class JointDeliveryMemberVO {
    private Long id;
    private Long riderId;
    private String riderName;
    private String riderPhone;
    private String status;
    private BigDecimal earnings;
    private LocalDateTime joinTime;
    private LocalDateTime pickupTime;
    private LocalDateTime completeTime;
}
