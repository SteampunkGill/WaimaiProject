package com.waimai.common.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("joint_delivery_group")
public class JointDeliveryGroup {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private String groupNo;
    private Integer requiredRiderCount;
    private Integer joinedRiderCount;
    private Integer completedRiderCount;
    private String status;
    private BigDecimal deliveryFeeTotal;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
