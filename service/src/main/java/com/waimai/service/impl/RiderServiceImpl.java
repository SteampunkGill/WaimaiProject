package com.waimai.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.waimai.common.constant.OrderStatus;
import com.waimai.common.constant.RiderAuditStatus;
import com.waimai.common.constant.RiderStatus;
import com.waimai.common.entity.*;
import com.waimai.common.exception.BusinessException;
import com.waimai.service.mapper.DeliveryTrackMapper;
import com.waimai.service.mapper.OrderMapper;
import com.waimai.service.mapper.RiderMapper;
import com.waimai.service.service.RiderService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class RiderServiceImpl extends ServiceImpl<RiderMapper, Rider> implements RiderService {

    private final OrderMapper orderMapper;
    private final DeliveryTrackMapper deliveryTrackMapper;
    private final RiderIncomeServiceImpl riderIncomeService;
    private final EtaServiceImpl etaService;
    private final RiderLevelServiceImpl riderLevelService;

    public RiderServiceImpl(OrderMapper orderMapper, DeliveryTrackMapper deliveryTrackMapper,
                            RiderIncomeServiceImpl riderIncomeService,
                            EtaServiceImpl etaService,
                            RiderLevelServiceImpl riderLevelService) {
        this.orderMapper = orderMapper;
        this.deliveryTrackMapper = deliveryTrackMapper;
        this.riderIncomeService = riderIncomeService;
        this.etaService = etaService;
        this.riderLevelService = riderLevelService;
    }

    @Override
    public Rider getByOpenid(String openid) {
        return lambdaQuery().eq(Rider::getOpenid, openid).one();
    }

    @Override
    public void register(Rider rider) {
        Rider exist = getByOpenid(rider.getOpenid());
        if (exist != null && exist.getAuditStatus() != RiderAuditStatus.REJECTED) {
            throw new BusinessException("您已申请过，请勿重复申请");
        }
        if (exist != null && exist.getAuditStatus() == RiderAuditStatus.REJECTED) {
            // Re-apply: update existing record
            rider.setId(exist.getId());
        } else {
            rider.setId(null);
        }
        rider.setAuditStatus(RiderAuditStatus.PENDING);
        rider.setStatus(RiderStatus.OFFLINE);
        rider.setTotalOrders(0);
        rider.setScore(new java.math.BigDecimal("5.0"));
        saveOrUpdate(rider);
    }

    @Override
    public void auditRider(Long riderId, Integer auditStatus, String reason) {
        Rider rider = getById(riderId);
        if (rider == null) {
            throw new BusinessException("骑手不存在");
        }
        rider.setAuditStatus(auditStatus);
        if (auditStatus == RiderAuditStatus.REJECTED && reason != null && !reason.isBlank()) {
            rider.setRejectionReason(reason);
        }
        if (auditStatus == RiderAuditStatus.APPROVED) {
            rider.setRejectionReason(null);
        }
        updateById(rider);
    }

    @Override
    public void updateLocation(Long riderId, double lng, double lat) {
        Rider rider = getById(riderId);
        if (rider != null) {
            rider.setCurrentLng(new java.math.BigDecimal(lng));
            rider.setCurrentLat(new java.math.BigDecimal(lat));
            updateById(rider);
        }
    }

    @Override
    public void goOnline(Long riderId) {
        Rider rider = getById(riderId);
        if (rider == null) throw new BusinessException("骑手不存在");
        if (rider.getAuditStatus() != RiderAuditStatus.APPROVED) {
            throw new BusinessException("账号尚未通过审核，无法上线");
        }
        if (rider.getStatus() != RiderStatus.OFFLINE) {
            throw new BusinessException("当前状态不允许上线");
        }
        rider.setStatus(RiderStatus.ONLINE);
        updateById(rider);
    }

    @Override
    public void goOffline(Long riderId) {
        Rider rider = getById(riderId);
        if (rider == null) throw new BusinessException("骑手不存在");
        rider.setStatus(RiderStatus.OFFLINE);
        updateById(rider);
    }

    @Override
    @Transactional
    public void acceptTask(Long riderId, Long orderId) {
        Rider rider = getById(riderId);
        if (rider.getAuditStatus() != RiderAuditStatus.APPROVED) {
            throw new BusinessException("账号尚未通过审核，无法接单");
        }
        Order order = orderMapper.selectById(orderId);
        if (order == null) throw new BusinessException("订单不存在");
        if (order.getIsJointDelivery() != null && order.getIsJointDelivery() == 1) {
            throw new BusinessException("该订单为联合配送订单，请在联合配送页面操作");
        }
        if (!OrderStatus.PREPARING.equals(order.getStatus())) {
            throw new BusinessException("订单已被其他骑手接单");
        }
        order.setRiderId(riderId);
        order.setStatus(OrderStatus.ACCEPTED);
        order.setDeliverTime(LocalDateTime.now());
        int eta = etaService.calculateEta(orderId);
        order.setEstimatedMinutes(eta);
        orderMapper.updateById(order);
    }

    @Override
    @Transactional
    public void pickupTask(Long riderId, Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null || !riderId.equals(order.getRiderId())) {
            throw new BusinessException("订单不存在或未分配给您");
        }
        if (!OrderStatus.ACCEPTED.equals(order.getStatus())) {
            throw new BusinessException("当前状态不允许取餐操作");
        }
        order.setStatus(OrderStatus.DELIVERING);
        orderMapper.updateById(order);
    }

    @Override
    @Transactional
    public void completeTask(Long riderId, Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null || !riderId.equals(order.getRiderId())) {
            throw new BusinessException("订单不存在");
        }
        order.setStatus(OrderStatus.COMPLETED);
        order.setCompleteTime(LocalDateTime.now());
        orderMapper.updateById(order);

        Rider rider = getById(riderId);
        if (rider != null) {
            rider.setTotalOrders(rider.getTotalOrders() + 1);
            updateById(rider);
            // Record income (base delivery fee from order, default 5.00 if null or zero)
            java.math.BigDecimal deliveryFee = order.getDeliveryFee();
            if (deliveryFee == null || deliveryFee.compareTo(java.math.BigDecimal.ZERO) <= 0) {
                deliveryFee = new java.math.BigDecimal("5.00");
            }
            riderIncomeService.recordIncome(riderId, orderId, deliveryFee);
            riderLevelService.recalculateLevel(riderId);
        }
    }

    @Override
    public List<DeliveryTrack> getTracks(Long orderId) {
        return deliveryTrackMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<DeliveryTrack>()
                        .eq(DeliveryTrack::getOrderId, orderId)
                        .orderByAsc(DeliveryTrack::getCreateTime)
        );
    }
}
