/**
 * Puppeteer E2E 测试配置
 */
export const config = {
  // 基础 URL
  baseUrl: process.env.E2E_BASE_URL || 'http://localhost:5173',
  
  // 后端 API URL
  apiUrl: process.env.E2E_API_URL || 'http://localhost:8080',
  
  // 浏览器配置
  browser: {
    headless: process.env.E2E_HEADLESS !== 'false',
    slowMo: parseInt(process.env.E2E_SLOW_MO || '0'),
    devtools: process.env.E2E_DEVTOOLS === 'true',
    // 使用系统 Chrome（macOS 路径）
    executablePath: process.env.E2E_CHROME_PATH || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
    args: [
      '--no-sandbox',
      '--disable-setuid-sandbox',
      '--disable-dev-shm-usage',
      '--disable-gpu'
    ]
  },
  
  // 超时配置（毫秒）
  timeout: {
    navigation: 30000,
    action: 10000,
    assertion: 5000
  },
  
  // 视口配置
  viewport: {
    width: 1920,
    height: 1080
  },
  
  // 截图配置
  screenshot: {
    enabled: true,
    path: 'e2e/screenshots',
    fullPage: true
  },
  
  // 测试用户配置
  testUser: {
    username: process.env.E2E_USERNAME || 'admin',
    password: process.env.E2E_PASSWORD || 'admin'
  }
}

export type Config = typeof config
