package com.waimai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.waimai.common.constant.JointDeliveryStatus;
import com.waimai.common.constant.OrderStatus;
import com.waimai.common.constant.RiderAuditStatus;
import com.waimai.common.constant.RiderStatus;
import com.waimai.common.dto.JointDeliveryDispatchDTO;
import com.waimai.common.dto.WsMessage;
import com.waimai.common.entity.*;
import com.waimai.common.exception.BusinessException;
import com.waimai.common.utils.SnowflakeUtil;
import com.waimai.common.vo.RiderNearbyVO;
import com.waimai.common.websocket.WebSocketServer;
import com.waimai.service.mapper.JointDeliveryGroupMapper;
import com.waimai.service.mapper.JointDeliveryMemberMapper;
import com.waimai.service.mapper.MerchantMapper;
import com.waimai.service.mapper.OrderMapper;
import com.waimai.service.mapper.RiderMapper;
import com.waimai.service.push.OrderPushService;
import com.waimai.service.service.GeoService;
import com.waimai.service.service.JointDeliveryService;
import com.waimai.service.impl.EtaServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class JointDeliveryServiceImpl implements JointDeliveryService {

    private static final double MAX_DISTANCE_KM = 5.0;
    private static final double DISTANCE_WEIGHT = 0.5;
    private static final double LOAD_WEIGHT = 0.5;
    private static final double INVITE_MULTIPLIER = 3.0;

    private final JointDeliveryGroupMapper groupMapper;
    private final JointDeliveryMemberMapper memberMapper;
    private final OrderMapper orderMapper;
    private final RiderMapper riderMapper;
    private final MerchantMapper merchantMapper;
    private final GeoService geoService;
    private final SnowflakeUtil snowflakeUtil;
    private final OrderPushService orderPushService;
    private final RiderIncomeServiceImpl riderIncomeService;
    private final RiderLevelServiceImpl riderLevelService;
    private final EtaServiceImpl etaService;

    public JointDeliveryServiceImpl(JointDeliveryGroupMapper groupMapper,
                                     JointDeliveryMemberMapper memberMapper,
                                     OrderMapper orderMapper,
                                     RiderMapper riderMapper,
                                     MerchantMapper merchantMapper,
                                     GeoService geoService,
                                     SnowflakeUtil snowflakeUtil,
                                     OrderPushService orderPushService,
                                     RiderIncomeServiceImpl riderIncomeService,
                                     RiderLevelServiceImpl riderLevelService,
                                     EtaServiceImpl etaService) {
        this.groupMapper = groupMapper;
        this.memberMapper = memberMapper;
        this.orderMapper = orderMapper;
        this.riderMapper = riderMapper;
        this.merchantMapper = merchantMapper;
        this.geoService = geoService;
        this.snowflakeUtil = snowflakeUtil;
        this.orderPushService = orderPushService;
        this.riderIncomeService = riderIncomeService;
        this.riderLevelService = riderLevelService;
        this.etaService = etaService;
    }

    @Override
    @Transactional
    public JointDeliveryGroup createJointDelivery(Long orderId, int requiredRiderCount) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) throw new BusinessException("订单不存在");
        if (!OrderStatus.PREPARING.equals(order.getStatus())) {
            throw new BusinessException("订单状态不是备餐中，无法创建联合配送");
        }

        JointDeliveryGroup existing = getByOrderId(orderId);
        if (existing != null) throw new BusinessException("该订单已创建联合配送组");

        JointDeliveryGroup group = new JointDeliveryGroup();
        group.setOrderId(orderId);
        group.setGroupNo(String.valueOf(snowflakeUtil.nextId()));
        group.setRequiredRiderCount(requiredRiderCount);
        group.setJoinedRiderCount(0);
        group.setCompletedRiderCount(0);
        group.setStatus(JointDeliveryStatus.RECRUITING);
        BigDecimal deliveryFee = order.getDeliveryFee();
        if (deliveryFee == null || deliveryFee.compareTo(BigDecimal.ZERO) <= 0) {
            deliveryFee = new BigDecimal("5.00");
        }
        group.setDeliveryFeeTotal(deliveryFee);
        groupMapper.insert(group);

        order.setIsJointDelivery(1);
        orderMapper.updateById(order);

        dispatchJointDelivery(group.getId());
        return group;
    }

    @Override
    @Transactional
    public void cancelJointDelivery(Long groupId) {
        JointDeliveryGroup group = groupMapper.selectById(groupId);
        if (group == null) throw new BusinessException("联合配送组不存在");
        if (!JointDeliveryStatus.RECRUITING.equals(group.getStatus())) {
            throw new BusinessException("当前状态不允许取消");
        }
        group.setStatus(JointDeliveryStatus.CANCELLED);
        groupMapper.updateById(group);

        List<JointDeliveryMember> members = listMembersByGroup(groupId);
        for (JointDeliveryMember m : members) {
            if (JointDeliveryStatus.MEMBER_INVITED.equals(m.getStatus())
                    || JointDeliveryStatus.MEMBER_JOINED.equals(m.getStatus())) {
                m.setStatus(JointDeliveryStatus.MEMBER_CANCELLED);
                memberMapper.updateById(m);
            }
        }
    }

    @Override
    @Transactional
    public void dispatchJointDelivery(Long groupId) {
        JointDeliveryGroup group = groupMapper.selectById(groupId);
        if (group == null) throw new BusinessException("联合配送组不存在");

        Order order = orderMapper.selectById(group.getOrderId());
        if (order == null) throw new BusinessException("订单不存在");

        Merchant merchant = merchantMapper.selectById(order.getMerchantId());
        if (merchant == null || merchant.getLongitude() == null || merchant.getLatitude() == null) {
            log.warn("商家位置信息不完整: orderId={}", group.getOrderId());
            return;
        }

        double merchantLng = merchant.getLongitude().doubleValue();
        double merchantLat = merchant.getLatitude().doubleValue();

        List<RiderNearbyVO> nearbyRiders = geoService.searchNearbyRiders(merchantLng, merchantLat, MAX_DISTANCE_KM);

        // Fallback: if GEO returns empty, query online riders from DB
        if (nearbyRiders.isEmpty()) {
            log.info("GEO returned empty, falling back to DB query for online riders");
            List<Rider> onlineRiders = riderMapper.selectList(new LambdaQueryWrapper<Rider>()
                    .eq(Rider::getStatus, RiderStatus.ONLINE)
                    .eq(Rider::getAuditStatus, RiderAuditStatus.APPROVED));
            nearbyRiders = onlineRiders.stream().map(r -> {
                RiderNearbyVO vo = new RiderNearbyVO();
                vo.setId(r.getId());
                vo.setRealName(r.getRealName());
                vo.setPhone(r.getPhone());
                vo.setDistanceKm(1.0);
                vo.setCurrentLoad(0);
                vo.setScore(r.getScore() != null ? r.getScore().doubleValue() : 5.0);
                return vo;
            }).collect(Collectors.toList());
        }
        if (nearbyRiders.isEmpty()) {
            log.warn("附近无可用骑手: groupId={}", groupId);
            return;
        }

        List<RiderNearbyVO> candidates = nearbyRiders.stream()
                .sorted(Comparator.comparingDouble(r ->
                        -scoreRider(r, r.getDistanceKm())))
                .limit((long) (group.getRequiredRiderCount() * INVITE_MULTIPLIER))
                .collect(Collectors.toList());

        BigDecimal splitEarnings = group.getDeliveryFeeTotal()
                .divide(new BigDecimal(group.getRequiredRiderCount()), 2, RoundingMode.HALF_UP);

        JointDeliveryDispatchDTO dto = new JointDeliveryDispatchDTO();
        dto.setGroupId(group.getId());
        dto.setGroupNo(group.getGroupNo());
        dto.setOrderId(order.getId());
        dto.setOrderNo(order.getOrderNo());
        dto.setMerchantName(merchant.getName());
        dto.setAddress(order.getAddress());
        dto.setAddressLng(order.getAddressLng());
        dto.setAddressLat(order.getAddressLat());
        dto.setRequiredRiderCount(group.getRequiredRiderCount());
        dto.setJoinedRiderCount(group.getJoinedRiderCount());
        dto.setSplitEarnings(splitEarnings);

        for (RiderNearbyVO rider : candidates) {
            JointDeliveryMember existing = memberMapper.selectOne(new LambdaQueryWrapper<JointDeliveryMember>()
                    .eq(JointDeliveryMember::getGroupId, groupId)
                    .eq(JointDeliveryMember::getRiderId, rider.getId()));
            if (existing != null) continue;

            JointDeliveryMember member = new JointDeliveryMember();
            member.setGroupId(groupId);
            member.setRiderId(rider.getId());
            member.setOrderId(group.getOrderId());
            member.setStatus(JointDeliveryStatus.MEMBER_INVITED);
            member.setEarnings(BigDecimal.ZERO);
            memberMapper.insert(member);

            WsMessage msg = new WsMessage("JOINT_DELIVERY_INVITE", dto);
            WebSocketServer.sendToRider(rider.getId(), msg);
            log.info("联合配送邀请推送: riderId={}, groupId={}", rider.getId(), groupId);
        }
    }

    @Override
    @Transactional
    public JointDeliveryMember riderJoin(Long groupId, Long riderId) {
        JointDeliveryGroup group = groupMapper.selectById(groupId);
        if (group == null) throw new BusinessException("联合配送组不存在");
        if (!JointDeliveryStatus.RECRUITING.equals(group.getStatus())) {
            throw new BusinessException("该联合配送组已不再招募骑手");
        }

        Rider rider = riderMapper.selectById(riderId);
        if (rider == null) throw new BusinessException("骑手不存在");
        if (rider.getAuditStatus() != RiderAuditStatus.APPROVED) {
            throw new BusinessException("账号尚未通过审核");
        }
        if (rider.getStatus() != RiderStatus.ONLINE) {
            throw new BusinessException("请先上线再接单");
        }

        JointDeliveryMember member = memberMapper.selectOne(new LambdaQueryWrapper<JointDeliveryMember>()
                .eq(JointDeliveryMember::getGroupId, groupId)
                .eq(JointDeliveryMember::getRiderId, riderId));
        if (member == null) throw new BusinessException("您未被邀请参与此联合配送");
        if (!JointDeliveryStatus.MEMBER_INVITED.equals(member.getStatus())) {
            throw new BusinessException("您已处理过此邀请");
        }

        member.setStatus(JointDeliveryStatus.MEMBER_JOINED);
        member.setJoinTime(LocalDateTime.now());
        memberMapper.updateById(member);

        group.setJoinedRiderCount(group.getJoinedRiderCount() + 1);
        boolean allJoined = group.getJoinedRiderCount() >= group.getRequiredRiderCount();
        if (allJoined) {
            group.setStatus(JointDeliveryStatus.READY);
        }
        groupMapper.updateById(group);

        Order order = orderMapper.selectById(group.getOrderId());
        if (order != null && allJoined) {
            order.setStatus(OrderStatus.ACCEPTED);
            order.setDeliverTime(LocalDateTime.now());
            int eta = etaService.calculateEta(order.getId());
            order.setEstimatedMinutes(eta);
            orderMapper.updateById(order);
        }

        notifyGroupUpdate(group);
        if (order != null) {
            Map<String, Object> data = new HashMap<>();
            data.put("groupId", group.getId());
            data.put("riderId", riderId);
            data.put("riderName", rider.getRealName());
            data.put("joinedCount", group.getJoinedRiderCount());
            data.put("requiredCount", group.getRequiredRiderCount());
            WsMessage msg = new WsMessage("JOINT_DELIVERY_RIDER_JOINED", data);
            WebSocketServer.sendToUser(order.getUserId(), msg);
        }

        return member;
    }

    @Override
    @Transactional
    public void riderCancelJoin(Long memberId, Long riderId) {
        JointDeliveryMember member = memberMapper.selectById(memberId);
        if (member == null) throw new BusinessException("成员记录不存在");
        if (!riderId.equals(member.getRiderId())) throw new BusinessException("无权操作");
        if (!JointDeliveryStatus.MEMBER_INVITED.equals(member.getStatus())) {
            throw new BusinessException("当前状态无法取消");
        }
        member.setStatus(JointDeliveryStatus.MEMBER_CANCELLED);
        memberMapper.updateById(member);
    }

    @Override
    @Transactional
    public void riderPickup(Long memberId, Long riderId) {
        JointDeliveryMember member = memberMapper.selectById(memberId);
        if (member == null) throw new BusinessException("成员记录不存在");
        if (!riderId.equals(member.getRiderId())) throw new BusinessException("无权操作");

        JointDeliveryGroup group = groupMapper.selectById(member.getGroupId());
        if (group == null) throw new BusinessException("联合配送组不存在");
        if (!JointDeliveryStatus.READY.equals(group.getStatus())) {
            throw new BusinessException("等待所有骑手加入后才能取餐");
        }
        if (!JointDeliveryStatus.MEMBER_JOINED.equals(member.getStatus())) {
            throw new BusinessException("当前状态不允许取餐操作");
        }

        member.setStatus(JointDeliveryStatus.MEMBER_PICKED_UP);
        member.setPickupTime(LocalDateTime.now());
        memberMapper.updateById(member);

        long allPickedUp = memberMapper.selectCount(new LambdaQueryWrapper<JointDeliveryMember>()
                .eq(JointDeliveryMember::getGroupId, group.getId())
                .eq(JointDeliveryMember::getStatus, JointDeliveryStatus.MEMBER_PICKED_UP));
        if (allPickedUp >= group.getRequiredRiderCount()) {
            group.setStatus(JointDeliveryStatus.DELIVERING);
            groupMapper.updateById(group);

            Order order = orderMapper.selectById(group.getOrderId());
            if (order != null) {
                order.setStatus(OrderStatus.DELIVERING);
                orderMapper.updateById(order);
            }
        }
    }

    @Override
    @Transactional
    public void riderComplete(Long memberId, Long riderId) {
        JointDeliveryMember member = memberMapper.selectById(memberId);
        if (member == null) throw new BusinessException("成员记录不存在");
        if (!riderId.equals(member.getRiderId())) throw new BusinessException("无权操作");
        if (!JointDeliveryStatus.MEMBER_PICKED_UP.equals(member.getStatus())) {
            throw new BusinessException("当前状态不允许完成配送");
        }

        member.setStatus(JointDeliveryStatus.MEMBER_COMPLETED);
        member.setCompleteTime(LocalDateTime.now());
        memberMapper.updateById(member);

        JointDeliveryGroup group = groupMapper.selectById(member.getGroupId());
        if (group == null) return;

        BigDecimal splitEarnings = group.getDeliveryFeeTotal()
                .divide(new BigDecimal(group.getRequiredRiderCount()), 2, RoundingMode.HALF_UP);
        member.setEarnings(splitEarnings);
        memberMapper.updateById(member);

        riderIncomeService.recordIncome(riderId, group.getOrderId(), splitEarnings);

        Rider rider = riderMapper.selectById(riderId);
        if (rider != null) {
            rider.setTotalOrders(rider.getTotalOrders() + 1);
            riderMapper.updateById(rider);
            riderLevelService.recalculateLevel(riderId);
        }

        long completedCount = memberMapper.selectCount(new LambdaQueryWrapper<JointDeliveryMember>()
                .eq(JointDeliveryMember::getGroupId, group.getId())
                .eq(JointDeliveryMember::getStatus, JointDeliveryStatus.MEMBER_COMPLETED));
        group.setCompletedRiderCount((int) completedCount);

        if (completedCount >= group.getRequiredRiderCount()) {
            group.setStatus(JointDeliveryStatus.COMPLETED);
            groupMapper.updateById(group);

            Order order = orderMapper.selectById(group.getOrderId());
            if (order != null) {
                order.setStatus(OrderStatus.COMPLETED);
                order.setCompleteTime(LocalDateTime.now());
                orderMapper.updateById(order);
            }

            if (order != null) {
                WsMessage userMsg = new WsMessage("JOINT_DELIVERY_COMPLETED",
                        Map.of("orderId", order.getId(), "orderNo", order.getOrderNo()));
                WebSocketServer.sendToUser(order.getUserId(), userMsg);
            }
        } else {
            groupMapper.updateById(group);
        }

        notifyGroupUpdate(group);
    }

    // ─── Query methods ──────────────────────────────────────────

    @Override
    public JointDeliveryGroup getByOrderId(Long orderId) {
        return groupMapper.selectOne(new LambdaQueryWrapper<JointDeliveryGroup>()
                .eq(JointDeliveryGroup::getOrderId, orderId)
                .orderByDesc(JointDeliveryGroup::getCreateTime)
                .last("LIMIT 1"));
    }

    @Override
    public JointDeliveryGroup getByGroupId(Long groupId) {
        return groupMapper.selectById(groupId);
    }

    @Override
    public List<JointDeliveryMember> listMembersByGroup(Long groupId) {
        return memberMapper.selectList(new LambdaQueryWrapper<JointDeliveryMember>()
                .eq(JointDeliveryMember::getGroupId, groupId)
                .orderByAsc(JointDeliveryMember::getCreateTime));
    }

    @Override
    public List<JointDeliveryGroup> listMyJointDeliveries(Long riderId) {
        List<JointDeliveryMember> members = memberMapper.selectList(new LambdaQueryWrapper<JointDeliveryMember>()
                .eq(JointDeliveryMember::getRiderId, riderId)
                .in(JointDeliveryMember::getStatus, JointDeliveryStatus.MEMBER_JOINED,
                        JointDeliveryStatus.MEMBER_PICKED_UP, JointDeliveryStatus.MEMBER_COMPLETED)
                .orderByDesc(JointDeliveryMember::getCreateTime));
        if (members.isEmpty()) return Collections.emptyList();

        Set<Long> groupIds = members.stream().map(JointDeliveryMember::getGroupId).collect(Collectors.toSet());
        return groupMapper.selectList(new LambdaQueryWrapper<JointDeliveryGroup>()
                .in(JointDeliveryGroup::getId, groupIds)
                .orderByDesc(JointDeliveryGroup::getCreateTime));
    }

    @Override
    public List<JointDeliveryMember> listMyPendingInvites(Long riderId) {
        return memberMapper.selectList(new LambdaQueryWrapper<JointDeliveryMember>()
                .eq(JointDeliveryMember::getRiderId, riderId)
                .eq(JointDeliveryMember::getStatus, JointDeliveryStatus.MEMBER_INVITED)
                .orderByDesc(JointDeliveryMember::getCreateTime));
    }

    @Override
    public List<JointDeliveryMember> listMembersByRider(Long riderId) {
        return memberMapper.selectList(new LambdaQueryWrapper<JointDeliveryMember>()
                .eq(JointDeliveryMember::getRiderId, riderId)
                .orderByDesc(JointDeliveryMember::getCreateTime));
    }

    @Override
    public Map<String, Object> getJointDeliveryProgress(Long orderId) {
        JointDeliveryGroup group = getByOrderId(orderId);
        Map<String, Object> result = new HashMap<>();
        if (group == null) {
            result.put("hasGroup", false);
            return result;
        }
        result.put("hasGroup", true);
        result.put("group", group);

        List<JointDeliveryMember> members = listMembersByGroup(group.getId());
        List<Map<String, Object>> memberList = new ArrayList<>();
        for (JointDeliveryMember m : members) {
            Map<String, Object> mm = new HashMap<>();
            mm.put("id", m.getId());
            mm.put("riderId", m.getRiderId());
            mm.put("status", m.getStatus());
            mm.put("earnings", m.getEarnings());
            mm.put("joinTime", m.getJoinTime());
            mm.put("pickupTime", m.getPickupTime());
            mm.put("completeTime", m.getCompleteTime());
            Rider rider = riderMapper.selectById(m.getRiderId());
            if (rider != null) {
                mm.put("riderName", rider.getRealName());
                mm.put("riderPhone", rider.getPhone());
            }
            memberList.add(mm);
        }
        result.put("members", memberList);

        int progress = group.getRequiredRiderCount() > 0
                ? (group.getCompletedRiderCount() * 100 / group.getRequiredRiderCount())
                : 0;
        result.put("progress", progress + "%");
        return result;
    }

    @Override
    public List<JointDeliveryGroup> listAllGroups() {
        return groupMapper.selectList(new LambdaQueryWrapper<JointDeliveryGroup>()
                .orderByDesc(JointDeliveryGroup::getCreateTime));
    }

    @Override
    public List<Order> listAvailableOrders() {
        return orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .eq(Order::getStatus, OrderStatus.PREPARING)
                .and(w -> w.isNull(Order::getIsJointDelivery).or().eq(Order::getIsJointDelivery, 0))
                .orderByDesc(Order::getCreateTime));
    }

    // ─── Private helpers ────────────────────────────────────────

    private double scoreRider(RiderNearbyVO rider, double distanceKm) {
        double distanceScore = 1.0 - (distanceKm / MAX_DISTANCE_KM);
        double loadScore = 1.0 - (Math.min(rider.getCurrentLoad(), 5) / 5.0);
        return DISTANCE_WEIGHT * distanceScore + LOAD_WEIGHT * loadScore;
    }

    private void notifyGroupUpdate(JointDeliveryGroup group) {
        List<JointDeliveryMember> members = listMembersByGroup(group.getId());
        Map<String, Object> data = new HashMap<>();
        data.put("groupId", group.getId());
        data.put("groupNo", group.getGroupNo());
        data.put("status", group.getStatus());
        data.put("joinedRiderCount", group.getJoinedRiderCount());
        data.put("completedRiderCount", group.getCompletedRiderCount());
        data.put("requiredRiderCount", group.getRequiredRiderCount());

        WsMessage msg = new WsMessage("JOINT_DELIVERY_UPDATE", data);
        for (JointDeliveryMember m : members) {
            WebSocketServer.sendToRider(m.getRiderId(), msg);
        }
    }
}
