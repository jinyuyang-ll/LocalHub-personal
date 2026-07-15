SELECT stock AS negative_stock
FROM tb_seckill_voucher
WHERE stock < 0;

SELECT user_id, voucher_id, COUNT(*) AS duplicate_count
FROM tb_voucher_order
GROUP BY user_id, voucher_id
HAVING COUNT(*) > 1;

SELECT status, COUNT(*) AS outbox_count
FROM tb_outbox_event
GROUP BY status;
