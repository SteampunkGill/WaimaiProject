package com.waimai.common.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class OrderVO {
    private Long id;
    private String orderNo;
    private String status;
    private BigDecimal totalAmount;
    private BigDecimal deliveryFee;
    private BigDecimal discountAmount;
    private BigDecimal payAmount;
    private String address;
    private String remark;
    private Long merchantId;
    private Long riderId;
    private String merchantName;
    private String riderName;
    private String riderPhone;
    private BigDecimal addressLng;
    private BigDecimal addressLat;
    private Integer estimatedMinutes;
    private LocalDateTime payTime;
    private LocalDateTime createTime;
    private Integer isJointDelivery;
    private JointDeliveryGroupVO jointDelivery;
    private List<OrderDetailVO> details;
}
