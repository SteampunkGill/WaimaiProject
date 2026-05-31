package com.waimai.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.waimai.common.constant.MerchantStatus;
import com.waimai.common.entity.Merchant;
import com.waimai.common.exception.BusinessException;
import com.waimai.common.utils.BCryptUtil;
import com.waimai.service.mapper.MerchantMapper;
import com.waimai.service.service.MerchantService;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MerchantServiceImpl extends ServiceImpl<MerchantMapper, Merchant> implements MerchantService {

    private static final double KM_TO_DEGREE = 1.0 / 111.0;

    private final GeoServiceImpl geoService;

    public MerchantServiceImpl(@Lazy GeoServiceImpl geoService) {
        this.geoService = geoService;
    }

    @Override
    public Merchant getByOpenid(String openid) {
        return lambdaQuery().eq(Merchant::getOpenid, openid).one();
    }

    @Override
    public Merchant getByPhone(String phone) {
        return lambdaQuery().eq(Merchant::getPhone, phone).one();
    }

    @Override
    public Merchant loginByPassword(String phone, String password) {
        Merchant merchant = getByPhone(phone);
        if (merchant == null) {
            throw new BusinessException("手机号未注册，请先申请入驻");
        }
        if (merchant.getPassword() == null || !BCryptUtil.checkPassword(password, merchant.getPassword())) {
            throw new BusinessException("密码错误");
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
        return merchant;
    }

    @Override
    public Merchant apply(Merchant merchant) {
        Merchant exist = getByOpenid(merchant.getOpenid());
        if (exist != null && exist.getStatus() != MerchantStatus.REJECTED) {
            throw new BusinessException("您已申请过，请勿重复申请");
        }
        // Hash password if provided
        if (merchant.getPassword() != null && !merchant.getPassword().isBlank()) {
            merchant.setPassword(BCrypt.hashpw(merchant.getPassword(), BCrypt.gensalt()));
        }
        merchant.setId(null);
        merchant.setStatus(MerchantStatus.PENDING);
        merchant.setScore(java.math.BigDecimal.valueOf(5.0));
        // Set default coordinates (Beijing area) if not provided
        if (merchant.getLongitude() == null) merchant.setLongitude(new java.math.BigDecimal("116.397"));
        if (merchant.getLatitude() == null) merchant.setLatitude(new java.math.BigDecimal("39.908"));
        // Set default delivery fee
        if (merchant.getDeliveryFee() == null) merchant.setDeliveryFee(new java.math.BigDecimal("5.00"));
        if (merchant.getAvgDeliveryTime() == null) merchant.setAvgDeliveryTime(30);
        save(merchant);
        return merchant;
    }

    @Override
    public void audit(Long merchantId, Integer status, String reason) {
        Merchant merchant = getById(merchantId);
        if (merchant == null) {
            throw new BusinessException("商家不存在");
        }
        merchant.setStatus(status);
        if (status == MerchantStatus.REJECTED && reason != null && !reason.isBlank()) {
            merchant.setRejectionReason(reason);
        }
        if (status == MerchantStatus.APPROVED) {
            merchant.setRejectionReason(null);
            // Add merchant to Redis GeoHash so customers can find it
            if (merchant.getLongitude() != null && merchant.getLatitude() != null) {
                geoService.addMerchantLocation(merchant.getId(),
                        merchant.getLongitude().doubleValue(),
                        merchant.getLatitude().doubleValue());
            }
        }
        updateById(merchant);
    }

    @Override
    public List<Merchant> searchNearby(double lng, double lat, double radiusKm) {
        double dLat = radiusKm * KM_TO_DEGREE;
        double dLng = radiusKm * KM_TO_DEGREE / Math.cos(Math.toRadians(lat));
        return lambdaQuery()
                .eq(Merchant::getStatus, MerchantStatus.APPROVED)
                .between(Merchant::getLatitude, lat - dLat, lat + dLat)
                .between(Merchant::getLongitude, lng - dLng, lng + dLng)
                .list();
    }

    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void loadApprovedMerchantsToGeo() {
        try {
            List<Merchant> approved = lambdaQuery()
                    .eq(Merchant::getStatus, MerchantStatus.APPROVED)
                    .isNotNull(Merchant::getLongitude)
                    .isNotNull(Merchant::getLatitude)
                    .list();
            for (Merchant m : approved) {
                try {
                    geoService.addMerchantLocation(m.getId(),
                            m.getLongitude().doubleValue(),
                            m.getLatitude().doubleValue());
                } catch (Exception ignored) { }
            }
        } catch (Exception ignored) { }
    }
}
