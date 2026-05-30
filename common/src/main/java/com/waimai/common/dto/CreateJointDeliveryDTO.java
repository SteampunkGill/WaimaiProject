package com.waimai.common.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateJointDeliveryDTO {
    @NotNull
    private Long orderId;

    @Min(2)
    private int requiredRiderCount;
}
