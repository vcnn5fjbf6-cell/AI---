import fs from "node:fs";
import path from "node:path";
import process from "node:process";

const root = process.cwd();
const read = (file) => fs.readFileSync(path.join(root, file), "utf8");
const component = read("src/pages/outsourcing/EngineeringOutsourcingWorkbench.vue");
const dashboard = read("src/pages/outsourcing/ITOutsourcingDashboard.vue");
const workbench = read("src/pages/workbench/WorkbenchPage.vue");
const styles = read("src/pages/outsourcing/EngineeringOutsourcingWorkbench.css");
const menu = read("src/config/menu.ts");
const router = read("src/router/index.ts");

const forbiddenShellMarkers = [
  'class="module-sidebar"',
  'class="module-topbar"',
  'class="portal-switch"',
  'class="module-account"'
];

for (const marker of forbiddenShellMarkers) {
  if (component.includes(marker) || styles.includes(marker.replace('class="', ".").replace('"', ""))) {
    throw new Error(`工程外包内容区仍包含重复系统外壳：${marker}`);
  }
}

const internalPaths = [
  "workbench",
  "orders",
  "entry",
  "acceptance",
  "settlement",
  "providers",
  "catalog",
  "config",
  "analytics",
  "settings"
];

for (const route of internalPaths) {
  const fullPath = `/engineering-outsourcing/${route}`;
  if (!menu.includes(fullPath)) throw new Error(`工程外包主菜单缺少：${fullPath}`);
  if (!router.includes(fullPath)) throw new Error(`工程外包路由缺少：${fullPath}`);
}

for (const supplierPath of ["workload", "acceptance-results", "profile"]) {
  const fullPath = `/engineering-outsourcing/${supplierPath}`;
  if (!menu.includes(fullPath) || !router.includes(fullPath)) {
    throw new Error(`工程外包供应商入口缺少：${fullPath}`);
  }
}

if (!component.includes("useRoute()") || !component.includes("goPage(targetPage")) {
  throw new Error("工程外包页面未使用平台路由切换业务子页");
}

if (!component.includes("ITOutsourcingDashboard") || !dashboard.includes("IT工程外包看板")) {
  throw new Error("工程外包主入口未接入 IT 工程外包看板");
}

if (!dashboard.includes('type="monthrange"') || !dashboard.includes('value="quarter"')) {
  throw new Error("IT 工程外包看板缺少统一时间区间或月季度粒度控制");
}

if (component.includes("我的待办") || !workbench.includes("IT工程外包待办")) {
  throw new Error("工程外包待办未完整迁移到统一工作台");
}

console.log("Engineering outsourcing shell check passed");
