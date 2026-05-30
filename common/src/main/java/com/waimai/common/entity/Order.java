package com.waimai.common.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("`order`")
public class Order {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderNo;
    private Long userId;
    private Long merchantId;
    private Long riderId;
    private String status;
    private BigDecimal totalAmount;
    private BigDecimal deliveryFee;
    private BigDecimal discountAmount;
    private BigDecimal payAmount;
    private String address;
    private BigDecimal addressLng;
    private BigDecimal addressLat;
    private String remark;
    private LocalDateTime payTime;
    private LocalDateTime deliverTime;
    private LocalDateTime completeTime;
    private Integer estimatedMinutes;
    private Integer isOvertime;
    private Integer isJointDelivery;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
