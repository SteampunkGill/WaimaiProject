<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import request from '@/utils/request'

const list = ref<any[]>([])
const loading = ref(false)
let timer: any = null

async function fetch() {
  loading.value = true
  try {
    const res: any = await request.get('/admin/overtime/list')
    list.value = res.data || []
  } catch { /* ignore */ }
  loading.value = false
}

onMounted(() => {
  fetch()
  timer = setInterval(fetch, 15000)
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>

<template>
  <div class="overtime-page">
    <div class="page-header">
      <h2>超时订单监控</h2>
      <span class="refresh-hint">每15秒自动刷新</span>
    </div>
    <el-table :data="list" v-loading="loading" stripe empty-text="暂无超时订单">
      <el-table-column prop="orderNo" label="订单号" width="180" />
      <el-table-column prop="merchantName" label="商家" width="140" />
      <el-table-column prop="riderName" label="骑手" width="100" />
      <el-table-column prop="status" label="状态" width="100" />
      <el-table-column prop="estimatedMinutes" label="预计时长(分钟)" width="120" />
      <el-table-column prop="overtimeMinutes" label="超时(分钟)" width="100">
        <template #default="{ row }">
          <el-tag type="danger">{{ row.overtimeMinutes }} 分钟</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="deliverTime" label="配送开始时间" width="170" />
    </el-table>
  </div>
</template>

<style scoped>
.overtime-page { max-width: 1200px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.page-header h2 { margin: 0; font-size: var(--font-size-h4); }
.refresh-hint { font-size: var(--font-size-caption); color: var(--color-text-disabled); }
</style>
