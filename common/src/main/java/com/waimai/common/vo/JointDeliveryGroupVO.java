package com.waimai.common.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class JointDeliveryGroupVO {
    private Long id;
    private String groupNo;
    private Integer requiredRiderCount;
    private Integer joinedRiderCount;
    private Integer completedRiderCount;
    private String status;
    private BigDecimal deliveryFeeTotal;
    private LocalDateTime createTime;
    private List<JointDeliveryMemberVO> members;
}
