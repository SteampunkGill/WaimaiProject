package com.waimai.service.service;

import com.waimai.common.entity.JointDeliveryGroup;
import com.waimai.common.entity.JointDeliveryMember;
import com.waimai.common.entity.Order;

import java.util.List;
import java.util.Map;

public interface JointDeliveryService {

    JointDeliveryGroup createJointDelivery(Long orderId, int requiredRiderCount);

    void cancelJointDelivery(Long groupId);

    void dispatchJointDelivery(Long groupId);

    JointDeliveryMember riderJoin(Long groupId, Long riderId);

    void riderCancelJoin(Long memberId, Long riderId);

    void riderPickup(Long memberId, Long riderId);

    void riderComplete(Long memberId, Long riderId);

    JointDeliveryGroup getByOrderId(Long orderId);

    JointDeliveryGroup getByGroupId(Long groupId);

    List<JointDeliveryMember> listMembersByGroup(Long groupId);

    List<JointDeliveryGroup> listMyJointDeliveries(Long riderId);

    List<JointDeliveryMember> listMyPendingInvites(Long riderId);

    List<JointDeliveryMember> listMembersByRider(Long riderId);

    Map<String, Object> getJointDeliveryProgress(Long orderId);

    List<JointDeliveryGroup> listAllGroups();

    List<Order> listAvailableOrders();
}
