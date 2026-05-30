package com.waimai.api.controller;

import com.waimai.common.Result;
import com.waimai.common.dto.CreateJointDeliveryDTO;
import com.waimai.common.entity.JointDeliveryGroup;
import com.waimai.common.entity.JointDeliveryMember;
import com.waimai.common.entity.Order;
import com.waimai.common.entity.Rider;
import com.waimai.common.exception.BusinessException;
import com.waimai.common.utils.UserContext;
import com.waimai.service.service.JointDeliveryService;
import com.waimai.service.service.RiderService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/joint-delivery")
public class JointDeliveryController {

    private final JointDeliveryService jointDeliveryService;
    private final RiderService riderService;

    public JointDeliveryController(JointDeliveryService jointDeliveryService,
                                    RiderService riderService) {
        this.jointDeliveryService = jointDeliveryService;
        this.riderService = riderService;
    }

    // ─── Admin / Merchant ──────────────────────────────────────

    @PostMapping("/group/create")
    public Result<JointDeliveryGroup> createGroup(@Valid @RequestBody CreateJointDeliveryDTO dto) {
        return Result.ok(jointDeliveryService.createJointDelivery(dto.getOrderId(), dto.getRequiredRiderCount()));
    }

    @GetMapping("/group/{groupId}")
    public Result<JointDeliveryGroup> getGroup(@PathVariable Long groupId) {
        return Result.ok(jointDeliveryService.getByGroupId(groupId));
    }

    @GetMapping("/group/order/{orderId}")
    public Result<Map<String, Object>> progress(@PathVariable Long orderId) {
        return Result.ok(jointDeliveryService.getJointDeliveryProgress(orderId));
    }

    @GetMapping("/group/{groupId}/members")
    public Result<List<JointDeliveryMember>> members(@PathVariable Long groupId) {
        return Result.ok(jointDeliveryService.listMembersByGroup(groupId));
    }

    @PostMapping("/group/{groupId}/cancel")
    public Result<?> cancelGroup(@PathVariable Long groupId) {
        jointDeliveryService.cancelJointDelivery(groupId);
        return Result.ok();
    }

    @PostMapping("/group/{groupId}/dispatch")
    public Result<?> dispatch(@PathVariable Long groupId) {
        jointDeliveryService.dispatchJointDelivery(groupId);
        return Result.ok();
    }

    @GetMapping("/group/list")
    public Result<List<JointDeliveryGroup>> listGroups() {
        return Result.ok(jointDeliveryService.listAllGroups());
    }

    @GetMapping("/available-orders")
    public Result<List<Order>> availableOrders() {
        return Result.ok(jointDeliveryService.listAvailableOrders());
    }

    // ─── Rider ─────────────────────────────────────────────────

    @GetMapping("/rider/invites")
    public Result<List<JointDeliveryMember>> myInvites() {
        Rider rider = currentRider();
        return Result.ok(jointDeliveryService.listMyPendingInvites(rider.getId()));
    }

    @GetMapping("/rider/list")
    public Result<List<JointDeliveryGroup>> myJointDeliveries() {
        Rider rider = currentRider();
        return Result.ok(jointDeliveryService.listMyJointDeliveries(rider.getId()));
    }

    @GetMapping("/rider/members")
    public Result<List<JointDeliveryMember>> myMembers() {
        Rider rider = currentRider();
        return Result.ok(jointDeliveryService.listMembersByRider(rider.getId()));
    }

    @PostMapping("/rider/join/{groupId}")
    public Result<JointDeliveryMember> join(@PathVariable Long groupId) {
        Rider rider = currentRider();
        return Result.ok(jointDeliveryService.riderJoin(groupId, rider.getId()));
    }

    @PostMapping("/rider/pickup/{memberId}")
    public Result<?> pickup(@PathVariable Long memberId) {
        Rider rider = currentRider();
        jointDeliveryService.riderPickup(memberId, rider.getId());
        return Result.ok();
    }

    @PostMapping("/rider/complete/{memberId}")
    public Result<?> complete(@PathVariable Long memberId) {
        Rider rider = currentRider();
        jointDeliveryService.riderComplete(memberId, rider.getId());
        return Result.ok();
    }

    @PostMapping("/rider/cancel/{memberId}")
    public Result<?> cancelJoin(@PathVariable Long memberId) {
        Rider rider = currentRider();
        jointDeliveryService.riderCancelJoin(memberId, rider.getId());
        return Result.ok();
    }

    // ─── Helper ────────────────────────────────────────────────

    private Rider currentRider() {
        Rider r = riderService.getByOpenid(UserContext.getOpenid());
        if (r == null) throw new BusinessException("未找到骑手信息");
        return r;
    }
}
