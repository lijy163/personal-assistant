import axios from 'axios';

export interface PublicAnswer {
  taskId: number;
  question: string;
  status: string;
  answer?: string;
  errorMessage?: string;
  createdAt: string;
  finishedAt?: string;
}

const client = axios.create({ baseURL: '/api/public/codex', timeout: 15000 });

function sessionToken() {
  const key = 'publicCodexSession';
  let token = sessionStorage.getItem(key);
  if (!token) {
    const bytes = crypto.getRandomValues(new Uint8Array(32));
    token = Array.from(bytes, value => value.toString(16).padStart(2, '0')).join('');
    sessionStorage.setItem(key, token);
  }
  return token;
}

client.interceptors.request.use(config => {
  config.headers['X-Public-Session'] = sessionToken();
  return config;
});

client.interceptors.response.use(response => response.data);

export const askPublicCodex = (question: string) =>
  client.post<unknown, { data: { taskId: number; status: string; createdAt: string } }>('/questions', { question });

export const getPublicCodexAnswer = (taskId: number) =>
  client.get<unknown, { data: PublicAnswer }>(`/questions/${taskId}`);
