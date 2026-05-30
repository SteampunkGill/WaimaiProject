<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast } from 'vant'
import request from '@/utils/request'

const route = useRoute()
const router = useRouter()
const order = ref<any>({})
const reviewForm = ref({ merchantRating: 5, merchantContent: '', riderRating: 5, riderContent: '' })
const showReview = ref(false)

const statusMap: any = {
  PENDING_PAYMENT: '待支付', PAID: '已支付', PREPARING: '备餐中',
  ACCEPTED: '已接单', DELIVERING: '配送中', COMPLETED: '已完成', CANCELLED: '已取消'
}

const statusStep = computed(() => {
  const s = order.value.status
  if (s === 'PENDING_PAYMENT') return 0
  if (s === 'PAID') return 1
  if (s === 'PREPARING') return 2
  if (s === 'ACCEPTED') return 3
  if (s === 'DELIVERING') return 4
  if (s === 'COMPLETED') return 5
  return -1
})

async function fetch() {
  try {
    const res: any = await request.get(`/order/${route.params.id}`)
    order.value = res.data || {}
  } catch {}
}

async function cancelOrder() {
  await request.post(`/order/${order.value.id}/cancel`)
  showToast('订单已取消')
  fetch()
}

async function payOrder() {
  await request.post(`/order/${order.value.id}/pay`)
  showToast('支付成功')
  fetch()
}

async function submitReview() {
  await request.post('/review/submit', {
    orderId: order.value.id,
    merchantRating: reviewForm.value.merchantRating,
    merchantContent: reviewForm.value.merchantContent,
    riderRating: order.value.riderId ? reviewForm.value.riderRating : null,
    riderContent: order.value.riderId ? reviewForm.value.riderContent : null
  })
  showToast('评价成功')
  showReview.value = false
  fetch()
}

onMounted(fetch)
</script>

<template>
  <div class="detail-page">
    <van-nav-bar title="订单详情" left-arrow @click-left="router.back()" fixed placeholder />

    <div class="status-section">
      <van-steps :active="statusStep" direction="horizontal" inactive-icon="clock-o" active-icon="checked">
        <van-step>已下单</van-step>
        <van-step>已支付</van-step>
        <van-step>备餐中</van-step>
        <van-step>已接单</van-step>
        <van-step>配送中</van-step>
        <van-step>已完成</van-step>
      </van-steps>
    </div>

    <div class="status-bar">
      <van-tag :type="order.status === 'COMPLETED' ? 'success' : order.status === 'CANCELLED' ? 'danger' : 'primary'" size="medium">
        {{ statusMap[order.status] || order.status }}
      </van-tag>
      <span v-if="order.status === 'DELIVERING' && order.estimatedMinutes" class="eta">
        <van-icon name="clock-o" /> 预计 {{ order.estimatedMinutes }} 分钟送达
      </span>
      <span v-if="order.isOvertime" class="overtime">
        <van-icon name="warning-o" /> 已超时
      </span>
    </div>

    <van-cell-group inset title="订单信息">
      <van-cell title="订单编号" :value="order.orderNo" />
      <van-cell title="商家" :value="order.merchantName" />
      <van-cell title="收货地址" :value="order.address" label="请保持电话畅通" />
      <van-cell v-if="order.remark" title="备注" :value="order.remark" />
    </van-cell-group>

    <div class="items-section">
      <div class="section-title">商品明细</div>
      <div v-for="d in order.details" :key="d.dishId" class="detail-item">
        <van-image v-if="d.dishImage" :src="d.dishImage" width="40" height="40" radius="4" />
        <div class="item-info">
          <span class="item-name">{{ d.dishName }}</span>
          <span class="item-qty">x{{ d.quantity }}</span>
        </div>
        <span class="item-price">¥{{ (d.price * d.quantity).toFixed(2) }}</span>
      </div>
    </div>

    <van-cell-group inset title="费用明细">
      <van-cell title="商品总额" :value="'¥' + ((order.totalAmount || 0)).toFixed(2)" />
      <van-cell title="配送费" :value="'¥' + ((order.deliveryFee || 0)).toFixed(2)" />
      <van-cell v-if="order.discountAmount" title="优惠" :value="'-¥' + ((order.discountAmount || 0)).toFixed(2)" />
      <van-cell title="实付">
        <template #value>
          <span class="pay-amount">¥{{ (order.payAmount || 0).toFixed(2) }}</span>
        </template>
      </van-cell>
    </van-cell-group>

    <van-cell-group v-if="order.isJointDelivery && order.jointDelivery" inset title="联合配送">
      <van-cell title="配送骑手" :value="`${order.jointDelivery.joinedRiderCount}/${order.jointDelivery.requiredRiderCount}`" />
      <div v-for="m in order.jointDelivery.members" :key="m.id" class="joint-rider-row">
        <span class="joint-rider-name">{{ m.riderName || '骑手#' + m.riderId }}</span>
        <van-tag :type="m.status === 'COMPLETED' ? 'success' : 'primary'" size="small">
          {{ m.status === 'INVITED' ? '已邀请' : m.status === 'JOINED' ? '已加入' : m.status === 'PICKED_UP' ? '已取餐' : m.status === 'COMPLETED' ? '已完成' : m.status }}
        </van-tag>
      </div>
    </van-cell-group>

    <van-cell-group v-if="order.payTime" inset title="时间信息">
      <van-cell title="支付时间" :value="order.payTime?.substring(0, 16)" />
      <van-cell title="下单时间" :value="order.createTime?.substring(0, 16)" />
    </van-cell-group>

    <div class="actions">
      <van-button v-if="order.status === 'PENDING_PAYMENT'" type="danger" round @click="cancelOrder">取消订单</van-button>
      <van-button v-if="order.status === 'PENDING_PAYMENT'" type="primary" round @click="payOrder">立即支付</van-button>
      <van-button v-if="order.status === 'COMPLETED' && !showReview" type="primary" round @click="showReview = true">评价</van-button>
    </div>

    <div v-if="showReview" class="review-form">
      <div class="review-card">
        <h4>评价商家 · {{ order.merchantName }}</h4>
        <van-rate v-model="reviewForm.merchantRating" :color="'#ffc800'" />
        <van-field v-model="reviewForm.merchantContent" placeholder="分享您的用餐体验吧" type="textarea" rows="2" />
      </div>
      <div v-if="order.riderId" class="review-card">
        <h4>评价骑手</h4>
        <van-rate v-model="reviewForm.riderRating" :color="'#ffc800'" />
        <van-field v-model="reviewForm.riderContent" placeholder="对骑手说点什么吧" type="textarea" rows="2" />
      </div>
      <van-button type="primary" round block @click="submitReview">提交评价</van-button>
    </div>
  </div>
</template>

<style scoped>
.detail-page { padding-bottom: 80px; }
.status-section { padding: 12px; background: #fff; margin-bottom: 8px; }
.status-bar { padding: 12px 16px; background: #fff; display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
.eta { color: #409EFF; font-size: 13px; display: flex; align-items: center; gap: 4px; }
.overtime { color: #ee0a24; font-size: 13px; font-weight: 600; display: flex; align-items: center; gap: 4px; }

.items-section { background: #fff; margin-top: 8px; padding: 0 16px 12px; }
.section-title { font-size: 14px; font-weight: 600; padding: 12px 0 8px; }
.detail-item { display: flex; align-items: center; gap: 10px; padding: 8px 0; border-bottom: 1px solid #f5f5f5; }
.item-info { flex: 1; }
.item-name { font-size: 13px; display: block; }
.item-qty { font-size: 11px; color: #999; }
.item-price { font-size: 14px; font-weight: 500; color: #333; }
.pay-amount { color: #ee0a24; font-weight: 700; font-size: 16px; }

.actions { display: flex; gap: 10px; justify-content: flex-end; padding: 16px; }

.joint-rider-row { display: flex; justify-content: space-between; align-items: center; padding: 8px 16px; border-top: 1px solid #f5f5f5; }
.joint-rider-name { font-size: 13px; color: #333; }

.review-form { padding: 12px 16px; }
.review-card { background: #fff; border-radius: 8px; padding: 16px; margin-bottom: 12px; }
.review-card h4 { font-size: 14px; margin-bottom: 8px; }
</style>
