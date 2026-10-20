<template>
  <VnetPage title="成本管理">
    <template #actions>
      <el-button :loading="syncingRates" @click="syncRates">同步费率</el-button>
    </template>
    <div class="hr-stat-grid">
      <div class="hr-stat primary"><span>总人数</span><strong>{{ summary.totalEmployees }}</strong></div>
      <div class="hr-stat success"><span>月薪总额</span><strong>{{ formatMoney(summary.totalMonthlySalary) }}</strong></div>
      <div class="hr-stat warning"><span>平均月薪</span><strong>{{ formatMoney(summary.avgSalary) }}</strong></div>
      <div class="hr-stat danger"><span>年薪总额</span><strong>{{ formatMoney(summary.annualSalary) }}</strong></div>
    </div>



    <section class="hr-panel">
      <div class="hr-panel-head">
        <h3>月度结算</h3>
        <div class="hr-filter-row">
          <el-date-picker v-model="settleMonth" type="month" value-format="YYYY-MM" />
          <el-button @click="recalc">重算</el-button>
        </div>
      </div>

      <div class="hr-settle-grid">
        <div class="hr-settle-card"><span>人数</span><strong>{{ settlement?.totalEmployees || 0 }}</strong></div>
        <div class="hr-settle-card success"><span>实发总额</span><strong>{{ formatMoney(totalNet) }}</strong></div>
        <div class="hr-settle-card info"><span>基础工资</span><strong>{{ formatMoney(totalBase) }}</strong></div>
        <div class="hr-settle-card warning"><span>加班费</span><strong>+{{ formatMoney(totalOtPay) }}</strong></div>
        <div class="hr-settle-card danger"><span>请假扣款</span><strong>-{{ formatMoney(totalLvDeduct) }}</strong></div>
      </div>

    </section>

    <section class="hr-panel">
      <div class="hr-panel-head"><h3>公司人力成本</h3></div>

    <section class="hr-panel">
      <div class="hr-panel-head"><h3>楼栋结算汇总</h3></div>
      <el-table :data="buildingCosts" size="small">
        <el-table-column prop="building" label="楼栋" min-width="120" />
        <el-table-column prop="count" label="人数" width="70" />
        <el-table-column label="基础工资" min-width="110" align="right">
          <template #default="{ row }">{{ formatMoney(row.baseSalary) }}</template>
        </el-table-column>
        <el-table-column label="加班费" min-width="110" align="right">
          <template #default="{ row }"><span class="hr-money-up">+{{ formatMoney(row.otPay) }}</span></template>
        </el-table-column>
        <el-table-column label="扣款" min-width="110" align="right">
          <template #default="{ row }"><span class="hr-money-down">-{{ formatMoney(row.lvDeduct) }}</span></template>
        </el-table-column>
        <el-table-column label="实发" min-width="120" align="right">
          <template #default="{ row }"><strong>{{ formatMoney(row.netPay) }}</strong></template>
        </el-table-column>
      </el-table>
    </section>

      <el-table :data="companyCosts" size="small">
        <el-table-column prop="company" label="公司" min-width="120" />
        <el-table-column prop="count" label="人数" width="70" />
        <el-table-column label="月薪总额" min-width="130">
          <template #default="{ row }">{{ formatMoney(row.totalSalary) }}</template>
        </el-table-column>
      </el-table>
    </section>

    <section class="hr-panel">
      <div class="hr-panel-head"><h3>人员薪资</h3><span>按薪资降序</span></div>
      <el-table :data="salarySorted" size="small">
        <el-table-column prop="name" label="姓名" min-width="100" />
        <el-table-column prop="position" label="职位" min-width="150" />
        <el-table-column prop="company" label="公司" min-width="110" />
        <el-table-column label="月薪" width="120" align="right">
          <template #default="{ row }"><strong>{{ formatMoney(hrStore.salaryFor(row)) }}</strong></template>
        </el-table-column>
        <el-table-column label="操作" width="90">
          <template #default="{ row }">
            <el-button link type="primary" @click="openSalaryDialog(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="salaryDialogVisible" :title="`编辑薪资 - ${salaryTarget?.name || ''}`" width="380px">
      <el-form label-width="90px">
        <el-form-item label="职位">
          <el-input :model-value="salaryTarget?.position" disabled />
        </el-form-item>
        <el-form-item label="月薪（元）">
          <el-input-number v-model="salaryValue" :min="0" :step="500" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="salaryDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveSalary">保存</el-button>
      </template>
    </el-dialog>
  </VnetPage>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { ElMessage } from "element-plus";
import VnetPage from "../../components/vnet/VnetPage.vue";
import { useHrStore, type HrEmployee, type HrSettlement } from "../../stores/hr";
import { formatMoney } from "./hr-utils";

const hrStore = useHrStore();
const settleMonth = ref(new Date().toISOString().slice(0, 7));
const settlement = ref<HrSettlement | null>(null);
const syncingRates = ref(false);
const salaryDialogVisible = ref(false);
const salaryTarget = ref<HrEmployee | null>(null);
const salaryValue = ref(0);

const summary = computed(() => {
  const total = hrStore.employees.reduce((sum, emp) => sum + hrStore.salaryFor(emp), 0);
  return {
    totalEmployees: hrStore.employees.length,
    totalMonthlySalary: total,
    avgSalary: hrStore.employees.length ? Math.round(total / hrStore.employees.length) : 0,
    annualSalary: total * 12
  };
});

const companyCosts = computed(() => {
  const map = new Map<string, { company: string; count: number; totalSalary: number }>();
  for (const emp of hrStore.employees) {
    const company = emp.company || "其他";
    const current = map.get(company) || { company, count: 0, totalSalary: 0 };
    current.count += 1;
    current.totalSalary += hrStore.salaryFor(emp);
    map.set(company, current);
  }
  return Array.from(map.values());
});

const salarySorted = computed(() => hrStore.employees.slice().sort((a, b) => hrStore.salaryFor(b) - hrStore.salaryFor(a)));

const totalBase = computed(() => (settlement.value?.records || []).reduce((sum, record) => sum + record.baseSalary, 0));
const totalOtPay = computed(() => (settlement.value?.records || []).reduce((sum, record) => sum + record.otPay, 0));
const totalLvDeduct = computed(() => (settlement.value?.records || []).reduce((sum, record) => sum + record.lvDeduct, 0));
const totalNet = computed(() => (settlement.value?.records || []).reduce((sum, record) => sum + record.netPay, 0));

const buildingCosts = computed(() => {
  const map = new Map<string, { building: string; count: number; baseSalary: number; otPay: number; lvDeduct: number; netPay: number }>();
  for (const record of settlement.value?.records || []) {
    const building = hrStore.employees.find((emp) => emp.id === record.employeeId)?.building || "-";
    const current = map.get(building) || { building, count: 0, baseSalary: 0, otPay: 0, lvDeduct: 0, netPay: 0 };
    current.count += 1;
    current.baseSalary += record.baseSalary;
    current.otPay += record.otPay;
    current.lvDeduct += record.lvDeduct;
    current.netPay += record.netPay;
    map.set(building, current);
  }
  return Array.from(map.values());
});

function refreshSettlement(): void {
  if (settleMonth.value) {
    settlement.value = hrStore.getOrCalcSettlement(settleMonth.value);
  } else {
    settlement.value = null;
  }
}

function recalc(): void {
  if (!settleMonth.value) return;
  hrStore.deleteSettlement(settleMonth.value);
  refreshSettlement();
  ElMessage.success("结算已重算");
}

function syncRates(): void {
  syncingRates.value = true;
  try {
    hrStore.refreshRates();
    refreshSettlement();
    ElMessage.success("费率已同步并重新结算");
  } finally {
    syncingRates.value = false;
  }
}

function openSalaryDialog(emp: HrEmployee): void {
  salaryTarget.value = emp;
  salaryValue.value = hrStore.salaryFor(emp);
  salaryDialogVisible.value = true;
}

function saveSalary(): void {
  if (!salaryTarget.value) return;
  hrStore.updateEmployee(salaryTarget.value.id, { salary: Math.max(0, Math.round(salaryValue.value)) });
  ElMessage.success("薪资已更新");
  salaryDialogVisible.value = false;
  refreshSettlement();
}

watch(settleMonth, refreshSettlement);
onMounted(() => {
  hrStore.refreshRates();
  hrStore.load();
  refreshSettlement();
});
</script>

<style scoped>
.hr-stat-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.hr-stat {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 16px;
  border: 1px solid var(--app-line);
  border-radius: var(--app-radius);
  background: #ffffff;
  box-shadow: var(--app-shadow);
}

.hr-stat span {
  color: var(--app-text-muted);
  font-size: 12px;
}

.hr-stat strong {
  font-size: 24px;
  line-height: 1;
}

.hr-stat.primary strong,
.hr-stat.info strong {
  color: var(--app-primary);
}

.hr-stat.success strong {
  color: var(--app-success);
}

.hr-stat.warning strong {
  color: var(--app-warning);
}

.hr-stat.danger strong {
  color: var(--app-danger);
}

.hr-panel {
  overflow: hidden;
  margin-top: 16px;
  border: 1px solid var(--app-line);
  border-radius: var(--app-radius);
  background: #ffffff;
  box-shadow: var(--app-shadow);
}

.hr-panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--app-line-soft);
}

.hr-panel-head h3 {
  margin: 0;
  font-size: 15px;
}

.hr-panel-head span {
  color: var(--app-text-muted);
  font-size: 12px;
}

.hr-filter-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.hr-settle-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px;
  padding: 16px;
}

.hr-settle-card {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 14px;
  border-radius: var(--app-radius);
  background: #f8fafc;
}

.hr-settle-card span {
  color: var(--app-text-muted);
  font-size: 12px;
}

.hr-settle-card strong {
  font-size: 18px;
}

.hr-settle-card.success strong {
  color: var(--app-success);
}

.hr-settle-card.warning strong {
  color: var(--app-warning);
}

.hr-settle-card.danger strong {
  color: var(--app-danger);
}

.hr-settle-card.info strong {
  color: var(--app-primary);
}

.hr-settle-body {
  padding: 0 16px 16px;
}

.hr-panel-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.hr-money-up {
  color: var(--app-success);
  font-weight: 600;
}

.hr-money-down {
  color: var(--app-danger);
  font-weight: 600;
}

@media (max-width: 900px) {
  .hr-stat-grid,
  .hr-settle-grid,
  .hr-panel-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

.hr-rate-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  padding: 16px;
}

.hr-rate-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 14px 16px;
  border: 1px solid var(--app-line);
  border-radius: var(--app-radius);
  background: var(--app-surface-muted);
}

.hr-rate-item strong {
  font-size: 14px;
  line-height: 1.5;
}

.hr-rate-item span {
  color: var(--app-primary);
  font-size: 14px;
  font-weight: 700;
}

@media (max-width: 900px) {
  .hr-rate-grid {
    grid-template-columns: 1fr;
  }
}

:deep(.vnet-page) {
  background:
    linear-gradient(rgba(13, 32, 56, 0.55), rgba(13, 32, 56, 0.55)),
    url("/hr-bg.png") center / cover no-repeat;
  padding: 16px;
  border-radius: 12px;
  min-height: calc(100vh - 120px);
}

</style>
