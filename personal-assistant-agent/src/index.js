import { readFile } from 'node:fs/promises';
import path from 'node:path';
import { AgentApiClient } from './api-client.js';
import { runCodex } from './codex-runner.js';

const configPath = path.resolve(process.env.PA_AGENT_CONFIG || './config.json');
const config = JSON.parse(await readFile(configPath, 'utf8'));
if (!config.serverUrl) throw new Error('config.json 缺少 serverUrl');

const pollIntervalMs = (config.pollIntervalSeconds ?? 5) * 1000;
const runtimeConfigPath = process.env.PA_RUNTIME_CONFIG;
let stopping = false;
let waitingLogged = false;

process.on('SIGINT', () => { stopping = true; });
process.on('SIGTERM', () => { stopping = true; });

console.log(`[agent] 已启动，服务器：${config.serverUrl}`);
while (!stopping) {
  try {
    const runtime = await loadRuntimeConfig();
    if (!runtime) {
      if (!waitingLogged) console.log('[agent] 等待后台配置 Agent 令牌和 API Key');
      waitingLogged = true;
      await sleep(pollIntervalMs);
      continue;
    }
    waitingLogged = false;
    process.env.CODEX_API_KEY = runtime.apiKey;
    process.env.OPENAI_BASE_URL = runtime.baseUrl;
    const api = new AgentApiClient(config.serverUrl, runtime.token);
    await api.heartbeat();
    const task = await api.claimTask();
    if (!task) {
      await sleep(pollIntervalMs);
      continue;
    }
    await executeTask(api, task);
  } catch (error) {
    console.error(`[agent] ${error instanceof Error ? error.message : error}`);
    await sleep(pollIntervalMs);
  }
}
console.log('[agent] 已停止');

async function executeTask(api, task) {
  console.log(`[task:${task.taskId}] 开始执行 ${task.projectKey} (${task.permissionMode})`);
  const renewTimer = setInterval(() => api.renew(task).catch(error => {
    console.error(`[task:${task.taskId}] 续租失败：${error.message}`);
  }), 60_000);
  try {
    const result = await runCodex(config, task, (type, content) => api.event(task, type, content));
    await api.complete(task, result);
    console.log(`[task:${task.taskId}] 执行完成`);
  } catch (error) {
    await api.fail(task, error, error?.threadId).catch(reportError => {
      console.error(`[task:${task.taskId}] 失败状态回传失败：${reportError.message}`);
    });
    console.error(`[task:${task.taskId}] ${error instanceof Error ? error.message : error}`);
  } finally {
    clearInterval(renewTimer);
  }
}

async function loadRuntimeConfig() {
  if (!runtimeConfigPath) {
    const token = process.env.PA_AGENT_TOKEN;
    const apiKey = process.env.CODEX_API_KEY;
    if (!token?.startsWith('pa_agent_') || !apiKey) return null;
    return { enabled: true, token, apiKey, baseUrl: process.env.OPENAI_BASE_URL || 'https://www.xshoow.cloud/v1' };
  }
  try {
    const runtime = JSON.parse(await readFile(runtimeConfigPath, 'utf8'));
    if (!runtime.enabled || !runtime.token?.startsWith('pa_agent_') || !runtime.apiKey) return null;
    return runtime;
  } catch {
    return null;
  }
}

function sleep(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}
