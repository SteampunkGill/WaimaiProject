<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'

const groups = ref<any[]>([])
const availableOrders = ref<any[]>([])
const members = ref<any[]>([])
const loading = ref(false)
const createVisible = ref(false)
const detailVisible = ref(false)
const detailGroup = ref<any>(null)
const createForm = ref({ orderId: null as number | null, requiredRiderCount: 2 })

const statusTag: Record<string, string> = {
  RECRUITING: 'warning', READY: 'success', DELIVERING: 'primary',
  COMPLETED: 'success', CANCELLED: 'info'
}
const statusLabel: Record<string, string> = {
  RECRUITING: '招募中', READY: '已就绪', DELIVERING: '配送中',
  COMPLETED: '已完成', CANCELLED: '已取消'
}
const memberStatusLabel: Record<string, string> = {
  INVITED: '已邀请', JOINED: '已加入', PICKED_UP: '已取餐',
  COMPLETED: '已完成', CANCELLED: '已取消'
}

async function fetchGroups() {
  loading.value = true
  try {
    const res: any = await request.get('/joint-delivery/group/list')
    groups.value = res.data || []
  } catch { groups.value = [] }
  loading.value = false
}

async function fetchAvailableOrders() {
  try {
    const res: any = await request.get('/joint-delivery/available-orders')
    availableOrders.value = res.data || []
  } catch { availableOrders.value = [] }
}

async function doCreate() {
  if (!createForm.value.orderId || createForm.value.requiredRiderCount < 2) {
    ElMessage.warning('请选择订单并设置骑手数（至少2人）')
    return
  }
  try {
    await request.post('/joint-delivery/group/create', {
      orderId: createForm.value.orderId,
      requiredRiderCount: createForm.value.requiredRiderCount
    })
    ElMessage.success('联合配送组已创建')
    createVisible.value = false
    createForm.value = { orderId: null, requiredRiderCount: 2 }
    fetchGroups()
    fetchAvailableOrders()
  } catch { ElMessage.error('创建失败') }
}

async function viewDetail(group: any) {
  detailGroup.value = group
  try {
    const res: any = await request.get(`/joint-delivery/group/order/${group.orderId}`)
    members.value = res.data?.members || []
  } catch { members.value = [] }
  detailVisible.value = true
}

async function cancelGroup() {
  if (!detailGroup.value) return
  try {
    await request.post(`/joint-delivery/group/${detailGroup.value.id}/cancel`)
    ElMessage.success('已取消')
    detailVisible.value = false
    fetchGroups()
  } catch { ElMessage.error('取消失败') }
}

async function reDispatch() {
  if (!detailGroup.value) return
  try {
    await request.post(`/joint-delivery/group/${detailGroup.value.id}/dispatch`)
    ElMessage.success('已重新派单')
    fetchGroups()
  } catch { ElMessage.error('派单失败') }
}

function openCreate() {
  createForm.value = { orderId: null, requiredRiderCount: 2 }
  fetchAvailableOrders()
  createVisible.value = true
}

onMounted(fetchGroups)
</script>

<template>
  <div class="joint-page">
    <div class="page-header">
      <h2>多骑手联合配送</h2>
      <el-button type="primary" @click="openCreate">创建联合配送</el-button>
    </div>

    <el-table :data="groups" v-loading="loading" stripe empty-text="暂无联合配送记录">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="orderId" label="订单ID" width="100" />
      <el-table-column prop="groupNo" label="编号" width="160" />
      <el-table-column label="骑手进度" width="150">
        <template #default="{ row }">
          已加入 {{ row.joinedRiderCount }} / 需 {{ row.requiredRiderCount }} &nbsp; 完成 {{ row.completedRiderCount }}
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="statusTag[row.status]">{{ statusLabel[row.status] || row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="总配送费" width="100">
        <template #default="{ row }">¥{{ (row.deliveryFeeTotal || 0).toFixed(2) }}</template>
      </el-table-column>
      <el-table-column label="进度" min-width="120">
        <template #default="{ row }">
          <el-progress :percentage="row.requiredRiderCount > 0 ? Math.round(row.completedRiderCount / row.requiredRiderCount * 100) : 0"
            :status="row.status === 'COMPLETED' ? 'success' : undefined" />
        </template>
      </el-table-column>
      <el-table-column label="创建时间" width="140">
        <template #default="{ row }">{{ row.createTime?.substring(0, 16) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="80" fixed="right">
        <template #default="{ row }">
          <el-button size="small" link type="primary" @click="viewDetail(row)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- Create Dialog -->
    <el-dialog v-model="createVisible" title="创建联合配送组" width="480px">
      <el-form :model="createForm" label-width="100px">
        <el-form-item label="选择订单">
          <el-select v-model="createForm.orderId" placeholder="选择备餐中的订单" filterable style="width:100%">
            <el-option v-for="o in availableOrders" :key="o.id"
              :label="`#${o.id} ${o.orderNo?.substring(o.orderNo.length - 10)} - ${o.address}`"
              :value="o.id" />
          </el-select>
          <div v-if="availableOrders.length === 0" style="color:#999;font-size:12px;margin-top:4px">
            暂无可用的备餐中订单
          </div>
        </el-form-item>
        <el-form-item label="所需骑手数">
          <el-input-number v-model="createForm.requiredRiderCount" :min="2" :max="10" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" @click="doCreate" :disabled="!createForm.orderId">确认创建</el-button>
      </template>
    </el-dialog>

    <!-- Detail Dialog -->
    <el-dialog v-model="detailVisible" title="联合配送组详情" width="700px">
      <template v-if="detailGroup">
        <el-descriptions :column="3" border size="small">
          <el-descriptions-item label="组ID">{{ detailGroup.id }}</el-descriptions-item>
          <el-descriptions-item label="编号">{{ detailGroup.groupNo }}</el-descriptions-item>
          <el-descriptions-item label="订单ID">{{ detailGroup.orderId }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusTag[detailGroup.status]">{{ statusLabel[detailGroup.status] || detailGroup.status }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="进度">{{ detailGroup.completedRiderCount }} / {{ detailGroup.requiredRiderCount }}</el-descriptions-item>
          <el-descriptions-item label="总配送费">¥{{ (detailGroup.deliveryFeeTotal || 0).toFixed(2) }}</el-descriptions-item>
        </el-descriptions>

        <el-table :data="members" style="margin-top:16px" stripe size="small">
          <el-table-column prop="riderId" label="骑手ID" width="80" />
          <el-table-column prop="riderName" label="姓名" width="100" />
          <el-table-column prop="riderPhone" label="电话" width="130" />
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="row.status === 'COMPLETED' ? 'success' : row.status === 'CANCELLED' ? 'info' : 'warning'" size="small">
                {{ memberStatusLabel[row.status] || row.status }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="收益" width="90">
            <template #default="{ row }">¥{{ (row.earnings || 0).toFixed(2) }}</template>
          </el-table-column>
          <el-table-column label="加入时间" width="140">
            <template #default="{ row }">{{ row.joinTime?.substring(0, 16) || '-' }}</template>
          </el-table-column>
        </el-table>
      </template>

      <div v-if="detailGroup && detailGroup.status === 'RECRUITING'" style="margin-top:16px;text-align:right">
        <el-button @click="reDispatch">重新派单</el-button>
        <el-button type="danger" @click="cancelGroup">取消该组</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.joint-page { max-width: 1400px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.page-header h2 { margin: 0; font-size: 18px; }
</style>
