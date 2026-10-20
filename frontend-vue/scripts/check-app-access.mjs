import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, resolve } from "node:path";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const routerSource = readFileSync(resolve(root, "src/router/index.ts"), "utf8");
const menuSource = readFileSync(resolve(root, "src/config/menu.ts"), "utf8");

const routeBlocks = [...routerSource.matchAll(/\{\s*path:\s*"([^"]+)"[\s\S]*?\n\s*\}/g)].map((match) => ({
  path: match[1],
  body: match[0]
}));

const routeErrors = [];
for (const route of routeBlocks) {
  if (route.body.includes("redirect:")) {
    continue;
  }
  if (route.path === "/workbench") {
    if (!route.body.includes("anyAppCodes")) {
      routeErrors.push("/workbench must require anyAppCodes");
    }
    if (!route.body.includes('"EHS"')) {
      routeErrors.push("/workbench must allow EHS access");
    }
    if (!route.body.includes('"OUTSOURCING"')) {
      routeErrors.push("/workbench must allow OUTSOURCING access");
    }
    continue;
  }
  if (route.path === "/platform/access" || route.path === "/login" || route.path === "/") {
    continue;
  }
  if (route.body.includes("public: true")) {
    continue;
  }
  if (/^\/(procurement|maintenance|platform|ehs|feishu\/procurement-intake)/.test(route.path)
      && !route.body.includes("appCode")) {
    routeErrors.push(`${route.path} is missing meta.appCode`);
  }
}

const menuErrors = [];
const workbenchMenu = menuSource.match(/\{\s*path:\s*"\/workbench"[\s\S]*?\n\s*\}/)?.[0] ?? "";
if (!workbenchMenu.includes("anyAppCodes")) {
  menuErrors.push("workbench menu must require anyAppCodes");
}
if (!workbenchMenu.includes('"EHS"')) {
  menuErrors.push("workbench menu must allow EHS access");
}
if (!workbenchMenu.includes('"OUTSOURCING"')) {
  menuErrors.push("workbench menu must allow OUTSOURCING access");
}
if (workbenchMenu.includes("alwaysVisible")) {
  menuErrors.push("workbench menu must not be alwaysVisible");
}
for (const menuPath of menuSource.matchAll(/path:\s*"([^"]+)"/g)) {
  const path = menuPath[1];
  if (path === "/workbench"
      || path === "/platform"
      || path === "/platform/access"
      || !/^\/(procurement|maintenance|platform|ehs)/.test(path)) {
    continue;
  }
  const start = menuPath.index ?? 0;
  const end = menuSource.indexOf("}", start);
  const body = menuSource.slice(start, end === -1 ? undefined : end);
  if (!body.includes("appCode")) {
    menuErrors.push(`${path} menu item is missing appCode`);
  }
}

const errors = [...routeErrors, ...menuErrors];
if (errors.length > 0) {
  console.error("Application access check failed:");
  for (const error of errors) {
    console.error(`- ${error}`);
  }
  process.exit(1);
}

console.log("Application access check passed");
