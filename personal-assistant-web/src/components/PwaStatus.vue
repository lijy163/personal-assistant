<template><div class="pwa-status"><el-alert v-if="!online" title="当前处于离线状态，收件箱文字可排队并在联网后同步" type="warning" :closable="false" show-icon/><div v-if="installReady||updateReady" class="pwa-action"><span>{{updateReady?'发现新版本，刷新后即可使用':'可将个人辅助系统安装到手机桌面'}}</span><el-button size="small" type="primary" @click="updateReady?applyUpdate():install()">{{updateReady?'立即更新':'安装应用'}}</el-button></div></div></template>
<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue';

interface InstallPromptEvent extends Event {
  prompt(): Promise<void>;
  userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }>;
}

const online = ref(navigator.onLine);
const installReady = ref(false);
const updateReady = ref(false);
let installPrompt: InstallPromptEvent | undefined;
let registration: ServiceWorkerRegistration | undefined;

function onOnline() { online.value = true; }
function onOffline() { online.value = false; }
function onInstallPrompt(event: Event) {
  event.preventDefault();
  installPrompt = event as InstallPromptEvent;
  installReady.value = true;
}
async function install() {
  if (!installPrompt) return;
  await installPrompt.prompt();
  await installPrompt.userChoice;
  installPrompt = undefined;
  installReady.value = false;
}
function applyUpdate() { registration?.waiting?.postMessage({ type: 'SKIP_WAITING' }); }
function watchRegistration(value: ServiceWorkerRegistration) {
  registration = value;
  if (value.waiting) updateReady.value = true;
  value.addEventListener('updatefound', () => {
    const worker = value.installing;
    worker?.addEventListener('statechange', () => {
      if (worker.state === 'installed' && navigator.serviceWorker.controller) updateReady.value = true;
    });
  });
}

onMounted(async () => {
  window.addEventListener('online', onOnline);
  window.addEventListener('offline', onOffline);
  window.addEventListener('beforeinstallprompt', onInstallPrompt);
  navigator.serviceWorker?.addEventListener('controllerchange', () => window.location.reload());
  const value = await navigator.serviceWorker?.ready;
  if (value) watchRegistration(value);
});
onBeforeUnmount(() => {
  window.removeEventListener('online', onOnline);
  window.removeEventListener('offline', onOffline);
  window.removeEventListener('beforeinstallprompt', onInstallPrompt);
});
</script>
<style scoped>.pwa-status{position:sticky;top:0;z-index:8}.pwa-action{display:flex;align-items:center;justify-content:center;gap:12px;padding:8px 16px;color:#1e3a8a;background:#dbeafe;font-size:13px}@media(max-width:768px){.pwa-action{justify-content:space-between;padding-left:max(12px,env(safe-area-inset-left));padding-right:max(12px,env(safe-area-inset-right))}}</style>
