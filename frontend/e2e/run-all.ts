/**
 * 运行所有 E2E 测试
 */
import { spawn } from 'child_process'
import * as path from 'path'
import * as fs from 'fs'
import { fileURLToPath } from 'url'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)

const testsDir = path.join(__dirname, 'tests')

// 测试文件列表（按顺序执行）
const testFiles = [
  'login.test.ts',
  'navigation.test.ts',
  'release-window.test.ts',
  'repository.test.ts',
  'iteration.test.ts',
  'release-automation.test.ts'  // 发布自动化功能测试
]

interface TestResult {
  file: string
  passed: boolean
  duration: number
  error?: string
}

const results: TestResult[] = []

async function runTest(file: string): Promise<TestResult> {
  const filePath = path.join(testsDir, file)
  const startTime = Date.now()
  
  return new Promise((resolve) => {
    console.log(`\n${'='.repeat(60)}`)
    console.log(`📋 Running: ${file}`)
    console.log('='.repeat(60))
    
    const child = spawn('npx', ['tsx', filePath], {
      cwd: path.join(__dirname, '..'),
      stdio: 'inherit',
      shell: true
    })
    
    child.on('close', (code) => {
      const duration = Date.now() - startTime
      const passed = code === 0
      
      resolve({
        file,
        passed,
        duration,
        error: passed ? undefined : `Exit code: ${code}`
      })
    })
    
    child.on('error', (error) => {
      const duration = Date.now() - startTime
      resolve({
        file,
        passed: false,
        duration,
        error: error.message
      })
    })
  })
}

async function main(): Promise<void> {
  console.log('🚀 ReleaseHub E2E Test Suite')
  console.log(`📅 ${new Date().toLocaleString()}`)
  console.log(`📁 Tests directory: ${testsDir}`)
  
  // 检查测试文件是否存在
  const existingTests = testFiles.filter(file => {
    const filePath = path.join(testsDir, file)
    return fs.existsSync(filePath)
  })
  
  console.log(`\n📊 Found ${existingTests.length} test files`)
  
  // 创建截图目录
  const screenshotDir = path.join(__dirname, 'screenshots')
  if (!fs.existsSync(screenshotDir)) {
    fs.mkdirSync(screenshotDir, { recursive: true })
  }
  
  // 依次运行测试
  for (const file of existingTests) {
    const result = await runTest(file)
    results.push(result)
  }
  
  // 输出汇总结果
  console.log('\n' + '='.repeat(60))
  console.log('📊 Test Summary')
  console.log('='.repeat(60))
  
  let totalPassed = 0
  let totalFailed = 0
  let totalDuration = 0
  
  for (const result of results) {
    const status = result.passed ? '✅' : '❌'
    const duration = (result.duration / 1000).toFixed(2)
    console.log(`${status} ${result.file} (${duration}s)`)
    
    if (result.passed) {
      totalPassed++
    } else {
      totalFailed++
      if (result.error) {
        console.log(`   Error: ${result.error}`)
      }
    }
    totalDuration += result.duration
  }
  
  console.log('\n' + '-'.repeat(60))
  console.log(`Total: ${results.length} test suites`)
  console.log(`  ✅ Passed: ${totalPassed}`)
  console.log(`  ❌ Failed: ${totalFailed}`)
  console.log(`  ⏱️  Duration: ${(totalDuration / 1000).toFixed(2)}s`)
  console.log('-'.repeat(60))
  
  // 如果有失败的测试，退出码为 1
  if (totalFailed > 0) {
    process.exit(1)
  }
}

main().catch(error => {
  console.error('Fatal error:', error)
  process.exit(1)
})
