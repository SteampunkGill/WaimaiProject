package com.waimai.api.controller;

import com.waimai.api.annotation.RateLimit;
import com.waimai.common.Result;
import com.waimai.common.constant.MerchantStatus;
import com.waimai.common.constant.RiderAuditStatus;
import com.waimai.common.dto.*;
import com.waimai.common.entity.Merchant;
import com.waimai.common.entity.Rider;
import com.waimai.common.entity.User;
import com.waimai.common.exception.BusinessException;
import com.waimai.common.utils.JwtUtil;
import com.waimai.common.vo.LoginVO;
import com.waimai.service.service.MerchantService;
import com.waimai.service.service.RiderService;
import com.waimai.service.service.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;
    private final MerchantService merchantService;
    private final RiderService riderService;
    private final JwtUtil jwtUtil;

    public AuthController(UserService userService, MerchantService merchantService,
                          RiderService riderService, JwtUtil jwtUtil) {
        this.userService = userService;
        this.merchantService = merchantService;
        this.riderService = riderService;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/login/wechat")
    public Result<LoginVO> wechatLogin(@Valid @RequestBody LoginDTO dto) {
        String openid = "wx_" + dto.getCode();
        User user = userService.loginByWechat(openid, dto.getNickname(), dto.getAvatar());

        String accessToken = jwtUtil.generateAccessToken(user.getId(), user.getOpenid());
        String refreshToken = jwtUtil.generateRefreshToken(user.getId(), user.getOpenid());

        LoginVO vo = new LoginVO(accessToken, refreshToken, user.getId(), user.getNickname(), user.getAvatar());
        return Result.ok(vo);
    }

    @PostMapping("/login/merchant/wechat")
    public Result<LoginVO> merchantLogin(@Valid @RequestBody LoginDTO dto) {
        String openid = "wx_" + dto.getCode();
        Merchant merchant = merchantService.getByOpenid(openid);
        if (merchant == null) {
            throw new BusinessException("商家未入驻，请先提交入驻申请");
        }
        if (merchant.getStatus() == MerchantStatus.PENDING) {
            throw new BusinessException("商家正在审核中，请耐心等待");
        }
        if (merchant.getStatus() == MerchantStatus.REJECTED) {
            throw new BusinessException("商家入驻申请已被驳回，请联系平台");
        }
        if (merchant.getStatus() == MerchantStatus.DISABLED) {
            throw new BusinessException("商家已被停用，请联系平台");
        }
        String accessToken = jwtUtil.generateAccessToken(merchant.getId(), merchant.getOpenid());
        String refreshToken = jwtUtil.generateRefreshToken(merchant.getId(), merchant.getOpenid());
        return Result.ok(new LoginVO(accessToken, refreshToken, merchant.getId(), merchant.getName(), merchant.getLogo()));
    }

    @PostMapping("/login/rider/wechat")
    public Result<LoginVO> riderLogin(@Valid @RequestBody LoginDTO dto) {
        String openid = "wx_" + dto.getCode();
        Rider rider = riderService.getByOpenid(openid);
        if (rider == null) {
            throw new BusinessException("骑手未注册，请先注册");
        }
        // Allow login for all audit statuses so rider can check audit progress
        String accessToken = jwtUtil.generateAccessToken(rider.getId(), rider.getOpenid());
        String refreshToken = jwtUtil.generateRefreshToken(rider.getId(), rider.getOpenid());
        return Result.ok(new LoginVO(accessToken, refreshToken, rider.getId(), rider.getRealName(), rider.getAvatar()));
    }

    @PostMapping("/register/rider")
    public Result<LoginVO> registerRider(@Valid @RequestBody RiderRegisterDTO dto) {
        String openid = "wx_" + dto.getCode();

        // Check if already registered and approved
        Rider exist = riderService.getByOpenid(openid);
        if (exist != null && exist.getAuditStatus() == RiderAuditStatus.APPROVED) {
            throw new BusinessException("您已注册并通过审核，请直接登录");
        }

        Rider rider = new Rider();
        rider.setOpenid(openid);
        rider.setRealName(dto.getRealName());
        rider.setIdCard(dto.getIdCard());
        rider.setPhone(dto.getPhone());
        rider.setPassword(dto.getPassword());
        rider.setAvatar(dto.getAvatar());
        riderService.register(rider);

        // Auto-login after registration: issue token so rider can check audit status immediately
        String accessToken = jwtUtil.generateAccessToken(rider.getId(), openid);
        String refreshToken = jwtUtil.generateRefreshToken(rider.getId(), openid);
        return Result.ok(new LoginVO(accessToken, refreshToken, rider.getId(), rider.getRealName(), rider.getAvatar()));
    }

    @GetMapping("/register/rider/status")
    public Result<Map<String, Object>> riderRegisterStatus(@RequestParam String code) {
        String openid = "wx_" + code;
        Rider rider = riderService.getByOpenid(openid);
        Map<String, Object> result = new HashMap<>();
        if (rider == null) {
            result.put("registered", false);
            result.put("auditStatus", -1);
            result.put("auditStatusText", "未注册");
        } else {
            result.put("registered", true);
            result.put("auditStatus", rider.getAuditStatus());
            result.put("auditStatusText", auditStatusText(rider.getAuditStatus()));
            result.put("rejectionReason", rider.getRejectionReason());
            result.put("realName", rider.getRealName());
            result.put("phone", rider.getPhone());
        }
        return Result.ok(result);
    }

    // ── Password-based auth endpoints ──────────────────────────────

    @RateLimit(maxRequests = 3, windowSeconds = 60)
    @PostMapping("/send-code")
    public Result<?> sendCode(@Valid @RequestBody SendCodeDTO dto) {
        userService.sendSmsCode(dto.getPhone());
        return Result.ok();
    }

    @PostMapping("/register/user")
    public Result<LoginVO> registerUser(@Valid @RequestBody UserRegisterDTO dto) {
        if (!userService.verifySmsCode(dto.getPhone(), dto.getCode())) {
            return Result.fail(400, "验证码错误或已过期");
        }
        User user = userService.registerByPassword(dto.getPhone(), dto.getPassword(), dto.getNickname());
        String accessToken = jwtUtil.generateAccessToken(user.getId(), user.getOpenid());
        String refreshToken = jwtUtil.generateRefreshToken(user.getId(), user.getOpenid());
        return Result.ok(new LoginVO(accessToken, refreshToken, user.getId(), user.getNickname(), user.getAvatar()));
    }

    @PostMapping("/login/user")
    public Result<LoginVO> userLogin(@Valid @RequestBody PasswordLoginDTO dto) {
        User user = userService.loginByPassword(dto.getPhone(), dto.getPassword());
        String accessToken = jwtUtil.generateAccessToken(user.getId(), user.getOpenid());
        String refreshToken = jwtUtil.generateRefreshToken(user.getId(), user.getOpenid());
        return Result.ok(new LoginVO(accessToken, refreshToken, user.getId(), user.getNickname(), user.getAvatar()));
    }

    @PostMapping("/login/merchant")
    public Result<LoginVO> merchantPasswordLogin(@Valid @RequestBody PasswordLoginDTO dto) {
        Merchant merchant = merchantService.loginByPassword(dto.getPhone(), dto.getPassword());
        String accessToken = jwtUtil.generateAccessToken(merchant.getId(), merchant.getOpenid());
        String refreshToken = jwtUtil.generateRefreshToken(merchant.getId(), merchant.getOpenid());
        return Result.ok(new LoginVO(accessToken, refreshToken, merchant.getId(), merchant.getName(), merchant.getLogo()));
    }

    @PostMapping("/login/rider")
    public Result<LoginVO> riderPasswordLogin(@Valid @RequestBody PasswordLoginDTO dto) {
        Rider rider = riderService.loginByPassword(dto.getPhone(), dto.getPassword());
        String accessToken = jwtUtil.generateAccessToken(rider.getId(), rider.getOpenid());
        String refreshToken = jwtUtil.generateRefreshToken(rider.getId(), rider.getOpenid());
        return Result.ok(new LoginVO(accessToken, refreshToken, rider.getId(), rider.getRealName(), rider.getAvatar()));
    }

    @PostMapping("/login/admin")
    public Result<LoginVO> adminLogin(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");
        if (!"admin".equals(username) || !"admin123".equals(password)) {
            throw new BusinessException("账号或密码错误");
        }
        // Admin uses userId=0, openid=admin as sentinel values
        String accessToken = jwtUtil.generateAccessToken(0L, "admin");
        String refreshToken = jwtUtil.generateRefreshToken(0L, "admin");
        return Result.ok(new LoginVO(accessToken, refreshToken, 0L, "管理员", null));
    }

    @PostMapping("/refresh-token")
    public Result<LoginVO> refreshToken(@RequestHeader("Authorization") String authHeader) {
        String token = authHeader.substring(7);
        if (!jwtUtil.validateToken(token)) {
            return Result.fail(401, "refresh token已过期");
        }
        var claims = jwtUtil.parseToken(token);
        Long userId = claims.get("userId", Long.class);
        String openid = claims.get("openid", String.class);

        String accessToken = jwtUtil.generateAccessToken(userId, openid);
        String refreshToken = jwtUtil.generateRefreshToken(userId, openid);

        return Result.ok(new LoginVO(accessToken, refreshToken, userId, null, null));
    }

    private String auditStatusText(Integer status) {
        if (status == null) return "未知";
        return switch (status) {
            case 0 -> "待审核";
            case 1 -> "审核通过";
            case 2 -> "已驳回";
            default -> "未知";
        };
    }
}
