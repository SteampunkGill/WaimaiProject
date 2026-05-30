package com.waimai.api.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.waimai.service.push.OrderPushService;
import com.waimai.common.Result;
import com.waimai.common.constant.OrderStatus;
import com.waimai.common.entity.DeliveryTrack;
import com.waimai.common.entity.Order;
import com.waimai.common.entity.Rider;
import com.waimai.common.entity.RiderIncome;
import com.waimai.common.entity.RiderWithdrawal;
import com.waimai.common.exception.BusinessException;
import com.waimai.common.utils.UserContext;
import com.waimai.service.mapper.DeliveryTrackMapper;
import com.waimai.service.mapper.OrderMapper;
import com.waimai.service.service.DirectionService;
import com.waimai.service.service.DispatchService;
import com.waimai.service.service.GeoService;
import com.waimai.service.service.OrderService;
import com.waimai.service.service.RiderIncomeService;
import com.waimai.service.service.RiderService;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rider")
public class RiderController {

    private final RiderService riderService;
    private final GeoService geoService;
    private final OrderPushService orderPushService;
    private final OrderService orderService;
    private final OrderMapper orderMapper;
    private final DispatchService dispatchService;
    private final RiderIncomeService riderIncomeService;
    private final DeliveryTrackMapper deliveryTrackMapper;
    private final DirectionService directionService;

    public RiderController(RiderService riderService, GeoService geoService,
                           OrderPushService orderPushService, OrderService orderService,
                           OrderMapper orderMapper, DispatchService dispatchService,
                           RiderIncomeService riderIncomeService,
                           DeliveryTrackMapper deliveryTrackMapper,
                           DirectionService directionService) {
        this.riderService = riderService;
        this.geoService = geoService;
        this.orderPushService = orderPushService;
        this.orderService = orderService;
        this.orderMapper = orderMapper;
        this.dispatchService = dispatchService;
        this.riderIncomeService = riderIncomeService;
        this.deliveryTrackMapper = deliveryTrackMapper;
        this.directionService = directionService;
    }

    @PostMapping("/register")
    public Result<?> register(@RequestBody Rider rider) {
        rider.setOpenid(UserContext.getOpenid());
        riderService.register(rider);
        return Result.ok();
    }

    @GetMapping("/info")
    public Result<Rider> info() {
        return Result.ok(currentRider());
    }

    @PostMapping("/location")
    public Result<?> updateLocation(@RequestBody Map<String, Double> body) {
        double lng = body.get("longitude");
        double lat = body.get("latitude");
        Rider rider = currentRider();
        riderService.updateLocation(rider.getId(), lng, lat);
        geoService.addRiderLocation(rider.getId(), lng, lat);
        orderPushService.pushRiderLocationToUser(rider.getId(), lng, lat);
        return Result.ok();
    }

    @PostMapping("/online")
    public Result<?> goOnline() {
        Rider rider = currentRider();
        riderService.goOnline(rider.getId());
        if (rider.getCurrentLng() != null && rider.getCurrentLat() != null) {
            geoService.addRiderLocation(rider.getId(),
                    rider.getCurrentLng().doubleValue(), rider.getCurrentLat().doubleValue());
        }
        return Result.ok();
    }

    @PostMapping("/offline")
    public Result<?> goOffline() {
        Rider rider = currentRider();
        riderService.goOffline(rider.getId());
        geoService.removeRiderLocation(rider.getId());
        return Result.ok();
    }

    // ─── Order endpoints ───────────────────────────────────────────

    @GetMapping("/order/pending")
    public Result<List<Order>> pendingOrders() {
        List<Order> list = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .eq(Order::getStatus, OrderStatus.PREPARING)
                .isNull(Order::getRiderId)
                .and(w -> w.isNull(Order::getIsJointDelivery).or().eq(Order::getIsJointDelivery, 0))
                .orderByDesc(Order::getCreateTime));
        return Result.ok(list);
    }

    @PostMapping("/order/{orderNo}/accept")
    public Result<?> acceptByOrderNo(@PathVariable String orderNo) {
        Order order = orderService.getByOrderNo(orderNo);
        if (order == null) throw new BusinessException("订单不存在");
        Rider rider = currentRider();
        riderService.acceptTask(rider.getId(), order.getId());
        return Result.ok();
    }

    @PostMapping("/order/{orderNo}/pickup")
    public Result<?> pickupByOrderNo(@PathVariable String orderNo) {
        Order order = orderService.getByOrderNo(orderNo);
        if (order == null) throw new BusinessException("订单不存在");
        Rider rider = currentRider();
        riderService.pickupTask(rider.getId(), order.getId());
        return Result.ok();
    }

    @PostMapping("/order/{orderNo}/complete")
    public Result<?> completeByOrderNo(@PathVariable String orderNo) {
        Order order = orderService.getByOrderNo(orderNo);
        if (order == null) throw new BusinessException("订单不存在");
        Rider rider = currentRider();
        riderService.completeTask(rider.getId(), order.getId());
        return Result.ok();
    }

    @GetMapping("/order/list")
    public Result<List<Order>> myDeliveries(@RequestParam(defaultValue = "1") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        Rider rider = currentRider();
        List<Order> list = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .eq(Order::getRiderId, rider.getId())
                .orderByDesc(Order::getCreateTime)
                .last("LIMIT " + ((page - 1) * size) + ", " + size));
        return Result.ok(list);
    }

    // ─── Legacy accept/complete by orderId ─

    @PostMapping("/accept/{orderId}")
    public Result<?> accept(@PathVariable Long orderId) {
        Rider rider = currentRider();
        riderService.acceptTask(rider.getId(), orderId);
        return Result.ok();
    }

    @PostMapping("/complete/{orderId}")
    public Result<?> complete(@PathVariable Long orderId) {
        Rider rider = currentRider();
        riderService.completeTask(rider.getId(), orderId);
        return Result.ok();
    }

    // ─── Delivery Track ────────────────────────────────────────────

    @PostMapping("/track/report")
    public Result<?> reportTrack(@RequestBody Map<String, Object> body) {
        Long orderId = Long.valueOf(body.get("orderId").toString());
        double lng = Double.parseDouble(body.get("longitude").toString());
        double lat = Double.parseDouble(body.get("latitude").toString());
        Rider rider = currentRider();
        Order order = orderService.getById(orderId);
        if (order == null || !rider.getId().equals(order.getRiderId())) {
            throw new BusinessException("订单不存在或未分配给您");
        }
        if (!OrderStatus.DELIVERING.equals(order.getStatus())
                && !OrderStatus.ACCEPTED.equals(order.getStatus())) {
            throw new BusinessException("当前状态不允许上报轨迹");
        }
        DeliveryTrack track = new DeliveryTrack();
        track.setOrderId(orderId);
        track.setRiderId(rider.getId());
        track.setLongitude(BigDecimal.valueOf(lng));
        track.setLatitude(BigDecimal.valueOf(lat));
        deliveryTrackMapper.insert(track);
        return Result.ok();
    }

    @GetMapping("/track/{orderId}")
    public Result<List<DeliveryTrack>> getTrack(@PathVariable Long orderId) {
        return Result.ok(riderService.getTracks(orderId));
    }

    @GetMapping("/level")
    public Result<Map<String, Object>> myLevel() {
        Rider rider = currentRider();
        Map<String, Object> data = new HashMap<>();
        data.put("level", rider.getLevel());
        data.put("levelScore", rider.getLevelScore());
        data.put("totalOrders", rider.getTotalOrders());
        data.put("score", rider.getScore() != null ? rider.getScore() : BigDecimal.valueOf(5.0));
        return Result.ok(data);
    }

    // ─── Income & Withdrawal ────────────────────────────────────────

    @GetMapping("/income/summary")
    public Result<Map<String, Object>> incomeSummary() {
        return Result.ok(riderIncomeService.getIncomeSummary(currentRider().getId()));
    }

    @GetMapping("/income/list")
    public Result<List<RiderIncome>> incomeList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(riderIncomeService.listIncome(currentRider().getId(), page, size));
    }

    @PostMapping("/withdrawal")
    public Result<Map<String, Object>> requestWithdrawal(@RequestBody Map<String, Object> body) {
        BigDecimal amount = new BigDecimal(body.get("amount").toString());
        RiderWithdrawal w = riderIncomeService.requestWithdrawal(currentRider().getId(), amount);
        Map<String, Object> result = new HashMap<>();
        result.put("id", w.getId());
        result.put("amount", w.getAmount());
        result.put("status", w.getStatus());
        return Result.ok(result);
    }

    @GetMapping("/withdrawal/list")
    public Result<List<RiderWithdrawal>> withdrawalList() {
        return Result.ok(riderIncomeService.listWithdrawals(currentRider().getId()));
    }

    // ─── Direction (proxy Amap driving API) ────────────────────────

    @GetMapping("/direction/driving")
    public Result<String> drivingDirection(
            @RequestParam String origin,
            @RequestParam String destination) {
        return Result.ok(directionService.getDrivingRoute(origin, destination));
    }

    // ─── Helper ────────────────────────────────────────────────────

    private Rider currentRider() {
        Rider r = riderService.getByOpenid(UserContext.getOpenid());
        if (r == null) throw new BusinessException("未找到骑手信息");
        if (r.getScore() == null) r.setScore(BigDecimal.valueOf(5.0));
        return r;
    }
}
