<template>
  <div class="party-list">
    <el-tabs v-model="activeTab">
      <el-tab-pane label="客户" name="customer">
        <CustomerList />
      </el-tab-pane>
      <el-tab-pane label="供应商" name="vendor">
        <VendorList />
      </el-tab-pane>
      <el-tab-pane label="员工" name="employee">
        <EmployeeList />
      </el-tab-pane>
      <el-tab-pane label="部门" name="dept">
        <DeptList />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import CustomerList from '@/views/arap/customer/CustomerList.vue'
import VendorList from '@/views/arap/vendor/VendorList.vue'
import EmployeeList from '@/views/arap/employee/EmployeeList.vue'
import DeptList from '@/views/arap/dept/DeptList.vue'

const activeTab = ref('customer')
</script>

<style scoped>
.party-list { padding: 20px; }
</style>