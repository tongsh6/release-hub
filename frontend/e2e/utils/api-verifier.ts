/**
 * API Verifier — 在 E2E 测试中直连后端 API 验证数据库和业务状态。
 *
 * 从 Puppeteer 测试环境中发起 HTTP 请求到后端 API，验证 UI 操作后
 * 的数据库状态、Run 记录、Git 操作结果是否与预期一致。
 */
export class ApiVerifier {
  private apiBase: string
  private token: string | null = null

  constructor(apiBase = 'http://localhost:8080') {
    this.apiBase = apiBase
  }

  /**
   * 登录获取 JWT token
   */
  async login(username = 'admin', password = 'admin'): Promise<string> {
    const res = await fetch(`${this.apiBase}/api/v1/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, password }),
    })
    const body = await res.json()
    if (!body?.data?.token) {
      throw new Error(`Login failed: ${JSON.stringify(body)}`)
    }
    this.token = body.data.token
    return this.token
  }

  private authHeaders(): Record<string, string> {
    if (!this.token) throw new Error('Not logged in. Call login() first.')
    return {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${this.token}`,
    }
  }

  /**
   * 查询发布窗口详情（验证状态流转）
   */
  async getReleaseWindow(id: string): Promise<Record<string, unknown>> {
    const res = await fetch(`${this.apiBase}/api/v1/release-windows/${id}`, {
      headers: this.authHeaders(),
    })
    const body = await res.json()
    return body?.data as Record<string, unknown>
  }

  /**
   * 查询窗口关联的迭代列表
   */
  async getWindowIterations(windowId: string): Promise<unknown[]> {
    const res = await fetch(
      `${this.apiBase}/api/v1/release-windows/${windowId}/iterations`,
      { headers: this.authHeaders() }
    )
    const body = await res.json()
    return (body?.data ?? []) as unknown[]
  }

  /**
   * 查询 Branch Status（验证 merge 结果）
   */
  async getBranchStatus(windowId: string): Promise<Record<string, unknown>> {
    const res = await fetch(
      `${this.apiBase}/api/v1/release-windows/${windowId}/branch-status`,
      { headers: this.authHeaders() }
    )
    const body = await res.json()
    return body?.data as Record<string, unknown>
  }

  /**
   * 查询 Run 分页列表（按 windowKey 过滤）
   */
  async getRunsByWindow(windowKey: string): Promise<unknown[]> {
    const params = new URLSearchParams({
      windowKey,
      page: '1',
      size: '20',
    })
    const res = await fetch(
      `${this.apiBase}/api/v1/runs/paged?${params}`,
      { headers: this.authHeaders() }
    )
    const body = await res.json()
    return (body?.data ?? []) as unknown[]
  }

  /**
   * 查询 Dashboard 统计
   */
  async getDashboardStats(): Promise<Record<string, unknown>> {
    const res = await fetch(`${this.apiBase}/api/v1/dashboard/stats`, {
      headers: this.authHeaders(),
    })
    const body = await res.json()
    return body?.data as Record<string, unknown>
  }
}
