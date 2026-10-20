<template>
  <VnetPage
    eyebrow="Room Category"
    title="机房分类配置"
    description="维护片区、机房、订单机房名称、机房性质、区域负责人和覆盖专业，作为台账和权限的数据底座。"
  >
    <template #actions>
      <el-button type="primary" @click="openCreate">新增机房分类</el-button>
    </template>

    <VnetPanel title="区域筛选">
      <el-form :inline="true" :model="filters" class="vnet-search-form">
        <el-form-item label="所属区域">
          <el-select v-model="filters.region" clearable placeholder="全部区域">
            <el-option label="北京" value="北京" />
            <el-option label="河北" value="河北" />
          </el-select>
        </el-form-item>
        <el-form-item label="专业分类">
          <el-select v-model="filters.profession" clearable placeholder="全部专业">
            <el-option v-for="item in professionOptions" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>
    </VnetPanel>

    <VnetPanel title="机房分类表" :description="`当前共 ${filteredRows.length} 条机房分类。`">
      <el-table :data="filteredRows" stripe>
        <el-table-column prop="area" label="片区" width="110" />
        <el-table-column prop="dataCenter" label="机房" min-width="150" />
        <el-table-column prop="building" label="楼栋" width="90" />
        <el-table-column prop="orderRoomName" label="订单机房名称" min-width="190" />
        <el-table-column prop="roomNature" label="性质" width="80" />
        <el-table-column prop="region" label="区域" width="80" />
        <el-table-column prop="roomType" label="类型" width="90" />
        <el-table-column prop="regionOwner" label="区域负责人" width="110" />
        <el-table-column prop="roomOwner" label="机房负责人" width="110" />
        <el-table-column label="覆盖专业" min-width="180">
          <template #default="{ row }">
            <el-tag v-for="item in row.professions" :key="item" class="inline-tag" effect="plain">{{ item }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-popconfirm title="确认删除该机房分类？" @confirm="removeRow(row.id)">
              <template #reference>
                <el-button link type="danger">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </VnetPanel>

    <el-dialog v-model="dialogVisible" :title="dialogMode === 'create' ? '新增机房分类' : '编辑机房分类'" width="860px">
      <el-form label-position="top" :model="formModel">
        <el-row :gutter="14">
          <el-col :span="8"><el-form-item label="片区"><el-input v-model="formModel.area" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="机房"><el-input v-model="formModel.dataCenter" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="楼栋"><el-input v-model="formModel.building" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="订单机房名称"><el-input v-model="formModel.orderRoomName" /></el-form-item></el-col>
          <el-col :span="6">
            <el-form-item label="机房性质">
              <el-select v-model="formModel.roomNature">
                <el-option label="自建" value="自建" />
                <el-option label="代维" value="代维" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="机房类型">
              <el-select v-model="formModel.roomType">
                <el-option label="园区型" value="园区型" />
                <el-option label="单体型" value="单体型" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8"><el-form-item label="所属区域"><el-input v-model="formModel.region" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="区域负责人"><el-input v-model="formModel.regionOwner" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="机房负责人"><el-input v-model="formModel.roomOwner" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="楼长"><el-input v-model="formModel.floorLeader" /></el-form-item></el-col>
          <el-col :span="16"><el-form-item label="机房业态"><el-input v-model="formModel.businessType" /></el-form-item></el-col>
          <el-col :span="24">
            <el-form-item label="覆盖专业">
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
import { computed, reactive, ref } from "vue";
import VnetPage from "../../../components/vnet/VnetPage.vue";
import VnetPanel from "../../../components/vnet/VnetPanel.vue";
import { professionOptions, roomRows, type RoomCategory } from "../../../data/maintenance";

const rows = ref<RoomCategory[]>(roomRows.map((item) => ({ ...item, professions: [...item.professions] })));
const dialogVisible = ref(false);
const dialogMode = ref<"create" | "edit">("create");
const currentId = ref<number | null>(null);

const filters = reactive({
  region: "",
  profession: ""
});

const formModel = reactive<RoomCategory>({
  id: 0,
  area: "",
  dataCenter: "",
  building: "",
  orderRoomName: "",
  roomNature: "自建",
  region: "",
  roomType: "园区型",
  regionOwner: "",
  roomOwner: "",
  floorLeader: "",
  businessType: "",
  professions: []
});

const filteredRows = computed(() =>
  rows.value.filter((row) => {
    const matchRegion = !filters.region || row.region === filters.region;
    const matchProfession = !filters.profession || row.professions.includes(filters.profession);
    return matchRegion && matchProfession;
  })
);

function resetFilters(): void {
  filters.region = "";
  filters.profession = "";
}

function assignForm(row?: RoomCategory): void {
  Object.assign(formModel, {
    id: row?.id ?? 0,
    area: row?.area ?? "",
    dataCenter: row?.dataCenter ?? "",
    building: row?.building ?? "",
    orderRoomName: row?.orderRoomName ?? "",
    roomNature: row?.roomNature ?? "自建",
    region: row?.region ?? "",
    roomType: row?.roomType ?? "园区型",
    regionOwner: row?.regionOwner ?? "",
    roomOwner: row?.roomOwner ?? "",
    floorLeader: row?.floorLeader ?? "",
    businessType: row?.businessType ?? "",
    professions: row ? [...row.professions] : []
  });
}

function openCreate(): void {
  dialogMode.value = "create";
  currentId.value = null;
  assignForm();
  dialogVisible.value = true;
}

function openEdit(row: RoomCategory): void {
  dialogMode.value = "edit";
  currentId.value = row.id;
  assignForm(row);
  dialogVisible.value = true;
}

function saveRow(): void {
  if (dialogMode.value === "create") {
    rows.value.push({ ...formModel, id: Date.now(), professions: [...formModel.professions] });
  } else if (currentId.value) {
    const index = rows.value.findIndex((item) => item.id === currentId.value);
    if (index >= 0) {
      rows.value[index] = { ...formModel, id: currentId.value, professions: [...formModel.professions] };
    }
  }
  dialogVisible.value = false;
}

function removeRow(id: number): void {
  rows.value = rows.value.filter((item) => item.id !== id);
}
</script>
