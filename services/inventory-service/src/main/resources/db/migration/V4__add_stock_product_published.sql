-- Tách trạng thái "product cha đang publish" ra khỏi seller_active để tránh xung đột
-- khi variant mới được thêm vào product đã publish (seller_active không còn đủ để biểu diễn 2 khái niệm độc lập)
ALTER TABLE stock
    ADD COLUMN product_published BOOLEAN NOT NULL DEFAULT TRUE;

-- Best-effort backfill: seller_active=true chỉ có thể đạt được trước đây thông qua publish snapshot,
-- nên dùng chính giá trị đó làm proxy cho product_published của dữ liệu cũ.
UPDATE stock SET product_published = seller_active;
