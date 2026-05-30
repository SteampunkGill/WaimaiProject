package com.waimai.api.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.waimai.common.Result;
import com.waimai.common.dto.PlaceOrderDTO;
import com.waimai.common.entity.JointDeliveryGroup;
import com.waimai.common.entity.JointDeliveryMember;
import com.waimai.common.entity.Order;
import com.waimai.common.entity.OrderDetail;
import com.waimai.common.utils.UserContext;
import com.waimai.common.vo.OrderDetailVO;
import com.waimai.common.vo.OrderVO;
import com.waimai.service.mapper.OrderDetailMapper;
import com.waimai.service.service.MerchantService;
import com.waimai.common.dto.OrderItemDTO;
import com.waimai.common.vo.JointDeliveryGroupVO;
import com.waimai.common.vo.JointDeliveryMemberVO;
import com.waimai.service.service.JointDeliveryService;
import com.waimai.service.service.OrderService;
import com.waimai.service.service.DispatchService;
import com.waimai.service.service.PaymentService;
import com.waimai.service.service.RiderService;
import com.waimai.common.entity.DeliveryTrack;
import com.waimai.service.mapper.DeliveryTrackMapper;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/order")
public class OrderController {

    private final OrderService orderService;
    private final OrderDetailMapper orderDetailMapper;
    private final MerchantService merchantService;
    private final PaymentService paymentService;
    private final DispatchService dispatchService;
    private final RiderService riderService;
    private final JointDeliveryService jointDeliveryService;

    public OrderController(OrderService orderService, OrderDetailMapper orderDetailMapper,
                           MerchantService merchantService, PaymentService paymentService,
                           DispatchService dispatchService, RiderService riderService,
                           JointDeliveryService jointDeliveryService) {
        this.orderService = orderService;
        this.orderDetailMapper = orderDetailMapper;
        this.merchantService = merchantService;
        this.paymentService = paymentService;
        this.dispatchService = dispatchService;
        this.riderService = riderService;
        this.jointDeliveryService = jointDeliveryService;
    }

    @PostMapping("/place")
    public Result<OrderVO> placeOrder(@Valid @RequestBody PlaceOrderDTO dto) {
        List<OrderItemDTO> items = dto.getItems().stream().map(i -> {
            OrderItemDTO item = new OrderItemDTO();
            item.setDishId(i.getDishId());
            item.setQuantity(i.getQuantity());
            return item;
        }).collect(Collectors.toList());

        Order order = orderService.placeOrder(
                UserContext.getUserId(), dto.getMerchantId(),
                dto.getAddress(),
                dto.getAddressLng() != null ? dto.getAddressLng().doubleValue() : 0,
                dto.getAddressLat() != null ? dto.getAddressLat().doubleValue() : 0,
                dto.getRemark(), items, dto.getCouponId());

        return Result.ok(toVO(order));
    }

    @PostMapping("/{orderId}/pay")
    public Result<Map<String, Object>> pay(@PathVariable Long orderId) {
        String payNo = paymentService.mockPay(orderId);
        Map<String, Object> data = new HashMap<>();
        data.put("payNo", payNo);
        data.put("status", "SUCCESS");
        return Result.ok(data);
    }

    @PostMapping("/{orderId}/cancel")
    public Result<?> cancel(@PathVariable Long orderId) {
        orderService.cancelOrder(orderId, UserContext.getUserId());
        return Result.ok();
    }

    @PostMapping("/{orderId}/dispatch")
    public Result<Long> dispatch(@PathVariable Long orderId) {
        Long riderId = dispatchService.dispatchOrder(orderId);
        if (riderId == null) return Result.fail("附近无可用骑手");
        return Result.ok(riderId);
    }

    @GetMapping("/{orderNo}/eta")
    public Result<Map<String, Object>> eta(@PathVariable String orderNo) {
        Order order = orderService.getByOrderNo(orderNo);
        if (order == null) return Result.fail("订单不存在");
        Map<String, Object> data = new HashMap<>();
        data.put("estimatedMinutes", order.getEstimatedMinutes());
        data.put("isOvertime", order.getIsOvertime() != null && order.getIsOvertime() == 1);
        return Result.ok(data);
    }

    @GetMapping("/{orderNo}")
    public Result<OrderVO> detail(@PathVariable String orderNo) {
        Order order = orderService.getByOrderNo(orderNo);
        if (order == null) return Result.fail("订单不存在");
        return Result.ok(toVO(order));
    }

    @GetMapping("/{orderId}/track")
    public Result<List<DeliveryTrack>> getTrack(@PathVariable Long orderId) {
        return Result.ok(riderService.getTracks(orderId));
    }

    @GetMapping("/list")
    public Result<Page<OrderVO>> list(@RequestParam(defaultValue = "1") int page,
                                       @RequestParam(defaultValue = "10") int size) {
        Page<Order> orderPage = orderService.listByUser(UserContext.getUserId(), page, size);
        Page<OrderVO> voPage = new Page<>(page, size, orderPage.getTotal());
        voPage.setRecords(orderPage.getRecords().stream().map(this::toVO).collect(Collectors.toList()));
        return Result.ok(voPage);
    }

    private OrderVO toVO(Order order) {
        OrderVO vo = new OrderVO();
        vo.setId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setStatus(order.getStatus());
        vo.setTotalAmount(order.getTotalAmount());
        vo.setDeliveryFee(order.getDeliveryFee());
        vo.setDiscountAmount(order.getDiscountAmount());
        vo.setPayAmount(order.getPayAmount());
        vo.setAddress(order.getAddress());
        vo.setAddressLng(order.getAddressLng());
        vo.setAddressLat(order.getAddressLat());
        vo.setRemark(order.getRemark());
        vo.setEstimatedMinutes(order.getEstimatedMinutes());
        vo.setMerchantId(order.getMerchantId());
        vo.setRiderId(order.getRiderId());
        vo.setIsJointDelivery(order.getIsJointDelivery());
        vo.setPayTime(order.getPayTime());
        vo.setCreateTime(order.getCreateTime());

        if (order.getIsJointDelivery() != null && order.getIsJointDelivery() == 1) {
            JointDeliveryGroup group = jointDeliveryService.getByOrderId(order.getId());
            if (group != null) {
                JointDeliveryGroupVO gvo = new JointDeliveryGroupVO();
                gvo.setId(group.getId());
                gvo.setGroupNo(group.getGroupNo());
                gvo.setRequiredRiderCount(group.getRequiredRiderCount());
                gvo.setJoinedRiderCount(group.getJoinedRiderCount());
                gvo.setCompletedRiderCount(group.getCompletedRiderCount());
                gvo.setStatus(group.getStatus());
                gvo.setDeliveryFeeTotal(group.getDeliveryFeeTotal());
                gvo.setCreateTime(group.getCreateTime());

                List<JointDeliveryMember> members = jointDeliveryService.listMembersByGroup(group.getId());
                List<JointDeliveryMemberVO> mvos = new java.util.ArrayList<>();
                for (JointDeliveryMember m : members) {
                    JointDeliveryMemberVO mv = new JointDeliveryMemberVO();
                    mv.setId(m.getId());
                    mv.setRiderId(m.getRiderId());
                    mv.setStatus(m.getStatus());
                    mv.setEarnings(m.getEarnings());
                    mv.setJoinTime(m.getJoinTime());
                    mv.setPickupTime(m.getPickupTime());
                    mv.setCompleteTime(m.getCompleteTime());
                    com.waimai.common.entity.Rider rider = riderService.getById(m.getRiderId());
                    if (rider != null) {
                        mv.setRiderName(rider.getRealName());
                        mv.setRiderPhone(rider.getPhone());
                    }
                    mvos.add(mv);
                }
                gvo.setMembers(mvos);
                vo.setJointDelivery(gvo);
            }
        }

        var merchant = merchantService.getById(order.getMerchantId());
        if (merchant != null) vo.setMerchantName(merchant.getName());

        List<OrderDetail> details = orderDetailMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<OrderDetail>()
                        .eq(OrderDetail::getOrderId, order.getId()));
        vo.setDetails(details.stream().map(d -> {
            OrderDetailVO dv = new OrderDetailVO();
            dv.setDishId(d.getDishId());
            dv.setDishName(d.getDishName());
            dv.setDishImage(d.getDishImage());
            dv.setPrice(d.getPrice());
            dv.setQuantity(d.getQuantity());
            return dv;
        }).collect(Collectors.toList()));

        return vo;
    }
}
