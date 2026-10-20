<template>
  <VnetPage
    eyebrow="Permission Settings"
    title="人员信息配置"
    description="维护人员角色、总部权限、控制机房和覆盖专业，是后续合同可见性和台账权限的基础。"
  >
    <template #actions>
      <el-button type="primary" @click="openCreate">新增人员权限</el-button>
    </template>

    <VnetPanel title="人员列表" description="人员选择后续可替换为 ElSelectMaPerson / ElSelectDistinctPerson。">
      <el-table :data="rows" stripe>
        <el-table-column prop="personName" label="人员" width="110" />
        <el-table-column prop="role" label="角色" width="130" />
        <el-table-column label="总部权限" width="110">
          <template #default="{ row }">
            <el-switch v-model="row.headquartersAccess" />
          </template>
        </el-table-column>
        <el-table-column label="控制机房" min-width="220">
          <template #default="{ row }">
            <el-tag v-for="room in row.rooms" :key="room" class="inline-tag" effect="plain">{{ room }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="专业分类" min-width="180">
          <template #default="{ row }">
            <el-tag v-for="item in row.professions" :key="item" class="inline-tag" type="success" effect="plain">{{ item }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-popconfirm title="确认删除该人员配置？" @confirm="removeRow(row.id)">
              <template #reference>
                <el-button link type="danger">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </VnetPanel>

    <el-dialog v-model="dialogVisible" :title="dialogMode === 'create' ? '新增人员权限' : '编辑人员权限'" width="720px">
      <el-form label-position="top" :model="formModel">
        <el-row :gutter="14">
          <el-col :span="12">
            <el-form-item label="人员">
              <el-select v-model="formModel.personName">
                <el-option v-for="item in personOptions" :key="item" :label="item" :value="item" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="角色">
              <el-select v-model="formModel.role">
                <el-option label="运维人员" value="运维人员" />
                <el-option label="机房负责人" value="机房负责人" />
                <el-option label="区域负责人" value="区域负责人" />
                <el-option label="系统管理员" value="系统管理员" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="总部权限">
              <el-switch v-model="formModel.headquartersAccess" active-text="可查看所有合同" inactive-text="按机房授权" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="控制机房">
              <el-select v-model="formModel.rooms" multiple collapse-tags placeholder="请选择机房">
                <el-option v-for="item in dataCenterOptions" :key="item" :label="item" :value="item" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="专业分类">
              <el-checkbox-group v-model="formModel.professions">
                <el-checkbox v-for="item in professionOptions" :key="item" :label="item" :value="item" />
              </el-checkbox-group>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveRow">保存</el-button>
      </template>
    </el-dialog>
  </VnetPage>
</template>

<script setup lang="ts">
import { reactive, ref } from "vue";
import VnetPage from "../../../components/vnet/VnetPage.vue";
import VnetPanel from "../../../components/vnet/VnetPanel.vue";
import {
  dataCenterOptions,
  permissionRows,
  personOptions,
  professionOptions,
  type PermissionPerson
} from "../../../data/maintenance";

const rows = ref<PermissionPerson[]>(permissionRows.map((item) => ({ ...item, rooms: [...item.rooms], professions: [...item.professions] })));
const dialogVisible = ref(false);
const dialogMode = ref<"create" | "edit">("create");
const currentId = ref<number | null>(null);

const formModel = reactive<PermissionPerson>({
  id: 0,
  personName: "张维",
  role: "运维人员",
  headquartersAccess: false,
  rooms: [],
  professions: []
});

function assignForm(row?: PermissionPerson): void {
  Object.assign(formModel, {
    id: row?.id ?? 0,
    personName: row?.personName ?? "张维",
    role: row?.role ?? "运维人员",
    headquartersAccess: row?.headquartersAccess ?? false,
    rooms: row ? [...row.rooms] : [],
    professions: row ? [...row.professions] : []
  });
}

function openCreate(): void {
  dialogMode.value = "create";
  currentId.value = null;
  assignForm();
  dialogVisible.value = true;
}

function openEdit(row: PermissionPerson): void {
  dialogMode.value = "edit";
  currentId.value = row.id;
  assignForm(row);
  dialogVisible.value = true;
}

function saveRow(): void {
  if (dialogMode.value === "create") {
    rows.value.push({ ...formModel, id: Date.now(), rooms: [...formModel.rooms], professions: [...formModel.professions] });
  } else if (currentId.value) {
    const index = rows.value.findIndex((item) => item.id === currentId.value);
    if (index >= 0) {
      rows.value[index] = { ...formModel, id: currentId.value, rooms: [...formModel.rooms], professions: [...formModel.professions] };
    }
  }
  dialogVisible.value = false;
}

function removeRow(id: number): void {
  rows.value = rows.value.filter((item) => item.id !== id);
}
</script>
