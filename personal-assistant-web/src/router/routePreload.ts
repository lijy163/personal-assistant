import type { Router, RouteRecordRaw } from 'vue-router';

const loadedRoutes = new Set<string>();

function loadRecord(record: RouteRecordRaw) {
  const components = record.components ?? (record.component ? { default: record.component } : {});
  return Promise.all(Object.values(components).map((component) => {
    if (typeof component !== 'function') return Promise.resolve();
    const loader = component as unknown as () => unknown;
    return Promise.resolve(loader()).catch(() => undefined);
  }));
}

export async function preloadRoute(router: Router, path: string) {
  if (loadedRoutes.has(path)) return;
  loadedRoutes.add(path);
  try {
    await Promise.all(router.resolve(path).matched.map((record) => loadRecord(record)));
  } catch {
    loadedRoutes.delete(path);
  }
}

export function preloadRoutes(router: Router, paths: string[]) {
  paths.forEach((path) => void preloadRoute(router, path));
}

export function scheduleRoutePreload(router: Router, paths: string[]) {
  const queue = [...new Set(paths)];
  const runNext = () => {
    const path = queue.shift();
    if (!path) return;
    void preloadRoute(router, path).finally(scheduleNext);
  };
  const scheduleNext = () => {
    if (!queue.length) return;
    window.requestIdleCallback(runNext, { timeout: 1500 });
  };
  scheduleNext();
}
