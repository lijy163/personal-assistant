<template>
  <el-card class="settings-card">
    <template #header>账号安全</template>
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px" status-icon>
      <el-form-item label="当前密码" prop="currentPassword">
        <el-input v-model="form.currentPassword" type="password" show-password autocomplete="current-password" />
      </el-form-item>
      <el-form-item label="新密码" prop="newPassword">
        <el-input v-model="form.newPassword" type="password" show-password autocomplete="new-password" />
      </el-form-item>
      <el-form-item label="确认新密码" prop="confirmPassword">
        <el-input v-model="form.confirmPassword" type="password" show-password autocomplete="new-password" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="saving" @click="submit">修改密码</el-button>
      </el-form-item>
    </el-form>
  </el-card>
</template>

<script setup lang="ts">
import type { FormInstance, FormRules } from 'element-plus';
import { ElMessage } from 'element-plus';
import { reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { changePassword } from '@/api/auth';

const router = useRouter();
const formRef = ref<FormInstance>();
const saving = ref(false);
const form = reactive({ currentPassword: '', newPassword: '', confirmPassword: '' });
const rules: FormRules = {
  currentPassword: [{ required: true, message: '请输入当前密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 8, max: 72, message: '密码长度必须为 8 到 72 个字符', trigger: 'blur' },
  ],
  confirmPassword: [{
    validator: (_rule, value, callback) => value === form.newPassword
      ? callback()
      : callback(new Error('两次输入的新密码不一致')),
    trigger: 'blur',
  }],
};

async function submit() {
  if (!await formRef.value?.validate()) return;
  saving.value = true;
  try {
    await changePassword({ currentPassword: form.currentPassword, newPassword: form.newPassword });
    localStorage.removeItem('token');
    localStorage.removeItem('currentUser');
    ElMessage.success('密码修改成功，请重新登录');
    await router.replace('/login');
  } finally {
    saving.value = false;
  }
}
</script>

<style scoped>
.settings-card { max-width: 640px; }
</style>