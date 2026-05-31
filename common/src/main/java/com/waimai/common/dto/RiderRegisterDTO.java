package com.waimai.common.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RiderRegisterDTO {
    @NotBlank(message = "code不能为空")
    private String code;
    private String nickname;
    private String avatar;

    @NotBlank(message = "真实姓名不能为空")
    private String realName;

    @NotBlank(message = "身份证号不能为空")
    private String idCard;

    @NotBlank(message = "手机号不能为空")
    private String phone;

    @NotBlank(message = "密码不能为空")
    @jakarta.validation.constraints.Size(min = 6, max = 32, message = "密码长度6-32位")
    private String password;
}
