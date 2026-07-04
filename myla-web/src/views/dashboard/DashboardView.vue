<template>
  <div class="dashboard-container">
    <h2 class="page-title">实时看板</h2>
    <el-row :gutter="20">
      <el-col :span="6" v-for="card in statCards" :key="card.label">
        <el-card shadow="hover" class="stat-card">
          <el-statistic :value="card.value" :title="card.label">
            <template #prefix>
              <el-icon :size="24">
                <component :is="card.icon" />
              </el-icon>
            </template>
          </el-statistic>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" style="margin-top: 20px">
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header>
            <span>最近样本</span>
          </template>
          <el-table :data="recentSamples" v-loading="loading" size="small" empty-text="暂无数据">
            <el-table-column prop="sampleId" label="样本编号" />
            <el-table-column prop="patientName" label="患者" />
            <el-table-column prop="specimenType" label="标本类型" />
            <el-table-column prop="status" label="状态">
              <template #default="{ row }">
                <el-tag :type="statusType(row.status)" size="small">{{ row.status }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header>
            <span>危急值告警</span>
          </template>
          <el-table :data="alerts" v-loading="loading" size="small" empty-text="暂无告警">
            <el-table-column prop="sampleId" label="样本编号" />
            <el-table-column prop="patientName" label="患者" />
            <el-table-column prop="organismName" label="检出菌" />
            <el-table-column prop="alertTime" label="告警时间" />
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { Document, Checked, Monitor, Warning } from '@element-plus/icons-vue'
import request from '@/api/request'

const loading = ref(false)
const recentSamples = ref([])
const alerts = ref([])

const statCards = ref([
  { label: '今日样本数', value: 0, icon: Document },
  { label: '待审核结果', value: 0, icon: Checked },
  { label: '仪器在线数', value: 0, icon: Monitor },
  { label: '危急值告警', value: 0, icon: Warning }
])

function statusType(status) {
  const map = { '已登记': '', '检测中': 'warning', '待审核': 'info', '已完成': 'success', '已退回': 'danger' }
  return map[status] || ''
}

onMounted(async () => {
  loading.value = true
  try {
    // Fetch dashboard stats — replace with real API when available
    // const res = await request.get('/dashboard/stats')
    // if (res.data) {
    //   statCards.value[0].value = res.data.todaySamples || 0
    //   statCards.value[1].value = res.data.pendingResults || 0
    //   statCards.value[2].value = res.data.onlineInstruments || 0
    //   statCards.value[3].value = res.data.criticalAlerts || 0
    //   recentSamples.value = res.data.recentSamples || []
    //   alerts.value = res.data.alerts || []
    // }
  } catch {
    // API not available yet, show empty state
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.dashboard-container {
  padding: 20px;
}

.page-title {
  margin: 0 0 20px 0;
  font-size: 20px;
  color: #303133;
}

.stat-card {
  text-align: center;
}
</style>
