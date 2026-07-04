<template>
  <div class="sample-container">
    <h2 class="page-title">样本管理</h2>

    <!-- Search & Filter -->
    <el-card shadow="never" class="filter-card">
      <el-row :gutter="16" align="middle">
        <el-col :span="6">
          <el-input
            v-model="searchKey"
            placeholder="搜索样本编号/条码/患者"
            clearable
            @keyup.enter="fetchSamples"
          />
        </el-col>
        <el-col :span="4">
          <el-select v-model="statusFilter" placeholder="状态筛选" clearable style="width: 100%">
            <el-option label="已登记" value="REGISTERED" />
            <el-option label="检测中" value="TESTING" />
            <el-option label="待审核" value="PENDING_REVIEW" />
            <el-option label="已完成" value="COMPLETED" />
          </el-select>
        </el-col>
        <el-col :span="4">
          <el-button type="primary" @click="fetchSamples">查询</el-button>
          <el-button @click="resetFilter">重置</el-button>
        </el-col>
      </el-row>
    </el-card>

    <!-- Table -->
    <el-card shadow="never" style="margin-top: 16px">
      <el-table
        :data="sampleList"
        v-loading="loading"
        empty-text="暂无样本数据"
        stripe
        style="width: 100%"
      >
        <el-table-column prop="sampleId" label="样本编号" min-width="140" />
        <el-table-column prop="barcode" label="条码" min-width="120" />
        <el-table-column prop="patientName" label="患者" min-width="100" />
        <el-table-column prop="specimenType" label="标本类型" min-width="100" />
        <el-table-column prop="status" label="状态" min-width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="receiveTime" label="接收时间" min-width="160" />
      </el-table>

      <!-- Pagination -->
      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50]"
          :total="total"
          layout="total, sizes, prev, pager, next"
          @current-change="fetchSamples"
          @size-change="fetchSamples"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import request from '@/api/request'

const loading = ref(false)
const sampleList = ref([])
const searchKey = ref('')
const statusFilter = ref('')
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)

function statusTagType(status) {
  const map = {
    'REGISTERED': '',
    'TESTING': 'warning',
    'PENDING_REVIEW': 'info',
    'COMPLETED': 'success',
    'REJECTED': 'danger'
  }
  return map[status] || ''
}

async function fetchSamples() {
  loading.value = true
  try {
    const params = {
      page: page.value,
      size: pageSize.value
    }
    if (searchKey.value) params.keyword = searchKey.value
    if (statusFilter.value) params.status = statusFilter.value

    const res = await request.get('/samples', { params })
    if (res.data) {
      sampleList.value = res.data.records || []
      total.value = res.data.total || 0
    }
  } catch {
    sampleList.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function resetFilter() {
  searchKey.value = ''
  statusFilter.value = ''
  page.value = 1
  fetchSamples()
}

onMounted(() => {
  fetchSamples()
})
</script>

<style scoped>
.sample-container {
  padding: 20px;
}

.page-title {
  margin: 0 0 20px 0;
  font-size: 20px;
  color: #303133;
}

.filter-card {
  margin-bottom: 0;
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
