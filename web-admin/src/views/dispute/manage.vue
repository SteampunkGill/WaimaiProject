<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'

const list = ref<any[]>([])
const loading = ref(false)
const statusFilter = ref('')
const drawerVisible = ref(false)
const current = ref<any>({})
const resolveForm = ref({ status: 'RESOLVED', adminRemark: '', resolution: '' })

const statusMap: any = { PENDING: '待处理', INVESTIGATING: '调查中', RESOLVED: '已解决', REJECTED: '已驳回' }
const typeMap: any = { WRONG_ITEM: '送错商品', MISSING_ITEM: '漏送商品', QUALITY_ISSUE: '质量问题', NOT_DELIVERED: '未送达', OTHER: '其他' }
const refundStatusMap: any = { REQUESTED: '待商户处理', APPROVED: '已同意退款', REJECTED: '已拒绝退款' }

function statusTag(s: string) {
  const types: any = { PENDING: 'warning', INVESTIGATING: '', RESOLVED: 'success', REJECTED: 'danger' }
  return types[s] || 'info'
}

async function fetch() {
  loading.value = true
  try {
    const res: any = await request.get('/dispute/admin/list', { params: { status: statusFilter.value || undefined } })
    list.value = res.data || []
  } catch { /* ignore */ }
  loading.value = false
}

function openDetail(row: any) {
  current.value = row
  resolveForm.value = { status: 'RESOLVED', adminRemark: '', resolution: '' }
  drawerVisible.value = true
}

async function doResolve() {
  await request.put(`/dispute/admin/${current.value.id}/resolve`, resolveForm.value)
  ElMessage.success('处理完成')
  drawerVisible.value = false
  fetch()
}

onMounted(fetch)
</script>

<template>
  <div class="dispute-page">
    <div class="page-header">
      <h2>纠纷处理</h2>
      <el-radio-group v-model="statusFilter" @change="fetch">
        <el-radio-button value="">全部</el-radio-button>
        <el-radio-button value="PENDING">待处理</el-radio-button>
        <el-radio-button value="INVESTIGATING">调查中</el-radio-button>
        <el-radio-button value="RESOLVED">已解决</el-radio-button>
        <el-radio-button value="REJECTED">已驳回</el-radio-button>
      </el-radio-group>
    </div>
    <el-table :data="list" v-loading="loading" stripe @row-click="openDetail" style="cursor:pointer">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="orderId" label="订单ID" width="80" />
      <el-table-column label="类型" width="100">
        <template #default="{ row }">{{ typeMap[row.type] || row.type }}</template>
      </el-table-column>
      <el-table-column prop="description" label="描述" show-overflow-tooltip />
      <el-table-column label="退款" width="110">
        <template #default="{ row }">
          <el-tag v-if="row.refundStatus" :type="row.refundStatus === 'APPROVED' ? 'success' : row.refundStatus === 'REJECTED' ? 'danger' : 'warning'" size="small">
            {{ refundStatusMap[row.refundStatus] || row.refundStatus }}
          </el-tag>
          <span v-else style="color:#999;font-size:12px">-</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusTag(row.status)">{{ statusMap[row.status] || row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="170" />
    </el-table>

    <el-drawer v-model="drawerVisible" title="纠纷详情" size="480px">
      <el-descriptions :column="1" border>
        <el-descriptions-item label="订单ID">{{ current.orderId }}</el-descriptions-item>
        <el-descriptions-item label="类型">{{ typeMap[current.type] }}</el-descriptions-item>
        <el-descriptions-item label="描述">{{ current.description }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusTag(current.status)">{{ statusMap[current.status] }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item v-if="current.refundStatus" label="退款状态">
          <el-tag :type="current.refundStatus === 'APPROVED' ? 'success' : current.refundStatus === 'REJECTED' ? 'danger' : 'warning'">
            {{ refundStatusMap[current.refundStatus] || current.refundStatus }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item v-if="current.merchantRemark" label="商户备注">{{ current.merchantRemark }}</el-descriptions-item>
      </el-descriptions>

      <div style="margin-top: 20px">
        <h4>处理</h4>
        <el-form :model="resolveForm" label-width="80px">
          <el-form-item label="处理结果">
            <el-select v-model="resolveForm.status">
              <el-option label="已解决" value="RESOLVED" />
              <el-option label="已驳回" value="REJECTED" />
              <el-option label="调查中" value="INVESTIGATING" />
            </el-select>
          </el-form-item>
          <el-form-item label="处理备注">
            <el-input v-model="resolveForm.adminRemark" type="textarea" :rows="2" />
          </el-form-item>
          <el-form-item label="解决方案">
            <el-input v-model="resolveForm.resolution" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="doResolve">提交处理</el-button>
          </el-form-item>
        </el-form>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.dispute-page { max-width: 1200px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.page-header h2 { margin: 0; font-size: var(--font-size-h4); }
</style>
