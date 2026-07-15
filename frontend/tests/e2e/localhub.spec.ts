import { test, expect } from '@playwright/test'

test('home page renders navigation and shops', async ({ page }) => {
  await page.route('**/api/shops/search**', route => route.fulfill({
    contentType: 'application/json', body: JSON.stringify({ success: true, data: [{ id: 1, name: '测试店铺', address: '测试路 1 号' }] })
  }))
  await page.goto('/')
  await expect(page.getByText('LocalHub')).toBeVisible()
  await expect(page.getByText('测试店铺')).toBeVisible()
})

test('AI chat consumes SSE chunks', async ({ page }) => {
  await page.route('**/api/ai/chat/stream**', route => route.fulfill({
    contentType: 'text/event-stream',
    body: 'event: message\ndata: 第一段\n\nevent: message\ndata: 第二段\n\nevent: done\ndata: [DONE]\n\n'
  }))
  await page.goto('/ai')
  await page.getByPlaceholder('请输入你的问题').fill('订单状态')
  await page.getByRole('button', { name: '流式发送' }).click()
  await expect(page.getByText('第一段第二段')).toBeVisible()
})

test('order polling stops after final state', async ({ page }) => {
  let calls = 0
  await page.route('**/api/voucher-orders/*/status', route => {
    calls += 1
    return route.fulfill({ contentType: 'application/json', body: JSON.stringify({ success: true, data: calls < 2 ? 'PROCESSING' : 'SUCCESS' }) })
  })
  await page.goto('/orders?orderId=1000001&poll=1')
  await expect(page.locator('pre')).toContainText('SUCCESS', { timeout: 5_000 })
  await expect(page.getByRole('button', { name: '自动轮询' })).toBeVisible()
})

test('AI reservation requires preview before confirmation', async ({ page }) => {
  await page.addInitScript(() => localStorage.setItem('token', 'e2e-token'))
  await page.route('**/api/ai/reservations/preview', route => route.fulfill({
    contentType: 'application/json', body: JSON.stringify({ success: true, data: {
      confirmationToken: 'once', shopName: '测试店铺', reserveTime: '2027-01-01T19:00:00', remark: '靠窗'
    } })
  }))
  await page.route('**/api/ai/reservations/confirm', route => route.fulfill({
    contentType: 'application/json', body: JSON.stringify({ success: true, data: 88 })
  }))
  await page.goto('/ai')
  await page.getByPlaceholder('店铺 ID').fill('1')
  await page.locator('input[type="datetime-local"]').fill('2027-01-01T19:00')
  await page.getByPlaceholder('备注').fill('靠窗')
  await page.getByRole('button', { name: '预览预约' }).click()
  await expect(page.getByText('店铺：测试店铺')).toBeVisible()
  await page.getByRole('button', { name: '确认创建' }).click()
  await expect(page.getByText('预约创建成功，编号：88')).toBeVisible()
})
