<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'

const list = ref<any[]>([])
const loading = ref(false)

async function fetch() {
  loading.value = true
  try {
    const res: any = await request.get('/admin/config')
    list.value = res.data || []
  } catch { /* ignore */ }
  loading.value = false
}

async function doSave(row: any) {
  await request.put(`/admin/config/${row.id}`, { configValue: row.configValue })
  ElMessage.success('保存成功')
}

async function reload() {
  await request.post('/admin/config/reload')
  ElMessage.success('缓存已刷新')
}

onMounted(fetch)
</script>

<template>
  <div class="config-page">
    <div class="page-header">
      <h2>系统配置</h2>
      <el-button @click="reload">刷新缓存</el-button>
    </div>
    <el-table :data="list" v-loading="loading" stripe>
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="configKey" label="配置键" width="200" />
      <el-table-column label="配置值">
        <template #default="{ row }">
          <el-input v-model="row.configValue" />
        </template>
      </el-table-column>
      <el-table-column prop="description" label="说明" />
      <el-table-column label="操作" width="80">
        <template #default="{ row }">
          <el-button text type="primary" @click="doSave(row)">保存</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<style scoped>
.config-page { max-width: 900px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.page-header h2 { margin: 0; font-size: var(--font-size-h4); }
</style>
