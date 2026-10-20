<template>
  <el-dialog
    v-model="visible"
    title="修改密码"
    width="460px"
    destroy-on-close
    :close-on-click-modal="!required"
    :show-close="!required"
  >
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-width="96px"
      class="password-form"
    >
      <el-form-item label="当前密码" prop="oldPassword">
        <el-input v-model="form.oldPassword" type="password" show-password autocomplete="current-password" />
      </el-form-item>
      <el-form-item label="新密码" prop="newPassword">
        <el-input v-model="form.newPassword" type="password" show-password autocomplete="new-password" />
      </el-form-item>
      <el-form-item label="确认密码" prop="confirmPassword">
        <el-input v-model="form.confirmPassword" type="password" show-password autocomplete="new-password" />
      </el-form-item>
      <div class="password-hint">
        密码每 90 天需要修改一次，新密码不能与最近 6 次使用过的密码重复。
      </div>
    </el-form>
    <template #footer>
      <el-button v-if="!required" @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">确认修改</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from "vue";
import { useRouter } from "vue-router";
import { ElMessage, type FormInstance, type FormRules } from "element-plus";
import { changePasswordApi } from "../../api/auth";
import { useAuthStore } from "../../stores/auth";
import type { ChangePasswordParams } from "../../types/auth";

const props = defineProps<{
  modelValue: boolean;
  required?: boolean;
}>();

const emit = defineEmits<{
  (event: "update:modelValue", value: boolean): void;
}>();

const router = useRouter();
const authStore = useAuthStore();
const formRef = ref<FormInstance>();
const submitting = ref(false);

const form = reactive<ChangePasswordParams>({
  oldPassword: "",
  newPassword: "",
  confirmPassword: ""
});

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit("update:modelValue", value)
});

const rules: FormRules<ChangePasswordParams> = {
  oldPassword: [{ required: true, message: "请输入当前密码", trigger: "blur" }],
  newPassword: [
    { required: true, message: "请输入新密码", trigger: "blur" },
    { min: 6, max: 128, message: "新密码长度需为 6-128 位", trigger: "blur" },
    {
      validator: (_rule, value: string) => {
        if (value && value === form.oldPassword) {
          return new Error("新密码不能与当前密码一致");
        }
        return true;
      },
      trigger: "blur"
    }
  ],
  confirmPassword: [
    { required: true, message: "请再次输入新密码", trigger: "blur" },
    {
      validator: (_rule, value: string) => {
        if (value && value !== form.newPassword) {
          return new Error("两次输入的新密码不一致");
        }
        return true;
      },
      trigger: "blur"
    }
  ]
};

function resetForm(): void {
  form.oldPassword = "";
  form.newPassword = "";
  form.confirmPassword = "";
  setTimeout(() => formRef.value?.clearValidate(), 0);
}

async function submit(): Promise<void> {
  if (!formRef.value) return;
  const valid = await formRef.value.validate().catch(() => false);
  if (!valid) return;
  submitting.value = true;
  try {
    await changePasswordApi({
      oldPassword: form.oldPassword,
      newPassword: form.newPassword,
      confirmPassword: form.confirmPassword
    });
    ElMessage.success("密码已修改，请重新登录");
    visible.value = false;
    authStore.logoutLocal();
    await router.push("/login");
  } finally {
    submitting.value = false;
  }
}

watch(
  () => props.modelValue,
  (value) => {
    if (value) {
      resetForm();
    }
  }
);
</script>

<style scoped>
.password-form {
  padding: 4px 4px 0;
}

.password-hint {
  margin-left: 96px;
  color: #71829d;
  font-size: 12px;
  line-height: 1.6;
}
</style>
