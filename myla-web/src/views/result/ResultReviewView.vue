<template>
  <div class="result-container">
    <h2 class="page-title">结果审核</h2>

    <el-row :gutter="16" class="result-layout">
      <!-- Left: Pending Results List -->
      <el-col :span="8">
        <el-card shadow="never" class="list-card">
          <template #header>
            <span>待审核结果列表 ({{ pendingList.length }})</span>
          </template>
          <el-table
            :data="pendingList"
            v-loading="listLoading"
            highlight-current-row
            empty-text="暂无待审核结果"
            size="small"
            @current-change="handleSelect"
          >
            <el-table-column prop="sampleId" label="样本编号" min-width="120" />
            <el-table-column prop="organismName" label="检出菌" min-width="140" />
            <el-table-column prop="priority" label="优先级" width="80">
              <template #default="{ row }">
                <el-tag
                  :type="row.priority === 'HIGH' ? 'danger' : row.priority === 'MEDIUM' ? 'warning' : ''"
                  size="small"
                >
                  {{ row.priority === 'HIGH' ? '高' : row.priority === 'MEDIUM' ? '中' : '低' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="testTime" label="检测时间" min-width="140" />
          </el-table>
        </el-card>
      </el-col>

      <!-- Right: Result Detail -->
      <el-col :span="16">
        <el-card shadow="never" class="detail-card" v-loading="detailLoading">
          <template v-if="!selectedResult">
            <el-empty description="请在左侧选择一条待审核结果" />
          </template>
          <template v-else>
            <!-- Patient Info -->
            <h3>患者信息</h3>
            <el-descriptions :column="2" border size="small" style="margin-bottom: 20px">
              <el-descriptions-item label="样本编号">{{ selectedResult.sampleId }}</el-descriptions-item>
              <el-descriptions-item label="患者姓名">{{ selectedResult.patientName || '-' }}</el-descriptions-item>
              <el-descriptions-item label="标本类型">{{ selectedResult.specimenType || '-' }}</el-descriptions-item>
              <el-descriptions-item label="科室">{{ selectedResult.wardName || '-' }}</el-descriptions-item>
              <el-descriptions-item label="采集时间">{{ selectedResult.collectTime || '-' }}</el-descriptions-item>
              <el-descriptions-item label="接收时间">{{ selectedResult.receiveTime || '-' }}</el-descriptions-item>
            </el-descriptions>

            <!-- Organism Identification -->
            <h3>菌株鉴定</h3>
            <el-descriptions :column="2" border size="small" style="margin-bottom: 20px">
              <el-descriptions-item label="菌株代码">{{ selectedResult.organismCode || '-' }}</el-descriptions-item>
              <el-descriptions-item label="菌株名称">{{ selectedResult.organismName || '-' }}</el-descriptions-item>
              <el-descriptions-item label="鉴定率">{{ selectedResult.identificationPercent || '-' }}%</el-descriptions-item>
              <el-descriptions-item label="检测时间">{{ selectedResult.testTime || '-' }}</el-descriptions-item>
            </el-descriptions>

            <!-- AST Results Table -->
            <h3>药敏结果 (AST)</h3>
            <el-table
              :data="astList"
              v-loading="astLoading"
              empty-text="暂无药敏数据"
              border
              size="small"
              style="margin-bottom: 20px"
            >
              <el-table-column prop="antibioticName" label="抗生素" min-width="140" />
              <el-table-column prop="micValue" label="MIC" min-width="80" />
              <el-table-column prop="micUnit" label="单位" width="60" />
              <el-table-column prop="finalSir" label="SIR" width="100">
                <template #default="{ row }">
                  <el-tag
                    :type="sirTagType(row.finalSir)"
                    size="small"
                  >
                    {{ row.finalSir }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="expertRuleComment" label="专家评语" min-width="160">
                <template #default="{ row }">
                  <span :style="{ color: row.isCorrected ? '#e6a23c' : '' }">
                    {{ row.expertRuleComment || '-' }}
                  </span>
                </template>
              </el-table-column>
            </el-table>

            <!-- Action Buttons -->
            <div class="action-buttons">
              <el-button
                type="success"
                :loading="approving"
                @click="handleApprove"
              >
                通过
              </el-button>
              <el-button
                type="danger"
                :loading="rejecting"
                @click="handleReject"
              >
                退回
              </el-button>
            </div>
          </template>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/api/request'

const listLoading = ref(false)
const detailLoading = ref(false)
const astLoading = ref(false)
const approving = ref(false)
const rejecting = ref(false)

const pendingList = ref([])
const selectedResult = ref(null)
const astList = ref([])

function sirTagType(sir) {
  const map = { 'S': 'success', 'I': 'warning', 'R': 'danger' }
  return map[sir] || ''
}

async function fetchPendingList() {
  listLoading.value = true
  try {
    // Fetch pending review results
    const res = await request.get('/results', {
      params: { reviewStatus: 'PENDING', page: 1, size: 100 }
    })
    if (res.data) {
      pendingList.value = res.data.records || []
    }
  } catch {
    pendingList.value = []
  } finally {
    listLoading.value = false
  }
}

async function handleSelect(row) {
  if (!row) return
  detailLoading.value = true
  astLoading.value = true
  selectedResult.value = row

  try {
    // Fetch full result detail with AST
    const res = await request.get(`/results/${row.id}`)
    if (res.data) {
      selectedResult.value = { ...selectedResult.value, ...res.data }
      astList.value = res.data.astResults || []
    }
  } catch {
    astList.value = []
  } finally {
    detailLoading.value = false
    astLoading.value = false
  }
}

async function handleApprove() {
  try {
    await ElMessageBox.confirm('确认通过该审核结果？', '审核确认', {
      confirmButtonText: '确认通过',
      cancelButtonText: '取消',
      type: 'info'
    })
  } catch {
    return
  }

  approving.value = true
  try {
    await request.put(`/results/${selectedResult.value.id}/review`, {
      action: 'APPROVE'
    })
    ElMessage.success('审核通过')
    // Remove from pending list
    pendingList.value = pendingList.value.filter(item => item.id !== selectedResult.value.id)
    selectedResult.value = null
    astList.value = []
  } catch {
    // Error handled by interceptor
  } finally {
    approving.value = false
  }
}

async function handleReject() {
  try {
    await ElMessageBox.confirm('确认退回该审核结果？退回后需要重新检测。', '退回确认', {
      confirmButtonText: '确认退回',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }

  rejecting.value = true
  try {
    await request.put(`/results/${selectedResult.value.id}/review`, {
      action: 'REJECT'
    })
    ElMessage.success('已退回')
    pendingList.value = pendingList.value.filter(item => item.id !== selectedResult.value.id)
    selectedResult.value = null
    astList.value = []
  } catch {
    // Error handled by interceptor
  } finally {
    rejecting.value = false
  }
}

onMounted(() => {
  fetchPendingList()
})
</script>

<style scoped>
.result-container {
  padding: 20px;
}

.page-title {
  margin: 0 0 20px 0;
  font-size: 20px;
  color: #303133;
}

.result-layout {
  align-items: flex-start;
}

.list-card,
.detail-card {
  height: calc(100vh - 140px);
  overflow-y: auto;
}

.list-card :deep(.el-card__body) {
  padding: 0;
}

.action-buttons {
  display: flex;
  justify-content: center;
  gap: 24px;
  padding: 20px 0;
}
</style>
