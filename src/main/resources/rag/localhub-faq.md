# LocalHub FAQ

## 登录
用户通过手机号和验证码登录。验证码会写入 Redis，并且验证码登录成功后会被删除。退出登录会删除 Redis Token。

## 店铺搜索
用户可以通过 `/api/shops/search` 按店铺名称搜索，也可以通过 `/api/shops/nearby` 查询附近店铺。

## 优惠券
普通优惠券通过 `/api/vouchers/{voucherId}/receive` 领取。秒杀券通过 `/api/seckill-vouchers/{voucherId}/orders` 下单。

## 秒杀订单
秒杀接口先返回订单号，后端异步创建订单。用户可以通过 `/api/voucher-orders/{orderId}/status` 查询状态。常见状态包括 PROCESSING、SUCCESS、FAILED、DUPLICATE。

## 支付和关单
未支付订单状态为 1，支付成功状态为 2，取消或自动关单状态为 4。系统会定时关闭超时未支付订单。

## 预约
用户可以通过 `/api/reservations` 创建预约，通过 `/api/reservations/me` 查询自己的预约，通过 `/api/reservations/{id}/cancel` 取消预约。

## 限流
验证码、秒杀、预约、AI 客服等接口使用 Redis ZSet + Lua + AOP 做滑动窗口限流。
