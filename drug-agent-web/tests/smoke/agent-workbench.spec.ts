import { test, expect } from '@playwright/test'

test.describe('Agent Workbench Smoke Tests', () => {
  // TC-001: 工作台页面正常加载
  test('TC-001: 工作台页面正常加载', async ({ page }) => {
    await page.goto('/workspace')

    // 验证页面主要元素出现
    // 等待至少一个关键元素可见
    await expect(page.getByText('横渡智能系统', { exact: false }).first()).toBeVisible({ timeout: 15000 })

    // 验证聊天区域占位符出现
    await expect(page.locator('textarea')).toBeVisible()
  })

  // TC-002: 新建会话
  test('TC-002: 点击新建会话，输入框获得焦点', async ({ page }) => {
    await page.goto('/workspace')

    // 等待输入框出现
    const textarea = page.locator('textarea')
    await expect(textarea).toBeVisible({ timeout: 10000 })

    // 点击新建会话按钮
    await page.getByTestId('new-chat-button').click()

    // 验证输入框获得焦点
    await expect(textarea).toBeFocused()
  })

  // TC-003: 发送消息后出现用户消息
  test('TC-003: 发送消息后出现用户消息', async ({ page }) => {
    await page.goto('/workspace')

    // 等待输入框
    const textarea = page.locator('textarea')
    await expect(textarea).toBeVisible({ timeout: 10000 })

    // 输入消息
    const testMessage = '审查标书文件'
    await textarea.fill(testMessage)

    // 点击发送按钮
    await page.getByRole('button', { name: /发送任务/ }).click()

    // 验证用户消息出现在聊天区域（通过特定的CSS类来区分）
    // 用户消息有 bg-slate-900 text-white 类
    const userMessage = page.locator('.bg-slate-900').filter({ hasText: testMessage }).first()
    await expect(userMessage).toBeVisible({ timeout: 5000 })

    // 验证URL包含sessionId（说明创建了会话）
    await expect(page).toHaveURL(/sessionId=/, { timeout: 5000 })
  })

  // TC-004: 进度消息出现
  test('TC-004: 发送消息后出现进度提示', async ({ page }) => {
    await page.goto('/workspace')

    // 发送消息
    const textarea = page.locator('textarea')
    await textarea.fill('生成报告')
    await page.getByRole('button', { name: /发送任务/ }).click()

    // 验证进度消息出现
    await expect(page.getByText('Agent 正在执行深层编排工作流')).toBeVisible({ timeout: 3000 })
  })

  // TC-005: 等待消息处理完成
  test('TC-005: 等待一段时间后进度消息消失', async ({ page }) => {
    await page.goto('/workspace')

    // 发送消息
    const textarea = page.locator('textarea')
    await textarea.fill('测试消息')
    await page.getByRole('button', { name: /发送任务/ }).click()

    // 验证进度消息出现
    await expect(page.getByText('Agent 正在执行深层编排工作流')).toBeVisible({ timeout: 3000 })

    // 等待足够时间让 mock 处理完成
    await page.waitForTimeout(5000)

    // 验证进度消息消失（mock 响应完成）
    await expect(page.getByText('Agent 正在执行深层编排工作流')).not.toBeVisible({ timeout: 5000 })
  })

  // TC-006: 历史会话切换 - 标记为跳过（需要后端）
  test.skip('TC-006: 切换历史会话', async ({ page }) => {
    // 此测试需要后端 API 运行，暂时跳过
  })

  // TC-007: 页面基本可交互无崩溃
  test('TC-007: 页面基本可交互无崩溃', async ({ page }) => {
    const consoleErrors: string[] = []

    page.on('console', msg => {
      if (msg.type() === 'error') {
        consoleErrors.push(msg.text())
      }
    })

    await page.goto('/workspace')

    // 等待页面加载
    await expect(page.locator('textarea')).toBeVisible({ timeout: 15000 })

    // 执行基本交互
    await page.getByTestId('new-chat-button').click()
    const textarea = page.locator('textarea')
    await textarea.fill('测试')
    await page.getByRole('button', { name: /发送任务/ }).click()

    // 等待异步操作
    await page.waitForTimeout(2000)

    // 过滤已知非严重错误（网络/代理错误）
    const severeErrors = consoleErrors.filter(err =>
      !err.includes('favicon') &&
      !err.includes('net::ERR_') &&
      !err.includes('Failed to load resource') &&
      !err.includes('proxy error') &&
      !err.includes('ECONNREFUSED') &&
      !err.includes('status code 500') &&
      !err.includes('AxiosError')
    )

    // 验证没有严重JS错误
    expect(severeErrors).toHaveLength(0)
  })
})
