<script setup lang="ts">
import { ref, onMounted } from 'vue'
import request from '@/utils/request'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'

const riders = ref<any[]>([])
const loading = ref(false)
const statusFilter = ref<number | undefined>(undefined)

const auditStatusMap: Record<number, { text: string; type: string }> = {
  0: { text: '待审核', type: 'warning' },
  1: { text: '已通过', type: 'success' },
  2: { text: '已驳回', type: 'danger' }
}
const riderStatusMap: Record<number, { text: string; color: string }> = {
  0: { text: '离线', color: '#ccc' },
  3: { text: '在线', color: '#67C23A' },
  4: { text: '离线', color: '#ccc' },
  5: { text: '已停用', color: '#F56C6C' }
}

async function fetchRiders() {
  loading.value = true
  try {
    const params: any = {}
    if (statusFilter.value !== undefined && statusFilter.value !== null) {
      params.auditStatus = statusFilter.value
    }
    const res: any = await request.get('/admin/rider/audit', { params })
    if (res.data) riders.value = Array.isArray(res.data) ? res.data : []
  } catch { ElMessage.error('加载失败') }
  loading.value = false
}

async function auditRider(riderId: number, auditStatus: number) {
  const label = auditStatus === 1 ? '通过' : '驳回'
  try {
    if (auditStatus === 2) {
      const { value: reason } = await ElMessageBox.prompt('请输入驳回原因', '骑手审核驳回', {
        confirmButtonText: '确认驳回', cancelButtonText: '取消', inputPlaceholder: '驳回原因（选填）'
      }).catch(() => ({ value: undefined }))
      if (reason === undefined) return
      await request.post(`/admin/rider/${riderId}/audit`, { auditStatus, reason: reason || '' })
    } else {
      await request.post(`/admin/rider/${riderId}/audit`, { auditStatus, reason: '' })
    }
    ElMessage.success(`审核${label}成功`)
    fetchRiders()
  } catch { ElMessage.error(`审核${label}失败`) }
}

async function toggleRiderStatus(rider: any) {
  const newStatus = rider.status === 5 ? 1 : 5
  const label = newStatus === 5 ? '停用' : '启用'
  try {
    await ElMessageBox.confirm(`确认${label}该骑手？`, '提示', { type: 'warning' })
    await request.put(`/admin/rider/${rider.id}/status`, { status: newStatus })
    ElMessage.success(`${label}成功`)
    fetchRiders()
  } catch { /* cancelled */ }
}

function onFilterChange() { fetchRiders() }

onMounted(fetchRiders)
</script>

<template>
  <div class="page-card">
    <div class="summary-row">
      <el-radio-group v-model="statusFilter" size="small" @change="onFilterChange">
        <el-radio-button :value="undefined">全部</el-radio-button>
        <el-radio-button :value="0">待审核</el-radio-button>
        <el-radio-button :value="1">已通过</el-radio-button>
        <el-radio-button :value="2">已驳回</el-radio-button>
      </el-radio-group>
      <el-button type="primary" size="small" :icon="Refresh" @click="fetchRiders" :loading="loading">刷新</el-button>
    </div>

    <el-table :data="riders" v-loading="loading" stripe class="audit-table" empty-text="暂无骑手数据">
      <el-table-column prop="id" label="ID" width="65" align="center" />
      <el-table-column prop="realName" label="真实姓名" width="100" />
      <el-table-column prop="phone" label="手机号" width="130" />
      <el-table-column prop="idCard" label="身份证号" width="190" show-overflow-tooltip />
      <el-table-column label="审核状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="auditStatusMap[row.auditStatus]?.type || 'info'" size="small" effect="plain" round>
            {{ auditStatusMap[row.auditStatus]?.text || row.auditStatus }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="接单状态" width="90" align="center">
        <template #default="{ row }">
          <span :style="{ color: (riderStatusMap[row.status] || riderStatusMap[0]).color, fontSize: '13px' }">
            {{ (riderStatusMap[row.status] || riderStatusMap[0]).text }}
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="totalOrders" label="累计配送" width="90" align="center" />
      <el-table-column prop="score" label="评分" width="70" align="center">
        <template #default="{ row }">{{ row.score ? Number(row.score).toFixed(1) : '--' }}</template>
      </el-table-column>
      <el-table-column prop="createTime" label="注册时间" width="170" />
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <div v-if="row.auditStatus === 0" class="action-btns">
            <el-button type="success" size="small" @click="auditRider(row.id, 1)">通过</el-button>
            <el-button type="danger" size="small" @click="auditRider(row.id, 2)">驳回</el-button>
          </div>
          <div v-else-if="row.auditStatus === 1" class="action-btns">
            <el-tag type="success" size="small">正常</el-tag>
            <el-button type="warning" size="small" plain @click="toggleRiderStatus(row)">停用</el-button>
          </div>
          <div v-else-if="row.status === 5" class="action-btns">
            <el-tag type="danger" size="small">已停用</el-tag>
            <el-button type="success" size="small" plain @click="toggleRiderStatus(row)">启用</el-button>
          </div>
          <span v-else style="color: #ccc; font-size: 13px">--</span>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<style scoped>
.page-card { background: var(--color-bg-card); border-radius: var(--radius-lg); padding: var(--spacing-lg); box-shadow: var(--shadow-sm); }
.summary-row { display: flex; justify-content: space-between; align-items: center; margin-bottom: 18px; }
.audit-table :deep(.el-table__header th) { background: var(--color-bg-stripe); color: var(--color-text-secondary); font-weight: var(--font-weight-semibold); }
.action-btns { display: flex; gap: 6px; align-items: center; }
</style>
