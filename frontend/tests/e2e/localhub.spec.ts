import { test, expect } from '@playwright/test'

test('home page renders LocalHub navigation', async ({ page }) => {
  await page.goto('/')
  await expect(page.getByText('LocalHub')).toBeVisible()
  await expect(page.getByText('AI 客服')).toBeVisible()
})

test('AI chat page can call backend through vite proxy', async ({ page }) => {
  await page.goto('/ai')
  await page.getByPlaceholder('请输入你的问题').fill('order status')
  await page.getByRole('button', { name: '发送' }).click()
  await expect(page.locator('pre')).toContainText(/订单|LocalHub|order/i)
})

test('orders page shows query form', async ({ page }) => {
  await page.goto('/orders')
  await expect(page.getByPlaceholder('订单号')).toBeVisible()
  await expect(page.getByRole('button', { name: '查询状态' })).toBeVisible()
})
