<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'

const list = ref<any[]>([])
const loading = ref(false)
const dialogVisible = ref(false)
const isEdit = ref(false)
const form = ref<any>({ name: '', type: 'FULL_REDUCTION', threshold: null, discountValue: 0, totalCount: 100, validDays: 7, merchantId: null })

const columns = [
  { prop: 'id', label: 'ID', width: '60' },
  { prop: 'name', label: '名称' },
  { prop: 'type', label: '类型', width: '120' },
  { prop: 'threshold', label: '门槛', width: '100' },
  { prop: 'discountValue', label: '优惠值', width: '100' },
  { prop: 'receivedCount', label: '已领', width: '80' },
  { prop: 'usedCount', label: '已用', width: '80' },
  { prop: 'status', label: '状态', width: '80' },
]

function typeLabel(t: string) {
  const m: any = { FULL_REDUCTION: '满减', DISCOUNT: '折扣', FREE_DELIVERY: '免运费' }
  return m[t] || t
}

function statusLabel(s: number) { return s === 1 ? '启用' : '停用' }

async function fetch() {
  loading.value = true
  try {
    const res: any = await request.get('/admin/coupon/list', { params: { page: 1, size: 100 } })
    list.value = res.data?.records || []
  } catch { /* ignore */ }
  loading.value = false
}

function openCreate() {
  isEdit.value = false
  form.value = { name: '', type: 'FULL_REDUCTION', threshold: null, discountValue: 0, totalCount: 100, validDays: 7, merchantId: null }
  dialogVisible.value = true
}

async function doSave() {
  await request.post('/admin/coupon', form.value)
  ElMessage.success('创建成功')
  dialogVisible.value = false
  fetch()
}

async function toggleStatus(row: any) {
  await request.put(`/admin/coupon/${row.id}/status`)
  ElMessage.success('状态已更新')
  fetch()
}

onMounted(fetch)
</script>

<template>
  <div class="coupon-page">
    <div class="page-header">
      <h2>优惠券管理</h2>
      <el-button type="primary" @click="openCreate">创建优惠券</el-button>
    </div>
    <el-table :data="list" v-loading="loading" stripe>
      <el-table-column v-for="col in columns" :key="col.prop" v-bind="col">
        <template #default="{ row }" v-if="col.prop === 'type'">{{ typeLabel(row.type) }}</template>
        <template #default="{ row }" v-if="col.prop === 'status'">
          <el-tag :type="row.status === 1 ? 'success' : 'info'">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="120">
        <template #default="{ row }">
          <el-button text type="primary" @click="toggleStatus(row)">
            {{ row.status === 1 ? '停用' : '启用' }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑' : '创建优惠券'" width="480px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="类型">
          <el-select v-model="form.type">
            <el-option label="满减" value="FULL_REDUCTION" />
            <el-option label="折扣" value="DISCOUNT" />
            <el-option label="免运费" value="FREE_DELIVERY" />
          </el-select>
        </el-form-item>
        <el-form-item label="门槛" v-if="form.type === 'FULL_REDUCTION'">
          <el-input-number v-model="form.threshold" :min="0" :precision="2" />
        </el-form-item>
        <el-form-item label="优惠值">
          <el-input-number v-model="form.discountValue" :min="0" :precision="2" />
        </el-form-item>
        <el-form-item label="发放量"><el-input-number v-model="form.totalCount" :min="1" /></el-form-item>
        <el-form-item label="有效天数"><el-input-number v-model="form.validDays" :min="1" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="doSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.coupon-page { max-width: 1200px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.page-header h2 { margin: 0; font-size: var(--font-size-h4); }
</style>
