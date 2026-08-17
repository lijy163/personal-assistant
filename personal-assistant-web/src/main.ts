import ElementPlus from 'element-plus';
import 'element-plus/dist/index.css';
import { createPinia } from 'pinia';
import { createApp } from 'vue';
import App from './App.vue';
import router from './router';
import { scheduleRoutePreload } from './router/routePreload';
import './styles/global.css';
import './styles/performance.css';
import './styles/calendarFit.css';

const app = createApp(App);

app.use(createPinia());
app.use(router);
app.use(ElementPlus);
app.mount('#app');

scheduleRoutePreload(router, [
  '/dashboard', '/calendar', '/project-workbench', '/life', '/work', '/inbox', '/reminders',
  '/automation', '/knowledge', '/finance-planning', '/life-management', '/reports',
]);

if ('serviceWorker' in navigator) {
  window.addEventListener('load', async () => {
    const registration = await navigator.serviceWorker.register('/sw.js', { updateViaCache: 'none' });
    await registration.update();
  });
}
