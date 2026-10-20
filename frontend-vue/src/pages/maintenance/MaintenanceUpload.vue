<template>
  <VnetPage
    eyebrow="Document Intake"
    title="合同 / 订单上传"
    description="上传总部框架、框架采购订单、机房自采合同或维修项目材料，进入 AI 拆解和人工确认流程。"
  >
    <div class="upload-grid">
      <VnetPanel title="上传材料" description="使用 ElUpload / ElRadioGroup / ElSteps 组织标准上传入口。">
        <el-form label-position="top">
          <el-form-item label="上传类型">
            <el-radio-group v-model="uploadType">
              <el-radio-button label="framework">总部框架协议</el-radio-button>
              <el-radio-button label="order">框架采购订单</el-radio-button>
              <el-radio-button label="local">机房自采合同 / 订单 / 维修项目</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="材料文件">
            <el-upload drag action="#" :auto-upload="false" :limit="1" :on-change="handleFileChange">
              <div class="upload-copy">
                <strong>拖拽 PDF / Word 到此处</strong>
                <span>或点击选择文件，后续接入真实解析接口</span>
              </div>
            </el-upload>
          </el-form-item>
          <el-alert
            type="info"
            :closable="false"
            title="解析结果会保留原文页码、证据片段和字段置信度，人工确认后再入库。"
          />
        </el-form>
      </VnetPanel>

      <VnetPanel title="解析步骤" description="失败时通过 ElAlert 展示异常，成功后进入 AI 拆解确认。">
        <el-steps :active="activeStep" finish-status="success" direction="vertical">
          <el-step title="上传材料" description="选择合同、订单或自采项目材料" />
          <el-step title="AI 拆解" description="识别来源、机房、专业、设备清单和有效期" />
          <el-step title="人工确认" description="校验字段、证据片段和置信度" />
          <el-step title="确认入库" description="写入维保来源和设备覆盖关系" />
        </el-steps>
        <div class="upload-actions">
          <el-button type="primary" @click="openResult">查看模拟解析结果</el-button>
          <el-button @click="$router.push('/maintenance/review/demo-parse')">进入确认页</el-button>
        </div>
      </VnetPanel>
    </div>

    <el-dialog v-model="resultVisible" title="解析结果预览" width="880px">
      <el-table :data="assetRows.slice(0, 3)" stripe>
        <el-table-column prop="assetCode" label="设备编码" min-width="130" />
        <el-table-column prop="assetName" label="设备名称" min-width="160" />
        <el-table-column prop="dataCenter" label="机房" min-width="150" />
        <el-table-column prop="profession" label="专业" width="90" />
        <el-table-column prop="sourceNo" label="来源编号" min-width="140" />
      </el-table>
      <template #footer>
        <el-button @click="resultVisible = false">关闭</el-button>
        <el-button type="primary" @click="$router.push('/maintenance/review/demo-parse')">人工确认</el-button>
      </template>
    </el-dialog>
  </VnetPage>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { ElMessage } from "element-plus";
import VnetPage from "../../components/vnet/VnetPage.vue";
import VnetPanel from "../../components/vnet/VnetPanel.vue";
import { assetRows } from "../../data/maintenance";

const uploadType = ref("framework");
const activeStep = ref(1);
const resultVisible = ref(false);

function handleFileChange(): void {
  activeStep.value = 2;
  ElMessage.success("文件已进入模拟解析队列");
}

function openResult(): void {
  activeStep.value = 3;
  resultVisible.value = true;
}
</script>

<style scoped>
.upload-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(320px, 0.85fr);
  gap: 16px;
}

.upload-copy {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 22px;
  color: var(--app-text-muted);
}

.upload-copy strong {
  color: var(--app-text);
}

.upload-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 24px;
}

@media (max-width: 980px) {
  .upload-grid {
    grid-template-columns: 1fr;
  }
}
</style>
