<template>
  <VnetPage title="资产目录管理">
    <template #actions>
      <el-button :icon="Download" :loading="exporting" @click="exportTemplate">导出维护表</el-button>
      <el-button :icon="Upload" :loading="importing" @click="openImport">批量导入</el-button>
      <el-button :icon="Refresh" :loading="loading" @click="loadPage">刷新</el-button>
      <el-button type="primary" :icon="Plus" @click="openCreate">新增目录项</el-button>
      <input ref="importInput" class="hidden-file-input" type="file" accept=".xlsx,.xls" @change="handleImport" />
    </template>

    <VnetPanel title="目录清单">
      <template #actions>
        <span class="result-count">共 {{ total }} 条</span>
      </template>

      <div class="catalog-filters">
        <el-input
          v-model="query.keyword"
          :prefix-icon="Search"
          clearable
          placeholder="搜索资产名称、目录编码或大类"
          @keyup.enter="search"
        />
        <el-select v-model="query.enabled" clearable placeholder="全部状态" @change="search">
          <el-option label="启用" :value="1" />
          <el-option label="停用" :value="0" />
        </el-select>
        <el-button type="primary" :icon="Search" @click="search">查询</el-button>
        <el-button @click="resetQuery">重置</el-button>
      </div>

      <el-table v-loading="loading" :data="records" stripe empty-text="暂无符合条件的资产目录">
        <el-table-column label="状态" width="86">
          <template #default="{ row }">
            <el-tag :type="row.enabled === 1 ? 'success' : 'info'" effect="plain">
              {{ row.enabled === 1 ? "启用" : "停用" }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="目录编码" min-width="150">
          <template #default="{ row }">
            <div class="primary-text">{{ row.categoryCode }} / {{ row.assetNameCode }}</div>
            <div class="secondary-text">{{ row.categoryName }}</div>
          </template>
        </el-table-column>
        <el-table-column label="资产名称" min-width="180">
          <template #default="{ row }">
            <div class="primary-text">{{ row.assetName }}</div>
            <div class="secondary-text">{{ row.unit || "未设置单位" }}</div>
          </template>
        </el-table-column>
        <el-table-column label="折旧" min-width="130">
          <template #default="{ row }">
            <div>{{ row.depreciationYears || "-" }} 年</div>
            <div class="secondary-text">{{ row.depreciationMonths ? `${row.depreciationMonths} 个月` : "未设置月数" }}</div>
          </template>
        </el-table-column>
        <el-table-column label="实物管理" min-width="180">
          <template #default="{ row }">
            <div>{{ row.inventoryMethod || "-" }}</div>
            <div class="secondary-text">编号 {{ row.physicalNumber || "-" }} · {{ row.physicalLabel || "无标签要求" }}</div>
          </template>
        </el-table-column>
        <el-table-column label="来源" min-width="150">
          <template #default="{ row }">
            <div>{{ row.sourceVersion || "人工维护" }}</div>
            <div class="secondary-text">{{ formatDateTime(row.updatedAt) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="128" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link :type="row.enabled === 1 ? 'danger' : 'success'" @click="toggleEnabled(row)">
              {{ row.enabled === 1 ? "停用" : "启用" }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="catalog-pagination">
        <el-pagination
          v-model:current-page="query.pageNo"
          v-model:page-size="query.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="loadPage"
          @size-change="handlePageSizeChange"
        />
      </div>
    </VnetPanel>

    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? '编辑资产目录项' : '新增资产目录项'"
      width="min(880px, calc(100vw - 32px))"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <div class="form-grid">
          <el-form-item label="资产大类编码" prop="categoryCode">
            <el-input v-model="form.categoryCode" maxlength="32" />
          </el-form-item>
          <el-form-item label="资产大类名称" prop="categoryName">
            <el-input v-model="form.categoryName" maxlength="255" />
          </el-form-item>
          <el-form-item label="资产名称编码" prop="assetNameCode">
            <el-input v-model="form.assetNameCode" maxlength="32" />
          </el-form-item>
          <el-form-item label="资产名称" prop="assetName">
            <el-input v-model="form.assetName" maxlength="255" />
          </el-form-item>
          <el-form-item label="折旧年限">
            <el-input v-model="form.depreciationYears" maxlength="64" />
          </el-form-item>
          <el-form-item label="折旧月数">
            <el-input v-model="form.depreciationMonths" maxlength="64" />
          </el-form-item>
          <el-form-item label="单位">
            <el-input v-model="form.unit" maxlength="64" />
          </el-form-item>
          <el-form-item label="盘点方式">
            <el-input v-model="form.inventoryMethod" maxlength="128" />
          </el-form-item>
          <el-form-item label="实物编号">
            <el-input v-model="form.physicalNumber" maxlength="64" />
          </el-form-item>
          <el-form-item label="实物标签">
            <el-input v-model="form.physicalLabel" maxlength="128" />
          </el-form-item>
          <el-form-item label="变更标识">
            <el-input v-model="form.changeMark" maxlength="255" />
          </el-form-item>
          <el-form-item label="状态" prop="enabled">
            <el-radio-group v-model="form.enabled">
              <el-radio-button :value="1">启用</el-radio-button>
              <el-radio-button :value="0">停用</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="备注" class="full-width">
            <el-input v-model="form.remark" type="textarea" :rows="3" maxlength="1000" show-word-limit />
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </VnetPage>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { Download, Plus, Refresh, Search, Upload } from "@element-plus/icons-vue";
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from "element-plus";
import VnetPage from "../../components/vnet/VnetPage.vue";
import VnetPanel from "../../components/vnet/VnetPanel.vue";
import {
  createAssetCatalogItemApi,
  exportAssetCatalogTemplateApi,
  getAssetCatalogPageApi,
  importAssetCatalogTemplateApi,
  updateAssetCatalogItemApi,
  updateAssetCatalogItemEnabledApi
} from "../../api/platform";
import type { AssetCatalogForm, AssetCatalogItemVO, AssetCatalogQuery } from "../../types/platform";

const loading = ref(false);
const saving = ref(false);
const exporting = ref(false);
const importing = ref(false);
const records = ref<AssetCatalogItemVO[]>([]);
const total = ref(0);
const dialogVisible = ref(false);
const editingId = ref<number | null>(null);
const formRef = ref<FormInstance>();
const importInput = ref<HTMLInputElement>();

const query = reactive<AssetCatalogQuery>({ pageNo: 1, pageSize: 20, keyword: "" });
const emptyForm = (): AssetCatalogForm => ({
  categoryCode: "",
  categoryName: "",
  assetNameCode: "",
  assetName: "",
  depreciationYears: "",
  depreciationMonths: "",
  unit: "",
  inventoryMethod: "",
  physicalNumber: "",
  physicalLabel: "",
  remark: "",
  changeMark: "",
  enabled: 1
});
const form = reactive<AssetCatalogForm>(emptyForm());
const rules: FormRules<AssetCatalogForm> = {
  categoryCode: [{ required: true, message: "请输入资产大类编码", trigger: "blur" }],
  categoryName: [{ required: true, message: "请输入资产大类名称", trigger: "blur" }],
  assetNameCode: [{ required: true, message: "请输入资产名称编码", trigger: "blur" }],
  assetName: [{ required: true, message: "请输入资产名称", trigger: "blur" }],
  enabled: [{ required: true, message: "请选择状态", trigger: "change" }]
};

onMounted(loadPage);

async function loadPage(): Promise<void> {
  loading.value = true;
  try {
    const result = await getAssetCatalogPageApi(query);
    records.value = result.records || [];
    total.value = result.total || 0;
  } finally {
    loading.value = false;
  }
}

function search(): void {
  query.pageNo = 1;
  void loadPage();
}

function resetQuery(): void {
  query.keyword = "";
  query.enabled = undefined;
  query.pageNo = 1;
  void loadPage();
}

function handlePageSizeChange(): void {
  query.pageNo = 1;
  void loadPage();
}

function assignForm(value: AssetCatalogForm): void {
  Object.assign(form, emptyForm(), value);
}

function openCreate(): void {
  editingId.value = null;
  assignForm(emptyForm());
  dialogVisible.value = true;
}

function openEdit(row: AssetCatalogItemVO): void {
  editingId.value = row.id;
  assignForm({
    categoryCode: row.categoryCode,
    categoryName: row.categoryName,
    assetNameCode: row.assetNameCode,
    assetName: row.assetName,
    depreciationYears: row.depreciationYears || "",
    depreciationMonths: row.depreciationMonths || "",
    unit: row.unit || "",
    inventoryMethod: row.inventoryMethod || "",
    physicalNumber: row.physicalNumber || "",
    physicalLabel: row.physicalLabel || "",
    remark: row.remark || "",
    changeMark: row.changeMark || "",
    enabled: row.enabled
  });
  dialogVisible.value = true;
}

async function save(): Promise<void> {
  if (!formRef.value) return;
  await formRef.value.validate();
  saving.value = true;
  try {
    if (editingId.value) {
      await updateAssetCatalogItemApi(editingId.value, form);
    } else {
      await createAssetCatalogItemApi(form);
    }
    ElMessage.success("资产目录已保存");
    dialogVisible.value = false;
    await loadPage();
  } finally {
    saving.value = false;
  }
}

async function toggleEnabled(row: AssetCatalogItemVO): Promise<void> {
  const enabled = row.enabled === 1 ? 0 : 1;
  await ElMessageBox.confirm(
    `${enabled === 1 ? "启用" : "停用"}“${row.assetName}”后，将立即影响后续报价的资产目录匹配。`,
    `${enabled === 1 ? "启用" : "停用"}目录项`,
    { type: "warning", confirmButtonText: "确认", cancelButtonText: "取消" }
  );
  await updateAssetCatalogItemEnabledApi(row.id, enabled);
  ElMessage.success(enabled === 1 ? "目录项已启用" : "目录项已停用");
  await loadPage();
}

async function exportTemplate(): Promise<void> {
  exporting.value = true;
  try {
    downloadBlob(await exportAssetCatalogTemplateApi(), "资产目录批量维护模板.xlsx");
  } finally {
    exporting.value = false;
  }
}

function openImport(): void {
  importInput.value?.click();
}

async function handleImport(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  input.value = "";
  if (!file) return;
  if (!/\.(xlsx|xls)$/i.test(file.name)) {
    ElMessage.warning("请上传 Excel 文件");
    return;
  }
  importing.value = true;
  try {
    const count = await importAssetCatalogTemplateApi(file);
    ElMessage.success(`已导入或更新 ${count} 条资产目录`);
    query.pageNo = 1;
    await loadPage();
  } finally {
    importing.value = false;
  }
}

function downloadBlob(blob: Blob, fileName: string): void {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = fileName;
  anchor.click();
  URL.revokeObjectURL(url);
}

function formatDateTime(value?: string | null): string {
  if (!value) return "未记录更新时间";
  return value.replace("T", " ").slice(0, 16);
}
</script>

<style scoped>
.hidden-file-input {
  display: none;
}

.result-count,
.secondary-text {
  color: #7c8da6;
  font-size: 12px;
}

.catalog-filters {
  display: grid;
  grid-template-columns: minmax(280px, 1fr) 160px auto auto;
  gap: 10px;
  margin-bottom: 14px;
}

.primary-text {
  color: #172b4d;
  font-weight: 600;
}

.secondary-text {
  margin-top: 4px;
  line-height: 1.5;
}

.catalog-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 18px;
}

.full-width {
  grid-column: 1 / -1;
}

@media (max-width: 760px) {
  .catalog-filters,
  .form-grid {
    grid-template-columns: 1fr;
  }

  .full-width {
    grid-column: auto;
  }

  .catalog-pagination {
    justify-content: flex-start;
    overflow-x: auto;
  }
}
</style>
