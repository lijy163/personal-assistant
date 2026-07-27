<template>
  <el-card>
    <template #header><div class="header"><span>Codex 推送令牌</span><el-button type="primary" @click="createVisible = true">新建令牌</el-button></div></template>
    <el-alert title="令牌只允许推送开发记录；明文只在创建后显示一次，请妥善保存。" type="info" :closable="false"/>
    <el-table :data="tokens" class="table">
      <el-table-column prop="name" label="名称"/><el-table-column prop="tokenPrefix" label="令牌前缀" width="160"/>
      <el-table-column prop="scope" label="权限" width="140"/><el-table-column label="创建时间" width="180"><template #default="{ row }">{{ formatTime(row.createdAt) }}</template></el-table-column>
      <el-table-column label="最近使用" width="180"><template #default="{ row }">{{ formatTime(row.lastUsedAt) }}</template></el-table-column>
      <el-table-column label="状态" width="100"><template #default="{ row }"><el-tag :type="row.revokedAt ? 'info' : expired(row) ? 'warning' : 'success'">{{ row.revokedAt ? '已撤销' : expired(row) ? '已过期' : '有效' }}</el-tag></template></el-table-column>
      <el-table-column label="操作" width="90"><template #default="{ row }"><el-button v-if="!row.revokedAt" link type="danger" @click="revoke(row.id)">撤销</el-button></template></el-table-column>
    </el-table>
    <el-empty v-if="!tokens.length" description="暂无令牌"/>

    <el-dialog v-model="createVisible" title="新建 Codex 推送令牌" width="500px">
      <el-form :model="form" label-width="90px"><el-form-item label="名称"><el-input v-model="form.name" placeholder="例如：本机 Codex"/></el-form-item><el-form-item label="过期时间"><el-date-picker v-model="form.expiresAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" placeholder="留空表示不过期"/></el-form-item></el-form>
      <template #footer><el-button @click="createVisible = false">取消</el-button><el-button type="primary" @click="createToken">创建</el-button></template>
    </el-dialog>
    <el-dialog v-model="tokenVisible" title="请立即复制令牌" width="620px" :close-on-click-modal="false">
      <el-alert title="关闭后无法再次查看此令牌。数据库仅保存 SHA-256 哈希。" type="warning" :closable="false"/>
      <el-input v-model="createdToken" readonly class="token"><template #append><el-button @click="copyToken">复制</el-button></template></el-input>
    </el-dialog>
  </el-card>
</template>

<script setup lang="ts">
import { ElMessage } from 'element-plus';
import { onMounted, reactive, ref } from 'vue';
import { createPersonalAccessToken, listPersonalAccessTokens, revokePersonalAccessToken, type PersonalAccessToken } from '@/api/devlog';

const tokens = ref<PersonalAccessToken[]>([]); const createVisible = ref(false); const tokenVisible = ref(false); const createdToken = ref('');
const form = reactive({ name: '本机 Codex', expiresAt: '' });
const formatTime = (value?: string) => value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '-';
const expired = (token: PersonalAccessToken) => !!token.expiresAt && new Date(token.expiresAt).getTime() <= Date.now();
async function load() { tokens.value = (await listPersonalAccessTokens()).data; }
async function createToken() { if (!form.name.trim()) { ElMessage.warning('请输入令牌名称'); return; } const result = await createPersonalAccessToken({ name: form.name.trim(), expiresAt: form.expiresAt || undefined }); createdToken.value = result.data.token; createVisible.value = false; tokenVisible.value = true; await load(); }
async function revoke(id: number) { await revokePersonalAccessToken(id); ElMessage.success('令牌已撤销'); await load(); }
async function copyToken() { await navigator.clipboard.writeText(createdToken.value); ElMessage.success('令牌已复制'); }
onMounted(load);
</script>

<style scoped>
.header { display:flex; justify-content:space-between; align-items:center; font-weight:600; }.table { margin-top:16px; }.token { margin-top:16px; }
</style>
