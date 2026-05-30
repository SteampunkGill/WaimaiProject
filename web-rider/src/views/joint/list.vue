<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { showSuccessToast, showFailToast } from 'vant'
import request from '@/utils/request'

const activeTab = ref(0)
const invites = ref<any[]>([])
const myGroups = ref<any[]>([])
const groupMembers = ref<Record<number, any[]>>({})
const loading = ref(false)
const error = ref('')
const riderId = ref(0)

const memberStatusLabel: Record<string, string> = {
  INVITED: '已邀请', JOINED: '已加入', PICKED_UP: '已取餐',
  COMPLETED: '已完成', CANCELLED: '已取消'
}

const groupStatusLabel: Record<string, string> = {
  RECRUITING: '招募中', READY: '已就绪', DELIVERING: '配送中',
  COMPLETED: '已完成', CANCELLED: '已取消'
}

async function fetchData() {
  loading.value = true
  error.value = ''
  try {
    const info: any = await request.get('/rider/info')
    riderId.value = info?.data?.id || 0

    const invRes: any = await request.get('/joint-delivery/rider/invites')
    invites.value = invRes.data || []

    const grpRes: any = await request.get('/joint-delivery/rider/list')
    myGroups.value = grpRes.data || []

    for (const g of myGroups.value) {
      try {
        const memRes: any = await request.get(`/joint-delivery/group/${g.id}/members`)
        groupMembers.value[g.id] = memRes.data || []
      } catch { groupMembers.value[g.id] = [] }
    }
  } catch (e: any) {
    error.value = e?.response?.data?.msg || '加载失败，请下拉刷新重试'
  }
  loading.value = false
}

async function joinGroup(groupId: number) {
  try {
    await request.post(`/joint-delivery/rider/join/${groupId}`)
    showSuccessToast('已加入联合配送')
    fetchData()
  } catch (e: any) {
    showFailToast(e?.response?.data?.msg || '加入失败')
  }
}

async function cancelInvite(memberId: number) {
  try {
    await request.post(`/joint-delivery/rider/cancel/${memberId}`)
    showSuccessToast('已拒绝')
    fetchData()
  } catch { showFailToast('操作失败') }
}

async function pickupTask(memberId: number) {
  try {
    await request.post(`/joint-delivery/rider/pickup/${memberId}`)
    showSuccessToast('已取餐')
    fetchData()
  } catch (e: any) {
    showFailToast(e?.response?.data?.msg || '取餐失败')
  }
}

async function completeTask(memberId: number) {
  try {
    await request.post(`/joint-delivery/rider/complete/${memberId}`)
    showSuccessToast('配送完成')
    fetchData()
  } catch (e: any) {
    showFailToast(e?.response?.data?.msg || '完成失败')
  }
}

function findMyMember(groupId: number): any {
  const members = groupMembers.value[groupId] || []
  return members.find((m: any) => m.riderId === riderId.value)
}

onMounted(fetchData)
</script>

<template>
  <div class="joint-page">
    <van-nav-bar title="联合配送" fixed placeholder />

    <van-tabs v-model:active="activeTab" sticky>
      <van-tab title="邀请">
        <van-pull-refresh v-model="loading" @refresh="fetchData">
          <div v-if="error" class="error-msg">{{ error }}</div>
          <div v-for="inv in invites" :key="inv.id" class="invite-card">
            <div class="card-header">
              <van-tag type="warning" size="small">联合配送邀请</van-tag>
              <span class="group-no">#{{ inv.groupId }}</span>
            </div>
            <div class="card-body">
              <div class="info-row">订单ID: {{ inv.orderId }}</div>
            </div>
            <div class="card-actions">
              <van-button size="small" plain type="danger" @click="cancelInvite(inv.id)">拒绝</van-button>
              <van-button size="small" type="primary" @click="joinGroup(inv.groupId)">接受</van-button>
            </div>
          </div>
          <van-empty v-if="!loading && !error && invites.length === 0" description="暂无联合配送邀请" />
        </van-pull-refresh>
      </van-tab>

      <van-tab title="进行中">
        <van-pull-refresh v-model="loading" @refresh="fetchData">
          <div v-if="error" class="error-msg">{{ error }}</div>
          <div v-for="g in myGroups" :key="g.id" class="group-card">
            <div class="card-header">
              <van-tag :type="g.status === 'COMPLETED' ? 'success' : g.status === 'DELIVERING' ? 'primary' : 'warning'" size="small">
                {{ groupStatusLabel[g.status] || g.status }}
              </van-tag>
              <span class="group-no">#{{ g.groupNo }}</span>
            </div>
            <div class="progress-bar">
              <van-progress :percentage="g.requiredRiderCount > 0 ? Math.round(g.completedRiderCount / g.requiredRiderCount * 100) : 0"
                stroke-color="#1989fa" />
              <span class="progress-text">{{ g.completedRiderCount }} / {{ g.requiredRiderCount }}</span>
            </div>

            <div class="member-list">
              <div v-for="m in (groupMembers[g.id] || [])" :key="m.id" class="member-row">
                <span class="member-rider">{{ m.riderName || '骑手 #' + m.riderId }}</span>
                <van-tag :type="m.status === 'COMPLETED' ? 'success' : m.status === 'CANCELLED' ? 'default' : 'primary'"
                  size="small">
                  {{ memberStatusLabel[m.status] || m.status }}
                </van-tag>
              </div>
            </div>

            <div class="my-actions" v-if="g.status !== 'COMPLETED' && g.status !== 'CANCELLED'">
              <template v-if="findMyMember(g.id)">
                <van-button v-if="findMyMember(g.id).status === 'JOINED'"
                  type="primary" size="small" round @click="pickupTask(findMyMember(g.id).id)">取餐</van-button>
                <van-button v-if="findMyMember(g.id).status === 'PICKED_UP'"
                  type="success" size="small" round @click="completeTask(findMyMember(g.id).id)">完成配送</van-button>
              </template>
            </div>
          </div>
          <van-empty v-if="!loading && !error && myGroups.length === 0" description="暂无进行中的联合配送" />
        </van-pull-refresh>
      </van-tab>
    </van-tabs>

    <van-tabbar route>
      <van-tabbar-item to="/" icon="home-o">大厅</van-tabbar-item>
      <van-tabbar-item to="/tasks" icon="todo-list-o">任务</van-tabbar-item>
      <van-tabbar-item to="/income" icon="gold-coin-o">收入</van-tabbar-item>
    </van-tabbar>
  </div>
</template>

<style scoped>
.joint-page { padding-bottom: 50px; }

.invite-card, .group-card {
  background: #fff; margin: 8px; padding: 12px; border-radius: 8px;
}
.card-header { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
.group-no { font-size: 12px; color: #999; }
.card-body { margin-bottom: 8px; }
.info-row { font-size: 13px; color: #666; margin: 2px 0; }
.card-actions { display: flex; justify-content: flex-end; gap: 8px; }

.progress-bar { display: flex; align-items: center; gap: 8px; margin: 8px 0; }
.progress-bar :deep(.van-progress) { flex: 1; }
.progress-text { font-size: 12px; color: #666; min-width: 40px; }

.member-list { margin: 8px 0; }
.member-row { display: flex; justify-content: space-between; align-items: center; padding: 6px 0; border-bottom: 1px solid #f5f5f5; }
.member-rider { font-size: 13px; }

.my-actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 8px; padding-top: 8px; border-top: 1px solid #eee; }

.error-msg { padding: 20px; text-align: center; color: #ee0a24; font-size: 14px; }
</style>
