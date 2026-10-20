<template>
  <VnetPage
    eyebrow="Quote Comparison"
    title="供应商报价"
    description="报价清单和比价结果集中展示，采购员可快速识别最低价、交期和报价状态。"
  >
    <VnetPanel title="报价列表">
      <el-table :data="quoteRows" stripe>
        <el-table-column prop="reqNo" label="申请单号" min-width="140" />
        <el-table-column prop="reqTitle" label="申请标题" min-width="240" />
        <el-table-column prop="supplierName" label="供应商" min-width="220" />
        <el-table-column label="报价金额" width="130"><template #default="{ row }">{{ formatMoney(row.quoteAmount) }}</template></el-table-column>
        <el-table-column prop="leadTimeDays" label="交期(天)" width="90" />
        <el-table-column prop="status" label="状态" width="90" />
      </el-table>
    </VnetPanel>

    <VnetPanel title="比价摘要" description="正式接入后使用 SupplierQuoteSummaryVO 聚合最低价、供应商数量和报价明细。">
      <div class="quote-summary">
        <div>
          <span>最低报价</span>
          <strong>{{ formatMoney(lowestQuote.quoteAmount) }}</strong>
        </div>
        <div>
          <span>最低价供应商</span>
          <strong>{{ lowestQuote.supplierName }}</strong>
        </div>
        <div>
          <span>最短交期</span>
          <strong>{{ fastestLeadTime }} 天</strong>
        </div>
      </div>
    </VnetPanel>
  </VnetPage>
</template>

<script setup lang="ts">
import { computed } from "vue";
import VnetPage from "../../components/vnet/VnetPage.vue";
import VnetPanel from "../../components/vnet/VnetPanel.vue";
import { quoteRows } from "../../data/procurement";

const lowestQuote = computed(() => [...quoteRows].sort((a, b) => a.quoteAmount - b.quoteAmount)[0]).value;
const fastestLeadTime = computed(() => Math.min(...quoteRows.map((row) => row.leadTimeDays))).value;

function formatMoney(value: number): string {
  return Number(value).toLocaleString("zh-CN", { style: "currency", currency: "CNY", maximumFractionDigits: 0 });
}
</script>

<style scoped>
.quote-summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.quote-summary div {
  padding: 18px;
  border: 1px solid var(--app-line-soft);
  border-radius: var(--app-radius);
  background: var(--app-surface-muted);
}

.quote-summary span,
.quote-summary strong {
  display: block;
}

.quote-summary span {
  color: var(--app-text-muted);
  font-size: 13px;
}

.quote-summary strong {
  margin-top: 8px;
  color: var(--app-text);
  font-size: 20px;
}

@media (max-width: 860px) {
  .quote-summary {
    grid-template-columns: 1fr;
  }
}
</style>
