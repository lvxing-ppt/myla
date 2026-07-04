<template>
  <div class="instrument-container">
    <h2 class="page-title">仪器管理</h2>

    <el-card shadow="never">
      <el-table
        :data="instrumentList"
        v-loading="loading"
        empty-text="暂无仪器数据"
        stripe
        style="width: 100%"
      >
        <el-table-column prop="instrumentId" label="仪器编号" min-width="120" />
        <el-table-column prop="model" label="型号" min-width="120" />
        <el-table-column prop="status" label="状态" min-width="100">
          <template #default="{ row }">
            <span class="status-dot" :class="statusDotClass(row.status)"></span>
            {{ row.status === 'ONLINE' ? '在线' : row.status === 'OFFLINE' ? '离线' : row.status }}
          </template>
        </el-table-column>
        <el-table-column prop="location" label="位置" min-width="120" />
        <el-table-column prop="lastOnlineTime" label="最后在线时间" min-width="160" />
        <el-table-column label="操作" min-width="160" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'OFFLINE'"
              type="success"
              size="small"
              :loading="row._starting"
              @click="handleStart(row)"
            >
              启动
            </el-button>
            <el-button
              v-if="row.status === 'ONLINE'"
              type="danger"
              size="small"
              :loading="row._stopping"
              @click="handleStop(row)"
            >
              停止
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/api/request'

const loading = ref(false)
const instrumentList = ref([])

function statusDotClass(status) {
  return status === 'ONLINE' ? 'online' : 'offline'
}

async function fetchInstruments() {
  loading.value = true
  try {
    const res = await request.get('/instruments')
    if (res.data) {
      instrumentList.value = (res.data.records || []).map(item => ({
        ...item,
        _starting: false,
        _stopping: false
      }))
    }
  } catch {
    instrumentList.value = []
  } finally {
    loading.value = false
  }
}

async function handleStart(row) {
  try {
    await ElMessageBox.confirm(`确认启动仪器 ${row.instrumentId}？`, '操作确认', {
      type: 'info'
    })
  } catch {
    return
  }

  row._starting = true
  try {
    await request.post(`/instruments/${row.id}/start`)
    ElMessage.success(`仪器 ${row.instrumentId} 已启动`)
    row.status = 'ONLINE'
  } catch {
    // Error handled by interceptor
  } finally {
    row._starting = false
  }
}

async function handleStop(row) {
  try {
    await ElMessageBox.confirm(`确认停止仪器 ${row.instrumentId}？`, '操作确认', {
      type: 'warning'
    })
  } catch {
    return
  }

  row._stopping = true
  try {
    await request.post(`/instruments/${row.id}/stop`)
    ElMessage.success(`仪器 ${row.instrumentId} 已停止`)
    row.status = 'OFFLINE'
  } catch {
    // Error handled by interceptor
  } finally {
    row._stopping = false
  }
}

onMounted(() => {
  fetchInstruments()
})
</script>

<style scoped>
.instrument-container {
  padding: 20px;
}

.page-title {
  margin: 0 0 20px 0;
  font-size: 20px;
  color: #303133;
}

.status-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-right: 6px;
}

.status-dot.online {
  background-color: #67c23a;
}

.status-dot.offline {
  background-color: #f56c6c;
}
</style>
