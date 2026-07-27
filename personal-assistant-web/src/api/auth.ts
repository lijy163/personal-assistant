import http from './http';

export interface LoginRequest { username: string; password: string; }
export interface LoginResponse { token: string; username: string; displayName: string; userId: number; }

export function login(data: LoginRequest) {
  return http.post<unknown, { data: LoginResponse }>('/auth/login', data);
}

export function logout() { return http.post('/auth/logout'); }