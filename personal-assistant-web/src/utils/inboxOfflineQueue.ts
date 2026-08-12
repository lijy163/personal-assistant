export interface OfflineInboxEntry {
  id: string;
  content: string;
  tags: string;
  remark: string;
  source: string;
  recordedAt?: string;
  queuedAt: string;
}

const QUEUE_KEY = 'personal-assistant-inbox-offline-queue-v1';

export function readInboxQueue(): OfflineInboxEntry[] {
  try { return JSON.parse(localStorage.getItem(QUEUE_KEY) || '[]') as OfflineInboxEntry[]; }
  catch { localStorage.removeItem(QUEUE_KEY); return []; }
}

export function enqueueInbox(payload: Omit<OfflineInboxEntry, 'id' | 'queuedAt'>): OfflineInboxEntry[] {
  const queue = readInboxQueue();
  queue.push({ ...payload, id: crypto.randomUUID(), queuedAt: new Date().toISOString() });
  localStorage.setItem(QUEUE_KEY, JSON.stringify(queue));
  return queue;
}

export function replaceInboxQueue(queue: OfflineInboxEntry[]) {
  if (queue.length) localStorage.setItem(QUEUE_KEY, JSON.stringify(queue));
  else localStorage.removeItem(QUEUE_KEY);
}
