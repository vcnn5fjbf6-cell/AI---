<template>
  <el-dialog v-model="visible" title="意见反馈" width="640px" class="feedback-dialog" destroy-on-close>
    <el-form label-position="top">
      <el-form-item label="反馈类型">
        <el-radio-group v-model="form.category">
          <el-radio-button label="SUGGESTION">功能建议</el-radio-button>
          <el-radio-button label="BUG">问题反馈</el-radio-button>
          <el-radio-button label="DATA">数据问题</el-radio-button>
          <el-radio-button label="OTHER">其他</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="标题">
        <el-input v-model="form.title" maxlength="100" show-word-limit placeholder="简单描述这个问题或建议" />
      </el-form-item>
      <el-form-item label="文字说明">
        <el-input
          v-model="form.content"
          type="textarea"
          :rows="5"
          maxlength="1000"
          show-word-limit
          placeholder="请写清楚你遇到的问题、期望效果或建议。文字中带 http/https 链接也会被记录。"
        />
      </el-form-item>
      <el-form-item label="相关链接">
        <el-input
          v-model="form.links"
          type="textarea"
          :rows="2"
          maxlength="1000"
          show-word-limit
          placeholder="可填写页面地址、飞书链接、商品链接等，一行一个"
        />
      </el-form-item>
      <el-form-item label="图片">
        <el-upload
          v-model:file-list="imageFiles"
          list-type="picture-card"
          accept="image/*"
          :auto-upload="false"
          :limit="5"
          :on-change="handleImageChange"
          :on-exceed="handleImageExceed"
        >
          <el-icon><Plus /></el-icon>
          <template #tip>
            <div class="upload-tip">最多 5 张，每张不超过 5MB。截图会保存到后台反馈详情。</div>
          </template>
        </el-upload>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">提交反馈</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { reactive, ref } from "vue";
import { ElMessage, type UploadFile, type UploadFiles, type UploadUserFile } from "element-plus";
import { Plus } from "@element-plus/icons-vue";
import { submitPlatformFeedbackApi } from "../../api/platform";

const visible = defineModel<boolean>({ default: false });

const form = reactive({
  category: "SUGGESTION",
  title: "",
  content: "",
  links: ""
});

const imageFiles = ref<UploadUserFile[]>([]);
const submitting = ref(false);

function handleImageChange(file: UploadFile): void {
  const raw = file.raw;
  if (!raw) {
    return;
  }
  if (!raw.type.startsWith("image/")) {
    ElMessage.warning("只能上传图片");
    imageFiles.value = imageFiles.value.filter((item) => item.uid !== file.uid);
    return;
  }
  if (raw.size > 5 * 1024 * 1024) {
    ElMessage.warning("单张图片不能超过 5MB");
    imageFiles.value = imageFiles.value.filter((item) => item.uid !== file.uid);
  }
}

function handleImageExceed(_files: File[], _uploadFiles: UploadFiles): void {
  ElMessage.warning("最多上传 5 张图片");
}

async function submit(): Promise<void> {
  const title = form.title.trim();
  const content = form.content.trim();
  const links = form.links.trim();
  const images = imageFiles.value.map((file) => file.raw).filter(Boolean) as File[];
  if (!content && !links && images.length === 0) {
    ElMessage.warning("请填写文字说明、相关链接或上传图片");
    return;
  }
  submitting.value = true;
  try {
    await submitPlatformFeedbackApi({
      category: form.category,
      title,
      content,
      links,
      pageUrl: location.href,
      images
    });
    ElMessage.success("反馈已提交");
    reset();
    visible.value = false;
  } finally {
    submitting.value = false;
  }
}

function reset(): void {
  form.category = "SUGGESTION";
  form.title = "";
  form.content = "";
  form.links = "";
  imageFiles.value = [];
}
</script>

<style scoped>
.upload-tip {
  color: var(--app-text-soft);
  font-size: 12px;
  line-height: 1.5;
  margin-top: 6px;
}

:deep(.el-upload--picture-card),
:deep(.el-upload-list--picture-card .el-upload-list__item) {
  width: 92px;
  height: 92px;
}

@media (max-width: 700px) {
  :deep(.feedback-dialog) {
    width: calc(100vw - 24px) !important;
  }
}
</style>
