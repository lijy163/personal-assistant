<template>
  <div class="login-page">
    <div class="login-card">
      <div class="login-title">个人辅助系统</div>
      <div class="login-subtitle">统一管理生活、工作、学习和提醒</div>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="login-form" @submit.prevent="submitLogin">
        <el-form-item label="用户名" prop="username"><el-input v-model="form.username" placeholder="请输入用户名" /></el-form-item>
        <el-form-item label="密码" prop="password"><el-input v-model="form.password" type="password" placeholder="请输入密码" show-password /></el-form-item>
        <el-button type="primary" native-type="submit" class="login-button" :loading="loading">登录</el-button>
      </el-form>
      <div class="login-tip">请使用管理员账号登录</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { FormInstance, FormRules } from 'element-plus';
import { ElMessage } from 'element-plus';
import { reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { login } from '@/api/auth';

const router = useRouter();
const formRef = ref<FormInstance>();
const loading = ref(false);
const form = reactive({ username: 'admin', password: '' });
const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
};

async function submitLogin() {
  if (!(await formRef.value?.validate())) return;
  loading.value = true;
  try {
    const response = await login(form);
    localStorage.setItem('token', response.data.token);
    localStorage.setItem('currentUser', JSON.stringify(response.data));
    ElMessage.success('登录成功');
    await router.replace('/dashboard');
  } finally { loading.value = false; }
}
</script>