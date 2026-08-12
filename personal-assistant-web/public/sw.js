const CACHE='personal-assistant-v4';
const SHELL=['/','/index.html','/login','/manifest.webmanifest','/icon.svg','/icons/icon-192.png','/icons/icon-512.png'];
self.addEventListener('install',event=>event.waitUntil(caches.open(CACHE).then(cache=>cache.addAll(SHELL))));
self.addEventListener('activate',event=>event.waitUntil(caches.keys().then(keys=>Promise.all(keys.filter(key=>key!==CACHE).map(key=>caches.delete(key)))).then(()=>self.clients.claim())));
self.addEventListener('message',event=>{if(event.data?.type==='SKIP_WAITING')self.skipWaiting()});
self.addEventListener('fetch',event=>{const request=event.request,url=new URL(request.url);if(request.method!=='GET'||url.origin!==self.location.origin||url.pathname.startsWith('/api/'))return;if(request.mode==='navigate'){event.respondWith(fetch(request).catch(()=>caches.match('/index.html')));return}event.respondWith(caches.match(request).then(cached=>cached||fetch(request).then(response=>{if(response.ok&&['script','style','font','image'].includes(request.destination)){const copy=response.clone();caches.open(CACHE).then(cache=>cache.put(request,copy))}return response})))});
