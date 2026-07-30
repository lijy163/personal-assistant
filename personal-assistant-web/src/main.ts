import ElementPlus from 'element-plus';
import 'element-plus/dist/index.css';
import { createPinia } from 'pinia';
import { createApp } from 'vue';
import App from './App.vue';
import router from './router';
import { scheduleRoutePreload } from './router/routePreload';
import './styles/global.css';
import './styles/performance.css';

const app = createApp(App);

app.use(createPinia());
app.use(router);
app.use(ElementPlus);
app.mount('#app');

scheduleRoutePreload(router, [
  '/dashboard', '/calendar', '/life', '/work', '/inbox', '/reminders',
  '/automation', '/knowledge', '/finance-planning', '/life-management', '/reports',
]);

if ('serviceWorker' in navigator) {
  window.addEventListener('load', () => navigator.serviceWorker.register('/sw.js'));
}
