export class AgentApiClient {
  constructor(serverUrl, token) {
    this.baseUrl = `${serverUrl.replace(/\/$/, '')}/api/codex-agent-runtime`;
    this.token = token;
  }

  heartbeat() {
    return this.request('/heartbeat', {});
  }

  async claimTask() {
    const response = await this.request('/tasks/claim', {});
    return response.data;
  }

  async renew(task) {
    const response = await this.request(`/tasks/${task.taskId}/renew`, { leaseId: task.leaseId });
    return response.data;
  }

  event(task, eventType, content) {
    return this.request(`/tasks/${task.taskId}/events`, {
      leaseId: task.leaseId,
      eventType,
      content: truncate(content, 60000),
    });
  }

  complete(task, result) {
    return this.request(`/tasks/${task.taskId}/complete`, {
      leaseId: task.leaseId,
      threadId: result.threadId,
      finalResponse: truncate(result.finalResponse || 'Codex 已完成任务，但没有返回文本。', 200000),
    });
  }

  fail(task, error, threadId) {
    return this.request(`/tasks/${task.taskId}/fail`, {
      leaseId: task.leaseId,
      threadId,
      errorMessage: truncate(error instanceof Error ? error.message : String(error), 20000),
    });
  }

  async request(path, body) {
    const response = await fetch(`${this.baseUrl}${path}`, {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${this.token}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(body),
      signal: AbortSignal.timeout(15000),
    });
    const payload = await response.json().catch(() => null);
    if (!response.ok || payload?.code !== 'SUCCESS') {
      throw new Error(payload?.message || `服务器请求失败：HTTP ${response.status}`);
    }
    return payload;
  }
}

function truncate(value, maxLength) {
  const text = String(value ?? '');
  return text.length > maxLength ? `${text.slice(0, maxLength)}\n...[内容已截断]` : text;
}
