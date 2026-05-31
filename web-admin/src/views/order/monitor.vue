<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed } from 'vue'
import request from '@/utils/request'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'

const orders = ref<any[]>([])
const loading = ref(false)
const currentPage = ref(1)
const total = ref(0)
const filterStatus = ref('')
let timer: ReturnType<typeof setInterval> | null = null

const statusMap: Record<string, { text: string; type: string; color: string }> = {
  PENDING_PAYMENT: { text: '待支付', type: 'warning', color: '#FAAD14' },
  PAID: { text: '已支付', type: 'primary', color: '#1890FF' },
  PREPARING: { text: '备餐中', type: 'info', color: '#909399' },
  DELIVERING: { text: '配送中', type: '', color: '#52C41A' },
  COMPLETED: { text: '已完成', type: 'success', color: '#52C41A' },
  CANCELLED: { text: '已取消', type: 'danger', color: '#FF4D4F' }
}

const statsRow = computed(() => {
  const counts: Record<string, number> = {}
  orders.value.forEach(o => {
    counts[o.status] = (counts[o.status] || 0) + 1
  })
  return Object.entries(statusMap).map(([key, entry]) => ({
    status: key,
    label: entry.text,
    color: entry.color,
    count: counts[key] || 0
  }))
})

async function fetchOrders() {
  loading.value = true
  try {
    const params: any = { page: currentPage.value, size: 20 }
    if (filterStatus.value) params.status = filterStatus.value
    const res: any = await request.get('/admin/order/monitor', { params })
    if (res.data) {
      orders.value = res.data.records || []
      total.value = res.data.total || 0
    }
  } catch {
    ElMessage.error('加载失败')
  } finally {
    loading.value = false
  }
}

function onPageChange(page: number) {
  currentPage.value = page
  fetchOrders()
}

function onStatusFilter(_val: string) {
  currentPage.value = 1
  fetchOrders()
}

onMounted(() => {
  fetchOrders()
  timer = setInterval(fetchOrders, 15000)
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>

<template>
  <div>
    <!-- Quick Stats Strip -->
    <div class="quick-stats">
      <div v-for="s in statsRow" :key="s.status" class="qs-item" @click="filterStatus = s.status; onStatusFilter(s.status)">
        <span class="qs-dot" :style="{ background: s.color }"></span>
        <span class="qs-label">{{ s.label }}</span>
        <span class="qs-count" :style="{ color: s.color }">{{ s.count }}</span>
      </div>
    </div>

    <!-- Table -->
    <div class="page-card">
      <div class="table-header">
        <div class="table-title">
          <span>订单列表</span>
          <span class="table-total">共 {{ total }} 条</span>
        </div>
        <div class="table-actions">
          <el-select v-model="filterStatus" placeholder="筛选状态" clearable size="small" style="width: 130px" @change="onStatusFilter">
            <el-option v-for="(val, key) in statusMap" :key="key" :label="val.text" :value="key" />
          </el-select>
          <el-button type="primary" size="small" :icon="Refresh" @click="fetchOrders" :loading="loading">
            刷新
          </el-button>
        </div>
      </div>

      <el-table :data="orders" v-loading="loading" stripe empty-text="暂无订单数据" class="order-table">
        <el-table-column prop="id" label="ID" width="65" align="center" />
        <el-table-column prop="orderNo" label="订单号" width="175" show-overflow-tooltip />
        <el-table-column prop="merchantName" label="商家" width="130" show-overflow-tooltip />
        <el-table-column prop="userName" label="顾客" width="90" />
        <el-table-column label="实付" width="85" align="right">
          <template #default="{ row }">
            <span class="pay-amount">¥{{ Number(row.payAmount || 0).toFixed(2) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag
              :type="statusMap[row.status]?.type || 'info'"
              :color="statusMap[row.status]?.color"
              size="small"
              effect="dark"
              round
            >
              {{ statusMap[row.status]?.text || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="address" label="配送地址" min-width="180" show-overflow-tooltip />
        <el-table-column prop="riderId" label="骑手ID" width="80" align="center">
          <template #default="{ row }">
            <span v-if="row.riderId">{{ row.riderId }}</span>
            <span v-else style="color:#ccc">--</span>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="下单时间" width="170" />
      </el-table>

      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="currentPage"
          :total="total"
          :page-size="20"
          layout="total, prev, pager, next"
          background
          small
          @current-change="onPageChange"
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
/* Quick Stats */
.quick-stats {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}
.qs-item {
  background: var(--color-bg-card);
  border-radius: var(--radius-base);
  padding: var(--spacing-md) var(--spacing-base);
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  cursor: pointer;
  transition: all 0.2s;
  box-shadow: var(--shadow-sm);
  user-select: none;
}
.qs-item:hover {
  transform: translateY(-1px);
  box-shadow: var(--shadow-md);
}
.qs-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}
.qs-label {
  font-size: var(--font-size-body-sm);
  color: var(--color-text-secondary);
}
.qs-count {
  font-size: 18px;
  font-weight: 700;
  min-width: 24px;
  text-align: right;
}

/* Card */
.page-card {
  background: var(--color-bg-card);
  border-radius: var(--radius-lg);
  padding: var(--spacing-lg);
  box-shadow: var(--shadow-sm);
}

.table-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 18px;
}
.table-title {
  display: flex;
  align-items: baseline;
  gap: 10px;
  font-size: var(--font-size-body);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
}
.table-total {
  font-size: var(--font-size-body-sm);
  font-weight: var(--font-weight-normal);
  color: var(--color-text-placeholder);
}
.table-actions {
  display: flex;
  gap: 8px;
}

.order-table :deep(.el-table__header th) {
  background: var(--color-bg-stripe);
  color: var(--color-text-secondary);
  font-weight: var(--font-weight-semibold);
}

.pay-amount {
  font-weight: var(--font-weight-bold);
  color: var(--color-danger);
  font-size: var(--font-size-body);
}

.pagination-wrap {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
