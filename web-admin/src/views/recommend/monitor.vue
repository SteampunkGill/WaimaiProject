<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import * as echarts from 'echarts'
import request from '@/utils/request'

const hotDishChartRef = ref<HTMLElement>()
const categoryPieRef = ref<HTMLElement>()
const pricePieRef = ref<HTMLElement>()
let hotChart: echarts.ECharts | null = null
let catChart: echarts.ECharts | null = null
let priceChart: echarts.ECharts | null = null

const stats = ref({
  totalDishes: 0,
  totalCategories: 0,
  hotDishes: [] as any[],
  categoryDistribution: [] as any[],
  priceDistribution: [] as any[]
})
const loading = ref(false)

const statCards = [
  { label: '菜品总数', key: 'totalDishes', suffix: '道', color: '#1890FF', bg: '#E6F7FF', icon: 'Dish' },
  { label: '分类总数', key: 'totalCategories', suffix: '个', color: '#52C41A', bg: '#F6FFED', icon: 'Grid' },
  { label: '热销菜品', key: 'hotDishes', suffix: '', color: '#FF6B35', bg: '#FFF3ED', icon: 'StarFilled', isArr: true },
  { label: '价格区间', key: 'priceDistribution', suffix: '', color: '#FAAD14', bg: '#FFFBE6', icon: 'PriceTag', isArr: true },
]

function formatStatVal(card: any): string {
  const v = (stats.value as any)[card.key]
  if (card.isArr) return String(Array.isArray(v) ? v.length : 0) + ' 类'
  return String(v ?? 0) + card.suffix
}

const hotColorPalette = ['#FF6B35', '#FF7A45', '#FF8C69', '#FFA07A', '#FFB899', '#FFCDB2', '#FFD4C0', '#FFDED4', '#FFE8E0', '#FFF0EB']

async function fetchData() {
  loading.value = true
  try {
    const res: any = await request.get('/admin/recommend/stats')
    if (res.data) {
      stats.value = res.data
    }
  } catch { /* use defaults */ }
  loading.value = false
  initCharts()
}

function initCharts() {
  if (hotDishChartRef.value) {
    if (!hotChart) hotChart = echarts.init(hotDishChartRef.value)
    const dishes = stats.value.hotDishes || []
    hotChart.setOption({
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, backgroundColor: '#fff', borderColor: '#eee', textStyle: { color: '#333' } },
      grid: { left: 6, right: 30, bottom: 0, top: 8, containLabel: true },
      xAxis: { type: 'value', name: '月销量', axisLabel: { color: '#999' }, splitLine: { lineStyle: { color: '#f5f5f5' } } },
      yAxis: {
        type: 'category',
        data: dishes.map((d: any) => d.name).reverse(),
        axisLabel: { width: 100, overflow: 'truncate', color: '#666' },
        axisLine: { show: false },
        axisTick: { show: false }
      },
      series: [{
        type: 'bar',
        barWidth: 16,
        data: dishes.map((d: any, i: number) => ({
          value: d.monthlySales || 0,
          itemStyle: {
            color: hotColorPalette[i % hotColorPalette.length],
            borderRadius: [0, 8, 8, 0]
          }
        })).reverse(),
        label: { show: true, position: 'right', fontSize: 11, color: '#999' }
      }]
    })
  }

  if (categoryPieRef.value) {
    if (!catChart) catChart = echarts.init(categoryPieRef.value)
    const catData = (stats.value.categoryDistribution || []).filter((c: any) => c.value > 0)
    catChart.setOption({
      tooltip: { trigger: 'item', formatter: '{b}: {c} 道 ({d}%)' },
      series: [{
        type: 'pie',
        radius: ['50%', '78%'],
        center: ['50%', '48%'],
        avoidLabelOverlap: false,
        itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 3 },
        label: { show: true, formatter: '{b}\n{d}%', fontSize: 11 },
        data: catData.length > 0 ? catData : [{ value: 1, name: '暂无数据', itemStyle: { color: '#eee' } }]
      }]
    })
  }

  if (pricePieRef.value) {
    if (!priceChart) priceChart = echarts.init(pricePieRef.value)
    const priceData = (stats.value.priceDistribution || []).filter((p: any) => p.value > 0)
    priceChart.setOption({
      tooltip: { trigger: 'item', formatter: '{b}: {c} 道 ({d}%)' },
      series: [{
        type: 'pie',
        radius: '68%',
        center: ['50%', '48%'],
        itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 3 },
        label: { show: true, formatter: '{b}\n{c} 道', fontSize: 12 },
        data: priceData.length > 0
          ? priceData.map((p: any, i: number) => ({
              ...p,
              itemStyle: { color: ['#67C23A', '#E6A23C', '#F56C6C'][i] }
            }))
          : [{ value: 1, name: '暂无数据', itemStyle: { color: '#eee' } }]
      }]
    })
  }
}

onMounted(fetchData)
onUnmounted(() => {
  hotChart?.dispose()
  catChart?.dispose()
  priceChart?.dispose()
})
</script>

<template>
  <div class="recommend" v-loading="loading">
    <!-- Stat Cards -->
    <div class="stat-grid">
      <div v-for="card in statCards" :key="card.label" class="stat-card">
        <div class="stat-icon-wrap" :style="{ background: card.bg }">
          <el-icon :size="20" :color="card.color"><component :is="card.icon" /></el-icon>
        </div>
        <div class="stat-body">
          <span class="stat-label">{{ card.label }}</span>
          <span class="stat-value" :style="{ color: card.color }">{{ formatStatVal(card) }}</span>
        </div>
      </div>
    </div>

    <!-- Charts -->
    <div class="charts-row">
      <div class="chart-card chart-card--large">
        <div class="chart-header">
          <span class="chart-title">热销菜品 Top 10</span>
          <span class="chart-hint">按月销量排序</span>
        </div>
        <div ref="hotDishChartRef" class="chart-body" style="height: 400px"></div>
      </div>
      <div class="chart-card">
        <div class="chart-header">
          <span class="chart-title">菜品分类分布</span>
        </div>
        <div ref="categoryPieRef" class="chart-body" style="height: 360px"></div>
      </div>
    </div>

    <div class="charts-row">
      <div class="chart-card chart-card--full">
        <div class="chart-header">
          <span class="chart-title">价格区间分布</span>
        </div>
        <div ref="pricePieRef" class="chart-body" style="height: 320px"></div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.recommend {
  max-width: 1400px;
}

/* Stat Cards */
.stat-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--spacing-base);
  margin-bottom: var(--spacing-base);
}
.stat-card {
  background: var(--color-bg-card);
  border-radius: var(--radius-lg);
  padding: var(--spacing-lg);
  display: flex;
  align-items: center;
  gap: var(--spacing-base);
  box-shadow: var(--shadow-sm);
  transition: all 0.25s;
  cursor: default;
}
.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-md);
}
.stat-icon-wrap {
  width: 48px;
  height: 48px;
  border-radius: var(--radius-lg);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.stat-body {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-xs);
}
.stat-label {
  font-size: var(--font-size-body-sm);
  color: var(--color-text-placeholder);
}
.stat-value {
  font-size: var(--font-size-h2);
  font-weight: var(--font-weight-bold);
}

/* Charts */
.charts-row {
  display: flex;
  gap: var(--spacing-base);
  margin-bottom: var(--spacing-base);
}
.chart-card {
  background: var(--color-bg-card);
  border-radius: var(--radius-lg);
  padding: var(--spacing-lg);
  flex: 1;
  box-shadow: var(--shadow-sm);
  min-width: 0;
}
.chart-card--large {
  flex: 1.5;
}
.chart-card--full {
  flex: none;
  width: 100%;
}
.chart-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--spacing-xs);
}
.chart-title {
  font-size: var(--font-size-body);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
}
.chart-hint {
  font-size: var(--font-size-caption-sm);
  color: var(--color-text-disabled);
}
</style>
