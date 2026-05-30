package com.waimai.service.impl;

import com.waimai.service.push.OrderPushService;
import com.waimai.common.constant.JointDeliveryStatus;
import com.waimai.common.constant.OrderStatus;
import com.waimai.common.entity.JointDeliveryGroup;
import com.waimai.common.entity.Merchant;
import com.waimai.common.entity.Order;
import com.waimai.common.entity.Rider;
import com.waimai.common.exception.BusinessException;
import com.waimai.common.vo.RiderNearbyVO;
import com.waimai.service.service.DispatchService;
import com.waimai.service.service.GeoService;
import com.waimai.service.service.JointDeliveryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class DispatchServiceImpl implements DispatchService {

    private static final double MAX_DISTANCE_KM = 5.0;
    private static final double DISTANCE_WEIGHT = 0.6;
    private static final double LOAD_WEIGHT = 0.4;

    private final GeoService geoService;
    private final OrderServiceImpl orderService;
    private final MerchantServiceImpl merchantService;
    private final RiderServiceImpl riderService;
    private final OrderPushService orderPushService;
    private final JointDeliveryService jointDeliveryService;

    public DispatchServiceImpl(GeoService geoService, OrderServiceImpl orderService,
                               MerchantServiceImpl merchantService, RiderServiceImpl riderService,
                               OrderPushService orderPushService,
                               JointDeliveryService jointDeliveryService) {
        this.geoService = geoService;
        this.orderService = orderService;
        this.merchantService = merchantService;
        this.riderService = riderService;
        this.orderPushService = orderPushService;
        this.jointDeliveryService = jointDeliveryService;
    }

    @Override
    public Long dispatchOrder(Long orderId) {
        Order order = orderService.getById(orderId);
        if (order == null) throw new BusinessException("订单不存在");
        if (!OrderStatus.PREPARING.equals(order.getStatus())) {
            throw new BusinessException("订单状态不是备餐中，无需派单");
        }

        JointDeliveryGroup group = jointDeliveryService.getByOrderId(orderId);
        if (group != null && JointDeliveryStatus.RECRUITING.equals(group.getStatus())) {
            jointDeliveryService.dispatchJointDelivery(group.getId());
            return null;
        }

        Merchant merchant = merchantService.getById(order.getMerchantId());
        if (merchant == null || merchant.getLatitude() == null || merchant.getLongitude() == null) {
            throw new BusinessException("商家位置信息不完整");
        }

        double merchantLng = merchant.getLongitude().doubleValue();
        double merchantLat = merchant.getLatitude().doubleValue();

        List<RiderNearbyVO> nearbyRiders = geoService.searchNearbyRiders(merchantLng, merchantLat, MAX_DISTANCE_KM);
        if (nearbyRiders.isEmpty()) {
            log.warn("附近无可用骑手: merchantId={}, orderId={}", merchant.getId(), orderId);
            return null;
        }

        Optional<RiderNearbyVO> bestRider = nearbyRiders.stream()
                .max(Comparator.comparingDouble(r -> scoreRider(r, r.getDistanceKm())));

        if (bestRider.isEmpty()) {
            log.warn("骑手评分失败: orderId={}", orderId);
            return null;
        }

        RiderNearbyVO selected = bestRider.get();
        log.info("派单: orderId={}, riderId={}, distance={}km, load={}, score={}",
                orderId, selected.getId(), selected.getDistanceKm(), selected.getCurrentLoad(),
                scoreRider(selected, selected.getDistanceKm()));

        orderPushService.pushDispatchToRider(selected.getId(), orderId);
        return selected.getId();
    }

    @Override
    public void autoDispatchForMerchant(Long merchantId) {
        List<Order> preparingOrders = orderService.lambdaQuery()
                .eq(Order::getMerchantId, merchantId)
                .eq(Order::getStatus, OrderStatus.PREPARING)
                .list();

        for (Order order : preparingOrders) {
            dispatchOrder(order.getId());
        }
    }

    private double scoreRider(RiderNearbyVO rider, double distanceKm) {
        double distanceScore = 1.0 - (distanceKm / MAX_DISTANCE_KM);
        double loadScore = 1.0 - (Math.min(rider.getCurrentLoad(), 5) / 5.0);
        return DISTANCE_WEIGHT * distanceScore + LOAD_WEIGHT * loadScore;
    }
}
