import { spawn } from 'node:child_process';
import { stat } from 'node:fs/promises';
import path from 'node:path';
import readline from 'node:readline';

export async function runCodex(config, task, onEvent) {
  const project = await validateTask(config, task);
  const sandbox = task.permissionMode === 'WORKSPACE_WRITE' ? 'workspace-write' : 'read-only';
  const args = ['exec', '--json', '--sandbox', sandbox, '--ask-for-approval', 'never', task.prompt];
  const timeoutMs = (config.taskTimeoutMinutes ?? 60) * 60_000;

  return new Promise((resolve, reject) => {
    const codexCommand = config.codexCommand ?? (process.platform === 'win32' ? 'codex.cmd' : 'codex');
    const child = spawn(codexCommand, args, {
      cwd: project.path,
      shell: false,
      windowsHide: true,
      env: process.env,
      stdio: ['ignore', 'pipe', 'pipe'],
    });
    let threadId;
    let finalResponse = '';
    let stderr = '';
    let eventChain = Promise.resolve();
    const stdoutLines = readline.createInterface({ input: child.stdout });
    const timeout = setTimeout(() => child.kill(), timeoutMs);

    stdoutLines.on('line', line => {
      let event;
      try {
        event = JSON.parse(line);
      } catch {
        return;
      }
      if (event.type === 'thread.started') threadId = event.thread_id;
      if (event.type === 'item.completed' && event.item?.type === 'agent_message') {
        finalResponse = event.item.text ?? finalResponse;
      }
      if (shouldReport(event)) {
        eventChain = eventChain.then(() => onEvent(event.type, JSON.stringify(safeEvent(event)))).catch(() => undefined);
      }
    });
    child.stderr.on('data', chunk => {
      stderr = `${stderr}${chunk}`.slice(-20000);
    });
    child.on('error', error => {
      clearTimeout(timeout);
      reject(new Error(`无法启动 Codex：${error.message}`));
    });
    child.on('close', async code => {
      clearTimeout(timeout);
      await eventChain;
      if (code === 0) resolve({ threadId, finalResponse });
      else reject(new CodexRunError(`Codex 执行失败（退出码 ${code}）：${stderr || '没有错误输出'}`, threadId));
    });
  });
}

async function validateTask(config, task) {
  const project = config.projects?.[task.projectKey];
  if (!project) throw new Error(`项目 ${task.projectKey} 不在本机白名单中`);
  if (task.permissionMode === 'WORKSPACE_WRITE' && !project.allowWrite) {
    throw new Error(`项目 ${task.projectKey} 未允许写入`);
  }
  const projectPath = path.resolve(project.path);
  const info = await stat(projectPath).catch(() => null);
  if (!info?.isDirectory()) throw new Error(`项目目录不存在：${projectPath}`);
  return { ...project, path: projectPath };
}

function shouldReport(event) {
  return event.type === 'turn.started' || event.type === 'turn.completed' || event.type === 'turn.failed'
    || event.type === 'error' || event.type === 'item.completed';
}

function safeEvent(event) {
  if (!event.item) return { type: event.type, status: event.status, error: event.error?.message };
  if (event.item.type === 'agent_message') {
    return { type: event.type, item: { type: event.item.type, text: event.item.text } };
  }
  return { type: event.type, item: { type: event.item.type, status: event.item.status } };
}

export class CodexRunError extends Error {
  constructor(message, threadId) {
    super(message);
    this.threadId = threadId;
  }
}
