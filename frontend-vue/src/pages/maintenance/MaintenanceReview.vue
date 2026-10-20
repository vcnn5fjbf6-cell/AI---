<template>
  <VnetPage
    eyebrow="AI Review"
    title="AI 拆解确认"
    :description="`解析任务 ${parseId}：对照 PDF 原文依据确认结构化字段，避免未经人工确认的数据进入台账。`"
  >
    <div class="review-grid">
      <VnetPanel title="原文证据" description="PDF 预览组件接入前，先保留页码、片段和来源说明。">
        <div class="evidence-box">
          <div class="evidence-page">PDF P.12</div>
          <p>服务范围包含亦庄A1、A2机房暖通和弱电设备，服务期自 2026-01-01 至 2026-12-31。</p>
          <el-progress :percentage="92" />
        </div>
        <el-alert type="warning" :closable="false" title="楼栋字段置信度低于 80%，需要人工确认。" />
      </VnetPanel>

      <VnetPanel title="字段确认" description="使用 ElForm 组织维保来源、订单编号、机房、专业和责任人。">
        <el-form label-position="top" :model="formModel">
          <el-row :gutter="14">
            <el-col :span="12">
              <el-form-item label="维保来源">
                <el-select v-model="formModel.sourceType">
                  <el-option label="总部框架" value="framework" />
                  <el-option label="框架订单" value="order" />
                  <el-option label="机房自采" value="local" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="框架协议编号">
                <el-input v-model="formModel.frameworkNo" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="采购订单编号">
                <el-input v-model="formModel.orderNo" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="机房">
                <el-select v-model="formModel.dataCenter">
                  <el-option v-for="item in dataCenterOptions" :key="item" :label="item" :value="item" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="专业分类">
                <el-select v-model="formModel.profession">
                  <el-option v-for="item in professionOptions" :key="item" :label="item" :value="item" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="责任人">
                <el-select v-model="formModel.owner">
                  <el-option v-for="item in personOptions" :key="item" :label="item" :value="item" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="服务开始">
                <el-date-picker v-model="formModel.startDate" type="date" value-format="YYYY-MM-DD" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="服务结束">
                <el-date-picker v-model="formModel.endDate" type="date" value-format="YYYY-MM-DD" />
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>
      </VnetPanel>
    </div>

    <VnetPanel title="设备清单" description="设备颗粒度覆盖关系确认后写入 maintenance_asset_coverage。">
      <el-table :data="assetRows.slice(0, 3)" stripe>
        <el-table-column prop="assetCode" label="设备编码" min-width="130" />
        <el-table-column prop="assetName" label="设备名称" min-width="160" />
        <el-table-column prop="building" label="楼栋" width="90" />
        <el-table-column prop="profession" label="专业" width="90" />
        <el-table-column prop="owner" label="责任人" width="90" />
        <el-table-column prop="endDate" label="服务结束" width="120" />
      </el-table>
      <div class="confirm-actions">
        <el-popconfirm title="确认字段和设备清单无误并入库？" @confirm="confirmImport">
          <template #reference>
            <el-button type="primary">确认入库</el-button>
          </template>
        </el-popconfirm>
        <el-button @click="$router.push('/maintenance/upload')">返回上传</el-button>
      </div>
    </VnetPanel>
  </VnetPage>
</template>

<script setup lang="ts">
import { reactive } from "vue";
import { useRoute } from "vue-router";
import { ElMessage } from "element-plus";
import VnetPage from "../../components/vnet/VnetPage.vue";
import VnetPanel from "../../components/vnet/VnetPanel.vue";
import { assetRows, dataCenterOptions, personOptions, professionOptions } from "../../data/maintenance";

const route = useRoute();
const parseId = String(route.params.parseId ?? "demo-parse");

const formModel = reactive({
  sourceType: "framework",
  frameworkNo: "FW-2026-001",
  orderNo: "PO-2026-088",
  dataCenter: "亦庄A1机房",
  profession: "暖通",
  owner: "张维",
  startDate: "2026-01-01",
  endDate: "2026-12-31"
});

function confirmImport(): void {
  ElMessage.success("已模拟确认入库");
}
</script>

<style scoped>
.review-grid {
  display: grid;
  grid-template-columns: minmax(320px, 0.8fr) minmax(0, 1.2fr);
  gap: 16px;
}

.evidence-box {
  display: flex;
  flex-direction: column;
  gap: 14px;
  margin-bottom: 16px;
  padding: 18px;
  border: 1px solid var(--app-line);
  border-radius: 8px;
  background: #f8fafc;
}

.evidence-page {
  width: fit-content;
  padding: 5px 10px;
  border-radius: 6px;
  background: rgba(22, 93, 255, 0.1);
  color: var(--app-primary);
  font-size: 12px;
  font-weight: 700;
}

.evidence-box p {
  margin: 0;
  line-height: 1.7;
  color: var(--app-text-muted);
}

.confirm-actions {
  display: flex;
  gap: 10px;
  margin-top: 18px;
}

@media (max-width: 1100px) {
  .review-grid {
    grid-template-columns: 1fr;
  }
}
</style>
