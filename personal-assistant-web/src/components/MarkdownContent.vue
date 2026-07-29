<template><div class="markdown-body" v-html="html" /></template>
<script setup lang="ts">
import DOMPurify from 'dompurify';
import MarkdownIt from 'markdown-it';
import { computed } from 'vue';

const props=defineProps<{content:string}>();
const markdown=new MarkdownIt({html:false,linkify:true,typographer:true,breaks:false});
const defaultLink=markdown.renderer.rules.link_open||((tokens,index,options,env,self)=>self.renderToken(tokens,index,options));
markdown.renderer.rules.link_open=(tokens,index,options,env,self)=>{tokens[index].attrSet('target','_blank');tokens[index].attrSet('rel','noopener noreferrer');return defaultLink(tokens,index,options,env,self);};
const html=computed(()=>DOMPurify.sanitize(markdown.render(props.content||''),{USE_PROFILES:{html:true}}));
</script>
<style scoped>
.markdown-body{color:#263449;font-size:17px;line-height:1.9;word-break:break-word}.markdown-body :deep(h1),.markdown-body :deep(h2),.markdown-body :deep(h3){color:#12213a;line-height:1.35;letter-spacing:-.025em}.markdown-body :deep(h1){margin:1.7em 0 .7em;font-size:2em}.markdown-body :deep(h2){margin:1.8em 0 .7em;padding-bottom:.35em;border-bottom:1px solid #e7edf5;font-size:1.55em}.markdown-body :deep(h3){margin:1.5em 0 .5em;font-size:1.25em}.markdown-body :deep(p){margin:1em 0}.markdown-body :deep(a){color:#2563eb;text-decoration:none;border-bottom:1px solid #bfdbfe}.markdown-body :deep(img){display:block;max-width:100%;height:auto;margin:1.8em auto;border-radius:16px;box-shadow:0 18px 45px rgb(15 23 42 / 12%)}.markdown-body :deep(blockquote){margin:1.4em 0;padding:12px 18px;color:#475569;background:#f1f5f9;border-left:4px solid #14b8a6;border-radius:0 12px 12px 0}.markdown-body :deep(pre){padding:18px;overflow:auto;color:#dbeafe;background:#101827;border:1px solid #26364f;border-radius:14px;box-shadow:0 12px 30px rgb(15 23 42 / 12%)}.markdown-body :deep(code){padding:.15em .4em;color:#be123c;background:#fff1f2;border-radius:6px;font-family:"JetBrains Mono",Consolas,monospace}.markdown-body :deep(pre code){padding:0;color:inherit;background:none}.markdown-body :deep(table){display:block;width:100%;overflow:auto;border-collapse:collapse}.markdown-body :deep(th),.markdown-body :deep(td){padding:10px 14px;border:1px solid #dbe3ee}.markdown-body :deep(th){background:#f8fafc}.markdown-body :deep(hr){margin:2.5em 0;border:0;border-top:1px solid #dbe3ee}.markdown-body :deep(li){margin:.4em 0}
@media(max-width:768px){.markdown-body{font-size:16px;line-height:1.82}.markdown-body :deep(h1){font-size:1.72em}.markdown-body :deep(h2){font-size:1.4em}}
</style>
