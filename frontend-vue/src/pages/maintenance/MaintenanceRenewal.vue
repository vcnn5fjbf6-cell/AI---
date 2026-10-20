<template>
  <VnetPage
    eyebrow="Renewal Tracking"
    title="续签 / 续采跟踪"
    description="跟踪临期框架、订单和自采项目，按提醒规则推进责任人确认、续签、续采和超期升级。"
  >
    <div class="renewal-grid">
      <VnetPanel title="任务流" description="正式接入后替换为 ElFlowTask / ElFlowTaskChart。">
        <el-timeline>
          <el-timeline-item timestamp="180 / 90 天" type="primary">总部框架续签预警，确认预算和服务范围。</el-timeline-item>
          <el-timeline-item timestamp="60 / 30 / 7 天" type="warning">框架订单续采预警，锁定采购单和供应商报价。</el-timeline-item>
          <el-timeline-item timestamp="30 / 15 / 7 天" type="danger">自采项目临期预警，逾期升级机房负责人和区域负责人。</el-timeline-item>
        </el-timeline>
      </VnetPanel>

      <VnetPanel title="提醒设置" description="提醒弹窗使用 ElDialog，后续可接飞书提醒任务。">
        <el-form label-position="top" :model="reminderForm">
          <el-form-item label="责任人">
            <el-select v-model="reminderForm.owner">
              <el-option v-for="item in personOptions" :key="item" :label="item" :value="item" />
            </el-select>
          </el-form-item>
          <el-form-item label="提醒内容">
            <el-input v-model="reminderForm.content" type="textarea" :rows="4" />
          </el-form-item>
          <el-button type="primary" @click="reminderVisible = true">预览提醒</el-button>
        </el-form>
      </VnetPanel>
    </div>

    <VnetPanel title="续签 / 续采任务列表" description="高优先级任务优先升级给机房负责人和区域负责人。">
      <el-table :data="renewalRows" stripe>
        <el-table-column prop="taskNo" label="任务编号" min-width="130" />
        <el-table-column prop="targetName" label="目标对象" min-width="220" />
        <el-table-column label="来源类型" width="110">
          <template #default="{ row }">
            <VnetStatusTag :source-type="row.sourceType" />
          </template>
        </el-table-column>
        <el-table-column prop="owner" label="责任人" width="90" />
        <el-table-column prop="dueDate" label="到期日" width="120" />
        <el-table-column prop="reminderRule" label="提醒规则" min-width="160" />
        <el-table-column label="优先级" width="90">
          <template #default="{ row }">
            <VnetStatusTag :priority="row.priority" />
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100" />
      </el-table>
    </VnetPanel>

    <el-dialog v-model="reminderVisible" title="提醒预览" width="560px">
      <p class="reminder-preview">{{ reminderForm.content }}</p>
      <template #footer>
        <el-button @click="reminderVisible = false">关闭</el-button>
        <el-button type="primary" @click="reminderVisible = false">确认发送</el-button>
      </template>
    </el-dialog>
  </VnetPage>
</template>

<script setup lang="ts">
import { reactive, ref } from "vue";
import VnetPage from "../../components/vnet/VnetPage.vue";
import VnetPanel from "../../components/vnet/VnetPanel.vue";
import VnetStatusTag from "../../components/vnet/VnetStatusTag.vue";
import { personOptions, renewalRows } from "../../data/maintenance";

const reminderVisible = ref(false);
const reminderForm = reactive({
  owner: "张维",
  content: "您负责的维保来源即将到期，请确认续签或续采计划，并补充合同 / 订单依据。"
});
</script>

<style scoped>
.renewal-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(320px, 0.72fr);
  gap: 16px;
}

.reminder-preview {
  margin: 0;
  line-height: 1.8;
  color: var(--app-text-muted);
}

@media (max-width: 980px) {
  .renewal-grid {
    grid-template-columns: 1fr;
  }
}
</style>
