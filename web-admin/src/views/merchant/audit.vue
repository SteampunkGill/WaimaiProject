<script setup lang="ts">
import { ref, onMounted } from 'vue'
import request from '@/utils/request'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'

const merchants = ref<any[]>([])
const loading = ref(false)
const statusFilter = ref<number | undefined>(undefined)

const statusMap: Record<number, { text: string; type: string }> = {
  0: { text: '待审核', type: 'warning' },
  1: { text: '已通过', type: 'success' },
  2: { text: '已驳回', type: 'danger' },
  3: { text: '已停用', type: 'info' }
}

async function fetchMerchants() {
  loading.value = true
  try {
    const params: any = {}
    if (statusFilter.value !== undefined && statusFilter.value !== null) {
      params.status = statusFilter.value
    }
    const res: any = await request.get('/admin/merchant/audit', { params })
    if (res.data) {
      merchants.value = Array.isArray(res.data) ? res.data : res.data.records || []
    }
  } catch { ElMessage.error('加载失败') }
  loading.value = false
}

async function audit(merchantId: number, status: number) {
  const label = status === 1 ? '通过' : '驳回'
  try {
    if (status === 2) {
      const { value: reason } = await ElMessageBox.prompt('请输入驳回原因', '审核驳回', {
        confirmButtonText: '确认驳回', cancelButtonText: '取消', inputPlaceholder: '驳回原因（选填）'
      }).catch(() => ({ value: undefined }))
      if (reason === undefined) return
      await request.post(`/admin/merchant/${merchantId}/audit`, { status, reason: reason || '' })
    } else {
      await request.post(`/admin/merchant/${merchantId}/audit`, { status, reason: '' })
    }
    ElMessage.success(`审核${label}成功`)
    fetchMerchants()
  } catch { ElMessage.error(`审核${label}失败`) }
}

async function toggleStatus(merchant: any) {
  const newStatus = merchant.status === 1 ? 3 : 1
  const label = newStatus === 3 ? '停用' : '启用'
  try {
    await ElMessageBox.confirm(`确认${label}该商家？`, '提示', { type: 'warning' })
    await request.put(`/admin/merchant/${merchant.id}/status`, { status: newStatus })
    ElMessage.success(`${label}成功`)
    fetchMerchants()
  } catch { /* cancelled */ }
}

function onFilterChange() { fetchMerchants() }

onMounted(fetchMerchants)
</script>

<template>
  <div class="page-card">
    <div class="summary-row">
      <el-radio-group v-model="statusFilter" size="small" @change="onFilterChange">
        <el-radio-button :value="undefined">全部</el-radio-button>
        <el-radio-button :value="0">待审核</el-radio-button>
        <el-radio-button :value="1">已通过</el-radio-button>
        <el-radio-button :value="2">已驳回</el-radio-button>
        <el-radio-button :value="3">已停用</el-radio-button>
      </el-radio-group>
      <el-button type="primary" size="small" :icon="Refresh" @click="fetchMerchants" :loading="loading">刷新</el-button>
    </div>

    <el-table :data="merchants" v-loading="loading" stripe class="audit-table" empty-text="暂无商家数据">
      <el-table-column prop="id" label="ID" width="65" align="center" />
      <el-table-column prop="name" label="店铺名称" min-width="140" />
      <el-table-column prop="phone" label="联系电话" width="130" />
      <el-table-column prop="address" label="地址" min-width="180" show-overflow-tooltip />
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="statusMap[row.status]?.type || 'info'" size="small" effect="plain" round>
            {{ statusMap[row.status]?.text || row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="申请时间" width="170" />
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <div v-if="row.status === 0" class="action-btns">
            <el-button type="success" size="small" @click="audit(row.id, 1)">通过</el-button>
            <el-button type="danger" size="small" @click="audit(row.id, 2)">驳回</el-button>
          </div>
          <div v-else-if="row.status === 1" class="action-btns">
            <el-tag type="success" size="small">营业中</el-tag>
            <el-button type="warning" size="small" plain @click="toggleStatus(row)">停用</el-button>
          </div>
          <div v-else-if="row.status === 3" class="action-btns">
            <el-tag type="info" size="small">已停用</el-tag>
            <el-button type="success" size="small" plain @click="toggleStatus(row)">启用</el-button>
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
