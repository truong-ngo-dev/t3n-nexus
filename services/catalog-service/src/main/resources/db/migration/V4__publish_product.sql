-- Đăng sản phẩm (feature 09): thời điểm đăng đầu tiên và bản sao trạng thái người bán.
-- Giải pháp: docs/stage/c1a-listing/09-publish-product/tech.md T1; dữ liệu: docs/service/catalog-service/data.md mục 10.

-- Thời điểm đăng đầu tiên, không đổi sau đó. Nháp chưa từng đăng thì rỗng; đã đăng hoặc đã gỡ thì có.
-- Dòng sản phẩm đã tồn tại đều là nháp (07, 08 chỉ có DRAFT), nên cột rỗng là đúng cho mọi dòng cũ.
ALTER TABLE product ADD COLUMN published_at TIMESTAMPTZ;
ALTER TABLE product ADD CONSTRAINT ck_product_published_at CHECK ((status = 'DRAFT') = (published_at IS NULL));

-- Bản sao trạng thái người bán (không phải aggregate): cập nhật từ seller.profile.approved / rejected.
-- Ghi đè theo version hồ sơ (chỉ khi mới hơn), nên nhận lặp hay sai thứ tự không cần bảng khử trùng theo mã sự kiện.
-- Không có khóa ngoại: tài khoản thuộc bounded context khác.
CREATE TABLE seller_state (
    account_id UUID        NOT NULL,
    status     VARCHAR(20) NOT NULL,
    version    BIGINT      NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT pk_seller_state PRIMARY KEY (account_id),
    CONSTRAINT ck_seller_state_status CHECK (status IN ('APPROVED', 'REJECTED')),
    CONSTRAINT ck_seller_state_version CHECK (version >= 0)
);
