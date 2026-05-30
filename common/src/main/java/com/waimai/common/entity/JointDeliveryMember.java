package com.waimai.common.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("joint_delivery_member")
public class JointDeliveryMember {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long groupId;
    private Long riderId;
    private Long orderId;
    private String status;
    private BigDecimal earnings;
    private LocalDateTime joinTime;
    private LocalDateTime pickupTime;
    private LocalDateTime completeTime;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
