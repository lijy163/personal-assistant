import http from './http';

export interface ApiResponse<T> {
  code: string;
  message: string;
  data: T;
  traceId: string | null;
}

export interface HealthStatusResponse {
  status: string;
  serviceName: string;
  version: string;
  checkedAt: string;
}

export function getSystemHealth() {
  return http.get<unknown, ApiResponse<HealthStatusResponse>>('/system/health');
}
