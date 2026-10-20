<template>
  <VnetPage title="EHS 智能助手">
    <template #actions><el-button :icon="DocumentAdd" @click="knowledgeVisible = true">新增知识</el-button><el-button type="primary" :icon="Plus" @click="newSession">新会话</el-button></template>
    <div class="assistant-layout">
      <aside class="session-pane">
        <div class="pane-title">会话记录</div>
        <div class="session-list">
          <button v-for="session in sessions" :key="session.id" class="session-row" :class="{ active: session.id === sessionId }" @click="selectSession(session.id)">
            <span>{{ session.title }}</span><el-icon title="删除会话" @click.stop="removeSession(session.id)"><Delete /></el-icon>
          </button>
          <el-empty v-if="!sessions.length" description="暂无会话" :image-size="54" />
        </div>
      </aside>

      <main class="conversation-pane">
        <div ref="messageViewport" class="message-list">
          <div v-if="!messages.length" class="welcome-block">
            <h2>查询 EHS 实时业务数据</h2>
            <p>回答会标注台账或知识库来源，审批结果只能由当前审批人处理。</p>
            <div class="suggestion-grid"><button v-for="item in suggestions" :key="item" @click="ask(item)">{{ item }}</button></div>
          </div>
          <div v-for="message in messages" :key="message.id" class="message-row" :class="message.role.toLowerCase()">
            <div class="message-meta">{{ message.role === 'USER' ? '我' : 'EHS 助手' }} · {{ formatTime(message.createdAt) }}</div>
            <div class="message-content">{{ message.content }}</div>
            <div v-if="message.role === 'ASSISTANT' && parseSources(message.sourceRefsJson).length" class="source-list"><span v-for="source in parseSources(message.sourceRefsJson)" :key="source">{{ source }}</span></div>
          </div>
          <div v-if="chatting" class="message-row assistant"><div class="message-meta">EHS 助手</div><div class="message-content"><el-icon class="is-loading"><Loading /></el-icon> 正在查询实时数据...</div></div>
        </div>
        <div class="composer"><el-input v-model="prompt" type="textarea" :rows="3" placeholder="询问隐患、危废、PPE、事故、作业许可或知识制度" @keydown.ctrl.enter.prevent="send" /><el-button type="primary" :icon="Promotion" :loading="chatting" @click="send">发送</el-button></div>
      </main>

      <aside class="knowledge-pane">
        <div class="pane-title"><span>知识库</span><el-button link type="primary" :icon="Upload" @click="fileInput?.click()">上传文件</el-button></div>
        <input ref="fileInput" class="hidden-input" type="file" accept=".txt,.md,.csv,.json" @change="uploadKnowledge" />
        <el-input v-model="knowledgeKeyword" placeholder="检索制度、标准、预案" clearable :prefix-icon="Search" @keyup.enter="loadKnowledge" />
        <div class="knowledge-list">
          <article v-for="doc in knowledge.records" :key="doc.id" class="knowledge-row">
            <div><strong>{{ doc.title }}</strong><small>{{ doc.category || '未分类' }} · {{ doc.uploadedByName || '系统' }}</small></div>
            <p>{{ excerpt(doc.content) }}</p>
            <el-button link type="danger" :icon="Delete" title="删除知识" @click="removeKnowledge(doc.id)" />
          </article>
          <el-empty v-if="!knowledge.records.length" description="暂无知识文档" :image-size="54" />
        </div>
      </aside>
    </div>

    <el-dialog v-model="knowledgeVisible" title="新增 EHS 知识" width="620px">
      <el-form label-width="90px"><el-form-item label="标题"><el-input v-model="knowledgeForm.title" /></el-form-item><el-form-item label="分类"><el-input v-model="knowledgeForm.category" /></el-form-item><el-form-item label="正文"><el-input v-model="knowledgeForm.content" type="textarea" :rows="12" /></el-form-item></el-form>
      <template #footer><el-button @click="knowledgeVisible = false">取消</el-button><el-button type="primary" :loading="knowledgeSaving" @click="saveKnowledge">保存</el-button></template>
    </el-dialog>
  </VnetPage>
</template>

<script setup lang="ts">
import { nextTick, onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { Delete, DocumentAdd, Loading, Plus, Promotion, Search, Upload } from "@element-plus/icons-vue";
import { chatEhsAssistantApi, createEhsKnowledgeApi, deleteEhsAiSessionApi, deleteEhsKnowledgeApi, listEhsAiMessagesApi, listEhsAiSessionsApi, pageEhsKnowledgeApi, uploadEhsKnowledgeApi } from "../../api/ehs";
import VnetPage from "../../components/vnet/VnetPage.vue";
import type { EhsAiMessage, EhsAiSession, EhsKnowledgeDocument } from "../../types/ehs";

const suggestions = ["当前有哪些临期或逾期隐患？", "危废待转运情况", "PPE 库存和临期预警", "作业许可审批情况"];
const sessions = ref<EhsAiSession[]>([]); const messages = ref<EhsAiMessage[]>([]); const sessionId = ref<number>(); const prompt = ref(""); const chatting = ref(false); const messageViewport = ref<HTMLElement>();
const knowledgeVisible = ref(false); const knowledgeSaving = ref(false); const knowledgeKeyword = ref(""); const fileInput = ref<HTMLInputElement>();
const knowledge = reactive({ total: 0, records: [] as EhsKnowledgeDocument[] }); const knowledgeForm = reactive({ title: "", category: "", content: "" });

async function loadSessions() { sessions.value = await listEhsAiSessionsApi(); }
async function selectSession(id: number) { sessionId.value = id; messages.value = await listEhsAiMessagesApi(id); await scrollBottom(); }
function newSession() { sessionId.value = undefined; messages.value = []; prompt.value = ""; }
async function removeSession(id: number) { await ElMessageBox.confirm("确认删除该会话？", "删除会话", { type: "warning" }); await deleteEhsAiSessionApi(id); if (sessionId.value === id) newSession(); await loadSessions(); }
function ask(text: string) { prompt.value = text; void send(); }
async function send() {
  const text = prompt.value.trim(); if (!text || chatting.value) return; prompt.value = "";
  messages.value.push({ id: -Date.now(), sessionId: sessionId.value || 0, role: "USER", content: text, createdAt: new Date().toISOString() });
  chatting.value = true; await scrollBottom();
  try { const result = await chatEhsAssistantApi(text, sessionId.value); sessionId.value = result.sessionId; messages.value = await listEhsAiMessagesApi(result.sessionId); await loadSessions(); await scrollBottom(); }
  finally { chatting.value = false; }
}
async function loadKnowledge() { const result = await pageEhsKnowledgeApi({ pageNo: 1, pageSize: 50, keyword: knowledgeKeyword.value }); knowledge.total = result.total; knowledge.records = result.records; }
async function saveKnowledge() { if (!knowledgeForm.title.trim() || !knowledgeForm.content.trim()) { ElMessage.warning("请填写标题和正文"); return; } knowledgeSaving.value = true; try { await createEhsKnowledgeApi({ ...knowledgeForm }); ElMessage.success("知识已入库并可检索"); knowledgeVisible.value = false; Object.assign(knowledgeForm, { title: "", category: "", content: "" }); await loadKnowledge(); } finally { knowledgeSaving.value = false; } }
async function uploadKnowledge(event: Event) { const input = event.target as HTMLInputElement; const file = input.files?.[0]; input.value = ""; if (!file) return; await uploadEhsKnowledgeApi(file); ElMessage.success("知识文件已上传并可检索"); await loadKnowledge(); }
async function removeKnowledge(id: number) { await ElMessageBox.confirm("确认删除该知识文档？", "删除知识", { type: "warning" }); await deleteEhsKnowledgeApi(id); await loadKnowledge(); }
function parseSources(value?: string): string[] { if (!value) return []; try { const result = JSON.parse(value); return Array.isArray(result) ? result : []; } catch { return []; } }
function excerpt(value: string) { const text = value.replace(/\s+/g, " "); return text.length > 90 ? `${text.slice(0, 90)}...` : text; }
function formatTime(value?: string) { return value ? value.replace("T", " ").slice(0, 16) : ""; }
async function scrollBottom() { await nextTick(); if (messageViewport.value) messageViewport.value.scrollTop = messageViewport.value.scrollHeight; }
onMounted(async () => { await Promise.all([loadSessions(), loadKnowledge()]); });
</script>

<style scoped>
.assistant-layout { display: grid; grid-template-columns: 220px minmax(480px, 1fr) 300px; min-height: 650px; border: 1px solid #dce5f0; border-radius: 6px; background: #fff; overflow: hidden; }
.session-pane, .knowledge-pane { min-width: 0; background: #f7f9fc; } .session-pane { border-right: 1px solid #dce5f0; } .knowledge-pane { border-left: 1px solid #dce5f0; padding: 0 12px; }
.pane-title { display: flex; align-items: center; justify-content: space-between; min-height: 50px; padding: 0 12px; color: #243851; font-weight: 600; border-bottom: 1px solid #dce5f0; }
.session-list, .knowledge-list { display: grid; gap: 6px; padding: 10px; }
.session-row { display: grid; grid-template-columns: minmax(0, 1fr) 18px; align-items: center; gap: 8px; min-height: 42px; padding: 8px 10px; border: 1px solid transparent; border-radius: 5px; background: transparent; color: #42546c; text-align: left; cursor: pointer; }
.session-row span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; } .session-row.active { border-color: #aac4f8; background: #eaf1ff; color: #2057b6; }
.conversation-pane { display: grid; grid-template-rows: minmax(0, 1fr) auto; min-width: 0; }
.message-list { max-height: 560px; padding: 22px; overflow-y: auto; }
.welcome-block { max-width: 720px; margin: 70px auto 0; text-align: center; } .welcome-block h2 { margin: 0 0 8px; font-size: 22px; letter-spacing: 0; color: #172b45; } .welcome-block p { margin: 0 0 22px; color: #708099; }
.suggestion-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; } .suggestion-grid button { min-height: 46px; padding: 10px 12px; border: 1px solid #d7e1ed; border-radius: 6px; background: #fff; color: #30445f; text-align: left; cursor: pointer; }
.message-row { max-width: 82%; margin-bottom: 18px; } .message-row.user { margin-left: auto; } .message-meta { margin-bottom: 5px; color: #8290a4; font-size: 12px; } .message-row.user .message-meta { text-align: right; }
.message-content { padding: 12px 14px; white-space: pre-wrap; line-height: 1.65; border: 1px solid #dce5f0; border-radius: 6px; background: #f8fafc; color: #293d56; } .message-row.user .message-content { border-color: #9bbcf3; background: #eaf2ff; }
.source-list { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 6px; } .source-list span { padding: 3px 7px; border-radius: 4px; background: #eef2f7; color: #607087; font-size: 11px; }
.composer { display: grid; grid-template-columns: minmax(0, 1fr) auto; align-items: end; gap: 10px; padding: 14px; border-top: 1px solid #dce5f0; }
.knowledge-pane > .el-input { margin-top: 12px; } .knowledge-row { position: relative; padding: 10px 28px 10px 10px; border-bottom: 1px solid #dce5f0; } .knowledge-row strong, .knowledge-row small { display: block; } .knowledge-row small { margin-top: 3px; color: #7a899d; font-size: 11px; } .knowledge-row p { margin: 8px 0 0; color: #52657e; font-size: 12px; line-height: 1.5; } .knowledge-row > .el-button { position: absolute; top: 4px; right: 0; }
.hidden-input { display: none; }
@media (max-width: 1100px) { .assistant-layout { grid-template-columns: 190px minmax(460px, 1fr); } .knowledge-pane { display: none; } }
@media (max-width: 720px) { .assistant-layout { grid-template-columns: 1fr; } .session-pane { display: none; } .suggestion-grid, .composer { grid-template-columns: 1fr; } .message-row { max-width: 96%; } }
</style>
