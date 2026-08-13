import { writeFile } from 'node:fs/promises';
import path from 'node:path';
import { createInterface } from 'node:readline/promises';
import { stdin, stdout } from 'node:process';

const input = createInterface({ input: stdin, output: stdout });

try {
  const token = (await input.question('公司电脑 Agent 令牌（pa_agent_...）：')).trim();
  const apiKey = (await input.question('XSHOOW API Key：')).trim();
  const enteredBaseUrl = (await input.question('API 服务地址（直接回车使用 https://www.xshoow.cloud/v1）：')).trim();
  if (!token.startsWith('pa_agent_')) throw new Error('Agent 令牌必须以 pa_agent_ 开头');
  if (!apiKey) throw new Error('API Key 不能为空');
  const runtime = {
    enabled: true,
    token,
    apiKey,
    baseUrl: enteredBaseUrl || 'https://www.xshoow.cloud/v1',
  };
  const outputPath = path.resolve('./runtime.local.json');
  await writeFile(outputPath, `${JSON.stringify(runtime, null, 2)}\n`, { encoding: 'utf8', mode: 0o600 });
  console.log(`配置已保存：${outputPath}`);
  console.log('现在运行 npm start 即可启动 Agent。');
} finally {
  input.close();
}
