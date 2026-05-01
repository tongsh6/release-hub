/* global process, console */
import fs from 'fs'
import path from 'path'

const root = path.resolve(process.cwd(), 'src')
const skipDir = path.resolve(root, 'i18n', 'messages')
const exts = new Set(['.vue', '.ts', '.tsx', '.js'])
const cnRegex = /[\u4e00-\u9fff]/

function walk(dir, files) {
  const entries = fs.readdirSync(dir, { withFileTypes: true })
  for (const entry of entries) {
    const full = path.join(dir, entry.name)
    if (full.startsWith(skipDir)) continue
    if (entry.isDirectory()) {
      walk(full, files)
    } else {
      const ext = path.extname(entry.name)
      if (exts.has(ext)) files.push(full)
    }
  }
}

function stripComments(content, isVue) {
  let s = content
  s = s.replace(/\/\*[\s\S]*?\*\//g, '')
  s = s.replace(/(^|\s)\/\/.*$/gm, '')
  if (isVue) s = s.replace(/<!--[\s\S]*?-->/g, '')
  return s
}

function checkFile(file) {
  const isVue = file.endsWith('.vue')
  const content = fs.readFileSync(file, 'utf8')
  const clean = stripComments(content, isVue)
  const lines = clean.split(/\r?\n/)
  const issues = []
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i]
    if (!cnRegex.test(line)) continue
    if (line.includes('$t(') || line.includes(' t(')) continue
    issues.push({ line: i + 1, text: line.trim() })
  }
  return issues
}

const files = []
walk(root, files)
const allIssues = []
for (const f of files) {
  const issues = checkFile(f)
  if (issues.length) allIssues.push({ file: f, issues })
}

if (allIssues.length) {
  console.error('i18n check failed:')
  for (const { file, issues } of allIssues) {
    for (const it of issues) {
      console.error(`${file}:${it.line} ${it.text}`)
    }
  }
  process.exit(1)
} else {
  console.log('i18n check passed')
}
