import { existsSync, readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const frontendRoot = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const repoRoot = resolve(frontendRoot, "..");
const publicRoot = resolve(frontendRoot, "public");
const developerRoot = resolve(publicRoot, "developer");

const developerResources = [
  "ai-context.md",
  "ai-context.json",
  "module-development-guide.md",
  "module-manifest.schema.json",
  "module-manifest.example.json",
  "pr-checklist.md"
];

const errors = [];
const parsedJsonResources = new Map();

function readRequired(path, label) {
  if (!existsSync(path)) {
    errors.push(`${label} is missing`);
    return "";
  }
  return readFileSync(path, "utf8");
}

function countMatches(source, pattern) {
  return [...source.matchAll(pattern)].length;
}

function checkTestGatedRoute(source, label) {
  const routePattern = /path:\s*["']\/developer-center["']/g;
  const matches = [...source.matchAll(routePattern)];
  if (matches.length !== 1) {
    errors.push(`${label} must contain exactly one /developer-center entry`);
    return;
  }

  const routeIndex = matches[0].index ?? -1;
  const gateStart = source.lastIndexOf("...(testEnvironment", routeIndex);
  const gateEnd = source.indexOf(": [])", routeIndex);
  if (gateStart < 0
      || gateEnd < 0
      || routeIndex - gateStart > 500
      || gateEnd - routeIndex > 1200) {
    errors.push(`${label} /developer-center entry must stay inside the testEnvironment conditional`);
  }
}

const llmsPath = resolve(publicRoot, "llms.txt");
const llmsSource = readRequired(llmsPath, "/llms.txt");
const resourceSources = new Map();

for (const resource of developerResources) {
  const path = resolve(developerRoot, resource);
  resourceSources.set(resource, readRequired(path, `/developer/${resource}`));
}

for (const resource of developerResources.filter((name) => name.endsWith(".json"))) {
  const source = resourceSources.get(resource);
  if (!source) {
    continue;
  }
  try {
    parsedJsonResources.set(resource, JSON.parse(source));
  } catch (error) {
    errors.push(`/developer/${resource} is not valid JSON: ${error.message}`);
  }
}

function resolveSchemaReference(rootSchema, schema) {
  if (!schema?.$ref?.startsWith("#/")) {
    return schema;
  }
  return schema.$ref
    .slice(2)
    .split("/")
    .map((part) => part.replaceAll("~1", "/").replaceAll("~0", "~"))
    .reduce((value, part) => value?.[part], rootSchema);
}

function validateJsonSchema(value, rawSchema, rootSchema, path = "$") {
  const schema = resolveSchemaReference(rootSchema, rawSchema);
  if (!schema) {
    errors.push(`${path} references an unknown JSON Schema node`);
    return;
  }
  if (Object.hasOwn(schema, "const") && value !== schema.const) {
    errors.push(`${path} must equal ${JSON.stringify(schema.const)}`);
  }
  if (schema.enum && !schema.enum.includes(value)) {
    errors.push(`${path} must be one of the declared enum values`);
  }
  if (schema.type === "object") {
    if (!value || typeof value !== "object" || Array.isArray(value)) {
      errors.push(`${path} must be an object`);
      return;
    }
    for (const requiredKey of schema.required ?? []) {
      if (!Object.hasOwn(value, requiredKey)) {
        errors.push(`${path} is missing required property ${requiredKey}`);
      }
    }
    if (schema.additionalProperties === false) {
      for (const key of Object.keys(value)) {
        if (!Object.hasOwn(schema.properties ?? {}, key)) {
          errors.push(`${path} contains unsupported property ${key}`);
        }
      }
    }
    for (const [key, childSchema] of Object.entries(schema.properties ?? {})) {
      if (Object.hasOwn(value, key)) {
        validateJsonSchema(value[key], childSchema, rootSchema, `${path}.${key}`);
      }
    }
    return;
  }
  if (schema.type === "array") {
    if (!Array.isArray(value)) {
      errors.push(`${path} must be an array`);
      return;
    }
    if (schema.minItems != null && value.length < schema.minItems) {
      errors.push(`${path} must contain at least ${schema.minItems} items`);
    }
    if (schema.uniqueItems && new Set(value.map((item) => JSON.stringify(item))).size !== value.length) {
      errors.push(`${path} must contain unique items`);
    }
    value.forEach((item, index) =>
      validateJsonSchema(item, schema.items ?? {}, rootSchema, `${path}[${index}]`)
    );
    return;
  }
  if (schema.type === "string") {
    if (typeof value !== "string") {
      errors.push(`${path} must be a string`);
      return;
    }
    if (schema.minLength != null && value.length < schema.minLength) {
      errors.push(`${path} must contain at least ${schema.minLength} characters`);
    }
    if (schema.maxLength != null && value.length > schema.maxLength) {
      errors.push(`${path} must contain no more than ${schema.maxLength} characters`);
    }
    if (schema.pattern && !new RegExp(schema.pattern).test(value)) {
      errors.push(`${path} does not match its declared pattern`);
    }
    return;
  }
  if (schema.type === "boolean" && typeof value !== "boolean") {
    errors.push(`${path} must be a boolean`);
  }
}

const manifestSchema = parsedJsonResources.get("module-manifest.schema.json");
const manifestExample = parsedJsonResources.get("module-manifest.example.json");
if (manifestSchema && manifestExample) {
  validateJsonSchema(manifestExample, manifestSchema, manifestSchema);
}

const indexedLinks = new Set(
  [...llmsSource.matchAll(/\[[^\]]+]\((\/[^)\s]+)\)/g)]
    .map((match) => match[1].split(/[?#]/, 1)[0])
);

for (const resource of developerResources) {
  const expectedLink = `/developer/${resource}`;
  if (!indexedLinks.has(expectedLink)) {
    errors.push(`/llms.txt must index ${expectedLink}`);
  }
}

const routerPath = resolve(frontendRoot, "src/router/index.ts");
const menuPath = resolve(frontendRoot, "src/config/menu.ts");
const pagePath = resolve(frontendRoot, "src/pages/platform/DeveloperCenterPage.vue");
const contentPath = resolve(frontendRoot, "src/pages/platform/developerCenterContent.ts");
const runtimeEnvironmentPath = resolve(frontendRoot, "src/config/runtime-environment.ts");
const testNginxPath = resolve(frontendRoot, "deploy/nginx/test.conf");
const productionNginxPath = resolve(frontendRoot, "deploy/nginx/default.conf");
const applicationTestPath = resolve(repoRoot, "backend-springboot/src/main/resources/application-test.yml");
const openApiConfigPath = resolve(
  repoRoot,
  "backend-springboot/src/main/java/com/company/procurement/common/config/OpenApiConfig.java"
);

const routerSource = readRequired(routerPath, "developer center router source");
const menuSource = readRequired(menuPath, "developer center menu source");
const pageSource = readRequired(pagePath, "DeveloperCenterPage.vue");
const contentSource = readRequired(contentPath, "developer center content source");
const runtimeEnvironmentSource = readRequired(runtimeEnvironmentPath, "runtime environment source");
const testNginxSource = readRequired(testNginxPath, "test Nginx config");
const productionNginxSource = readRequired(productionNginxPath, "production Nginx config");
const applicationTestSource = readRequired(applicationTestPath, "test backend config");
const openApiConfigSource = readRequired(openApiConfigPath, "OpenAPI config");

for (const link of indexedLinks) {
  if (link.startsWith("/developer/") || link === "/llms.txt") {
    const staticPath = resolve(publicRoot, link.slice(1));
    if (!existsSync(staticPath)) {
      errors.push(`/llms.txt points to missing static resource ${link}`);
    }
    continue;
  }
  if (link === "/developer-center") {
    if (!routerSource.includes('path: "/developer-center"') || !existsSync(pagePath)) {
      errors.push("/llms.txt points to missing /developer-center route");
    }
    continue;
  }
  if (link === "/v3/api-docs") {
    if (!testNginxSource.includes("location /v3/api-docs")
        || !applicationTestSource.includes("api-docs:")) {
      errors.push("/llms.txt points to an unconfigured /v3/api-docs endpoint");
    }
    continue;
  }
  if (link === "/swagger-ui/index.html") {
    if (!testNginxSource.includes("location /swagger-ui/")
        || !applicationTestSource.includes("swagger-ui:")) {
      errors.push("/llms.txt points to an unconfigured Swagger UI endpoint");
    }
    continue;
  }
  errors.push(`/llms.txt contains an unresolved local link ${link}`);
}

const pageChecks = [
  ["search control", /<el-(?:input|autocomplete)\b[\s\S]{0,500}(?:搜索|search)/i],
  ["TEST marker", /\bTEST\b/],
  ["OpenAPI entry", /OpenAPI[\s\S]{0,500}\/v3\/api-docs|\/v3\/api-docs[\s\S]{0,500}OpenAPI/i],
  ["llms entry", /\/llms\.txt/]
];

for (const [label, pattern] of pageChecks) {
  if (!pattern.test(pageSource)) {
    errors.push(`DeveloperCenterPage.vue is missing the ${label}`);
  }
}

if (!contentSource.includes("/developer/module-manifest.example.json")
    || !contentSource.includes("module-manifest.schema.json")
    || /label:\s*["']module-manifest\.example\.json["'][\s\S]{0,200}language:\s*["']JSON["']/.test(contentSource)) {
  errors.push("the page must download the complete schema-validated module manifest example");
}

if (!pageSource.includes("section.code.value")
    || !pageSource.includes("section.code.label")) {
  errors.push("developer center search must index visible code blocks and permission examples");
}

if (!pageSource.includes("useRouter")
    || !pageSource.includes("route.hash")
    || !pageSource.includes("hashSectionId")) {
  errors.push("developer center hash navigation must preserve Vue Router state and section deep links");
}

if (!contentSource.includes("downloadable: false")
    || !pageSource.includes('v-if="resource.downloadable !== false"')) {
  errors.push("interactive resources such as Swagger UI must not expose a misleading download action");
}

if (!openApiConfigSource.includes("GlobalOpenApiCustomizer")
    || !openApiConfigSource.includes('BEARER_AUTH = "bearerAuth"')
    || !openApiConfigSource.includes("SecurityScheme.Type.HTTP")) {
  errors.push("Swagger must expose a bearer JWT authorization scheme for protected test APIs");
}

if (countMatches(runtimeEnvironmentSource, /isTestEnvironment/g) < 1
    || !runtimeEnvironmentSource.includes("X-Environment")
    || !runtimeEnvironmentSource.includes('"test"')) {
  errors.push("runtime environment detection must derive test mode from X-Environment");
}

checkTestGatedRoute(menuSource, "menu");
checkTestGatedRoute(routerSource, "router");

const productionDisabledLocations = [
  ["exact /llms.txt", /location\s*=\s*\/llms\.txt\s*\{[\s\S]*?return\s+404;[\s\S]*?\}/],
  ["/llms.txt/*", /location\s+\^~\s+\/llms\.txt\/\s*\{[\s\S]*?return\s+404;[\s\S]*?\}/],
  ["/developer/*", /location\s+\^~\s+\/developer\/\s*\{[\s\S]*?return\s+404;[\s\S]*?\}/],
  ["exact /developer-center", /location\s*=\s*\/developer-center\s*\{[\s\S]*?return\s+404;[\s\S]*?\}/],
  ["/developer-center/*", /location\s+\^~\s+\/developer-center\/\s*\{[\s\S]*?return\s+404;[\s\S]*?\}/],
  ["exact /v3/api-docs", /location\s*=\s*\/v3\/api-docs\s*\{[\s\S]*?return\s+404;[\s\S]*?\}/],
  ["/v3/api-docs/*", /location\s+\^~\s+\/v3\/api-docs\/\s*\{[\s\S]*?return\s+404;[\s\S]*?\}/],
  ["exact /v3/api-docs.yaml", /location\s*=\s*\/v3\/api-docs\.yaml\s*\{[\s\S]*?return\s+404;[\s\S]*?\}/],
  ["/swagger-ui/*", /location\s+\^~\s+\/swagger-ui\/\s*\{[\s\S]*?return\s+404;[\s\S]*?\}/]
];

for (const [label, pattern] of productionDisabledLocations) {
  if (!pattern.test(productionNginxSource)) {
    errors.push(`production Nginx must return 404 for ${label}`);
  }
}

if (!/location\s+~\*\s+\^\/assets\/\(\?:DeveloperCenterPage\|developerCenterContent\)-/.test(productionNginxSource)) {
  errors.push("production Nginx must block developer-center JS/CSS chunks");
}

if (!testNginxSource.includes("location = /llms.txt")
    || !testNginxSource.includes("location ^~ /developer/")) {
  errors.push("test Nginx must expose /llms.txt and /developer/*");
}

if (errors.length > 0) {
  console.error("Developer center check failed:");
  for (const error of errors) {
    console.error(`- ${error}`);
  }
  process.exit(1);
}

console.log(
  `Developer center check passed: ${developerResources.length} resources, `
    + `${indexedLinks.size} local links, JSON and test-only gates verified`
);
