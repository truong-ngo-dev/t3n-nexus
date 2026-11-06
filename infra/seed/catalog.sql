-- Dữ liệu khởi tạo: bộ mẫu cấu trúc danh mục của catalog-service (feature 06): 10 thương hiệu, 13 mẫu thuộc tính (35 tùy chọn),
-- 22 danh mục (3 cấp một, 6 cấp hai, 13 lá), 57 dòng gán. Nội dung và lý do: docs/stage/c1a-listing/06-category-seed-data/tech.md T2.
-- Chạy tay một lần khi dựng môi trường, SAU KHI Flyway đã tạo bảng. Không phải migration, không có trong mã dịch vụ.
-- Không phát event, không ghi kiểm toán (dữ liệu khởi tạo thuộc môi trường, như tài khoản Quản trị).
--
--   psql -f catalog.sql        (không tham số; vào cơ sở dữ liệu catalog_db)
--
-- Một giao dịch: hoặc đủ cả bộ mẫu, hoặc không có gì. Chạy lại vô hại: nếu brand, attribute_template hoặc category đã có dòng
-- thì không thêm gì (không "bổ sung phần thiếu", để không nạp lại thứ Quản trị đã đổi hay ngừng dùng).
-- Lỗi (thiếu bảng, câu ghi lỗi) thì dừng, mã thoát khác 0, không ghi gì. Hai lần chạy đồng thời nối đuôi nhau (khóa tư vấn).

\set ON_ERROR_STOP on

-- Phải có đủ bảng (chưa chạy Flyway, hoặc nhầm cơ sở dữ liệu thì dừng, không tạo bảng).
DO $$
DECLARE missing text;
BEGIN
    SELECT string_agg(t, ', ') INTO missing
      FROM unnest(ARRAY['brand', 'attribute_template', 'attribute_option', 'category', 'category_attribute_assignment']) AS t
     WHERE to_regclass(t) IS NULL;
    IF missing IS NOT NULL THEN
        RAISE EXCEPTION 'seed-catalog: thiếu bảng % (chưa chạy Flyway, hoặc nhầm cơ sở dữ liệu)', missing;
    END IF;
END $$;

BEGIN;
SELECT pg_advisory_xact_lock(hashtext('seed-catalog'));

SELECT (NOT EXISTS (SELECT 1 FROM brand)
        AND NOT EXISTS (SELECT 1 FROM attribute_template)
        AND NOT EXISTS (SELECT 1 FROM category)) AS can_seed
\gset

\if :can_seed

-- UUID v7: 48 bit mili giây hiện tại ghép vào UUID ngẫu nhiên, bật hai bit 52, 53 của byte 6 để nibble phiên bản từ 0100 (v4)
-- thành 0111 (v7); Postgres 16 chưa có uuidv7(). Cùng công thức với admin.sql.
CREATE FUNCTION pg_temp.uuid_v7() RETURNS uuid LANGUAGE sql VOLATILE AS $f$
    SELECT encode(set_bit(set_bit(overlay(uuid_send(gen_random_uuid())
                                          placing substring(int8send((extract(epoch FROM clock_timestamp()) * 1000)::bigint) FROM 3)
                                          FROM 1 FOR 6), 52, 1), 53, 1), 'hex')::uuid
$f$;

-- Nội dung bộ mẫu. name_key ghi sẵn, bằng đúng kết quả hàm khóa tên của miền (bỏ khoảng trắng thừa, bỏ dấu, chữ thường);
-- test CatalogSeedTest đối chiếu từng dòng với NameKey.of.

CREATE TEMP TABLE s_brand (pos int, name text, name_key text) ON COMMIT DROP;
INSERT INTO s_brand VALUES
    (0, 'Coolmate', 'coolmate'),
    (1, 'Uniqlo', 'uniqlo'),
    (2, 'Nike', 'nike'),
    (3, 'Adidas', 'adidas'),
    (4, 'Samsung', 'samsung'),
    (5, 'Apple', 'apple'),
    (6, 'Xiaomi', 'xiaomi'),
    (7, 'Sony', 'sony'),
    (8, 'Lock&Lock', 'lock&lock'),
    (9, 'Sunhouse', 'sunhouse');

CREATE TEMP TABLE s_template (id uuid NOT NULL DEFAULT pg_temp.uuid_v7(), pos int, name text, display_name text, hint text, input_type text, unit text) ON COMMIT DROP;
INSERT INTO s_template (pos, name, display_name, hint, input_type, unit) VALUES
    (0, 'color', 'Màu sắc', 'Chọn màu chính của sản phẩm', 'SINGLE_SELECT', NULL),
    (1, 'size_clothing', 'Kích cỡ quần áo', NULL, 'SINGLE_SELECT', NULL),
    (2, 'material', 'Chất liệu', 'Chọn các chất liệu chính', 'MULTI_SELECT', NULL),
    (3, 'storage', 'Dung lượng bộ nhớ', NULL, 'SINGLE_SELECT', NULL),
    (4, 'ram', 'RAM', NULL, 'SINGLE_SELECT', NULL),
    (5, 'origin', 'Xuất xứ', NULL, 'SINGLE_SELECT', NULL),
    (6, 'screen_size', 'Kích thước màn hình', 'Đo theo đường chéo', 'NUMBER', 'inch'),
    (7, 'battery_capacity', 'Dung lượng pin', NULL, 'NUMBER', 'mAh'),
    (8, 'warranty_months', 'Thời gian bảo hành', NULL, 'NUMBER', 'tháng'),
    (9, 'capacity_liter', 'Dung tích', NULL, 'NUMBER', 'lít'),
    (10, 'is_waterproof', 'Chống nước', NULL, 'BOOLEAN', NULL),
    (11, 'release_date', 'Ngày ra mắt', NULL, 'DATE', NULL),
    (12, 'model', 'Mã model', 'Mã model do nhà sản xuất công bố', 'TEXT', NULL);

CREATE TEMP TABLE s_option (id uuid NOT NULL DEFAULT pg_temp.uuid_v7(), template_name text, pos int, value text, display_value text) ON COMMIT DROP;
INSERT INTO s_option (template_name, pos, value, display_value) VALUES
    ('color', 0, 'den', 'Đen'),
    ('color', 1, 'trang', 'Trắng'),
    ('color', 2, 'xam', 'Xám'),
    ('color', 3, 'do', 'Đỏ'),
    ('color', 4, 'xanh-duong', 'Xanh dương'),
    ('color', 5, 'xanh-la', 'Xanh lá'),
    ('color', 6, 'vang', 'Vàng'),
    ('color', 7, 'hong', 'Hồng'),
    ('size_clothing', 0, 's', 'S'),
    ('size_clothing', 1, 'm', 'M'),
    ('size_clothing', 2, 'l', 'L'),
    ('size_clothing', 3, 'xl', 'XL'),
    ('size_clothing', 4, 'xxl', 'XXL'),
    ('material', 0, 'cotton', 'Cotton'),
    ('material', 1, 'polyester', 'Polyester'),
    ('material', 2, 'denim', 'Denim'),
    ('material', 3, 'linen', 'Vải lanh'),
    ('material', 4, 'silicone', 'Silicone'),
    ('material', 5, 'nhua', 'Nhựa'),
    ('storage', 0, '64gb', '64 GB'),
    ('storage', 1, '128gb', '128 GB'),
    ('storage', 2, '256gb', '256 GB'),
    ('storage', 3, '512gb', '512 GB'),
    ('storage', 4, '1tb', '1 TB'),
    ('ram', 0, '4gb', '4 GB'),
    ('ram', 1, '6gb', '6 GB'),
    ('ram', 2, '8gb', '8 GB'),
    ('ram', 3, '12gb', '12 GB'),
    ('ram', 4, '16gb', '16 GB'),
    ('origin', 0, 'viet-nam', 'Việt Nam'),
    ('origin', 1, 'trung-quoc', 'Trung Quốc'),
    ('origin', 2, 'nhat-ban', 'Nhật Bản'),
    ('origin', 3, 'han-quoc', 'Hàn Quốc'),
    ('origin', 4, 'thai-lan', 'Thái Lan'),
    ('origin', 5, 'my', 'Mỹ');

-- path = "Cấp một/Cấp hai/Lá"; parent_path rỗng ở cấp một. Cha nằm trước con trong cùng bảng nên không cần thứ tự chèn riêng.
CREATE TEMP TABLE s_category (id uuid NOT NULL DEFAULT pg_temp.uuid_v7(), level int, path text, parent_path text, pos int, name text, name_key text) ON COMMIT DROP;
INSERT INTO s_category (level, path, parent_path, pos, name, name_key) VALUES
    (1, 'Thời trang', NULL, 0, 'Thời trang', 'thoi trang'),
    (2, 'Thời trang/Thời trang nam', 'Thời trang', 0, 'Thời trang nam', 'thoi trang nam'),
    (3, 'Thời trang/Thời trang nam/Áo thun nam', 'Thời trang/Thời trang nam', 0, 'Áo thun nam', 'ao thun nam'),
    (3, 'Thời trang/Thời trang nam/Áo sơ mi nam', 'Thời trang/Thời trang nam', 1, 'Áo sơ mi nam', 'ao so mi nam'),
    (3, 'Thời trang/Thời trang nam/Quần jean nam', 'Thời trang/Thời trang nam', 2, 'Quần jean nam', 'quan jean nam'),
    (2, 'Thời trang/Thời trang nữ', 'Thời trang', 1, 'Thời trang nữ', 'thoi trang nu'),
    (3, 'Thời trang/Thời trang nữ/Áo thun nữ', 'Thời trang/Thời trang nữ', 0, 'Áo thun nữ', 'ao thun nu'),
    (3, 'Thời trang/Thời trang nữ/Váy nữ', 'Thời trang/Thời trang nữ', 1, 'Váy nữ', 'vay nu'),
    (1, 'Điện tử', NULL, 1, 'Điện tử', 'dien tu'),
    (2, 'Điện tử/Điện thoại', 'Điện tử', 0, 'Điện thoại', 'dien thoai'),
    (3, 'Điện tử/Điện thoại/Smartphone', 'Điện tử/Điện thoại', 0, 'Smartphone', 'smartphone'),
    (3, 'Điện tử/Điện thoại/Điện thoại phổ thông', 'Điện tử/Điện thoại', 1, 'Điện thoại phổ thông', 'dien thoai pho thong'),
    (2, 'Điện tử/Phụ kiện điện thoại', 'Điện tử', 1, 'Phụ kiện điện thoại', 'phu kien dien thoai'),
    (3, 'Điện tử/Phụ kiện điện thoại/Ốp lưng', 'Điện tử/Phụ kiện điện thoại', 0, 'Ốp lưng', 'op lung'),
    (3, 'Điện tử/Phụ kiện điện thoại/Sạc và cáp', 'Điện tử/Phụ kiện điện thoại', 1, 'Sạc và cáp', 'sac va cap'),
    (2, 'Điện tử/Âm thanh', 'Điện tử', 2, 'Âm thanh', 'am thanh'),
    (3, 'Điện tử/Âm thanh/Tai nghe', 'Điện tử/Âm thanh', 0, 'Tai nghe', 'tai nghe'),
    (3, 'Điện tử/Âm thanh/Loa bluetooth', 'Điện tử/Âm thanh', 1, 'Loa bluetooth', 'loa bluetooth'),
    (1, 'Nhà cửa và đời sống', NULL, 2, 'Nhà cửa và đời sống', 'nha cua va doi song'),
    (2, 'Nhà cửa và đời sống/Đồ dùng nhà bếp', 'Nhà cửa và đời sống', 0, 'Đồ dùng nhà bếp', 'do dung nha bep'),
    (3, 'Nhà cửa và đời sống/Đồ dùng nhà bếp/Nồi cơm điện', 'Nhà cửa và đời sống/Đồ dùng nhà bếp', 0, 'Nồi cơm điện', 'noi com dien'),
    (3, 'Nhà cửa và đời sống/Đồ dùng nhà bếp/Bình giữ nhiệt', 'Nhà cửa và đời sống/Đồ dùng nhà bếp', 1, 'Bình giữ nhiệt', 'binh giu nhiet');

CREATE TEMP TABLE s_assignment (category_path text, template_name text, required boolean, display_order int, constraints jsonb) ON COMMIT DROP;
INSERT INTO s_assignment VALUES
    ('Thời trang/Thời trang nam/Áo thun nam', 'color', true, 0, '{}'::jsonb),
    ('Thời trang/Thời trang nam/Áo thun nam', 'size_clothing', true, 1, '{}'::jsonb),
    ('Thời trang/Thời trang nam/Áo thun nam', 'material', false, 2, '{"maxSelections":2}'::jsonb),
    ('Thời trang/Thời trang nam/Áo thun nam', 'origin', false, 3, '{}'::jsonb),
    ('Thời trang/Thời trang nam/Áo sơ mi nam', 'color', true, 0, '{}'::jsonb),
    ('Thời trang/Thời trang nam/Áo sơ mi nam', 'size_clothing', true, 1, '{}'::jsonb),
    ('Thời trang/Thời trang nam/Áo sơ mi nam', 'material', false, 2, '{"maxSelections":2}'::jsonb),
    ('Thời trang/Thời trang nam/Áo sơ mi nam', 'origin', false, 3, '{}'::jsonb),
    ('Thời trang/Thời trang nam/Quần jean nam', 'color', true, 0, '{}'::jsonb),
    ('Thời trang/Thời trang nam/Quần jean nam', 'size_clothing', true, 1, '{}'::jsonb),
    ('Thời trang/Thời trang nam/Quần jean nam', 'material', false, 2, '{"maxSelections":2}'::jsonb),
    ('Thời trang/Thời trang nam/Quần jean nam', 'origin', false, 3, '{}'::jsonb),
    ('Thời trang/Thời trang nữ/Áo thun nữ', 'color', true, 0, '{}'::jsonb),
    ('Thời trang/Thời trang nữ/Áo thun nữ', 'size_clothing', true, 1, '{}'::jsonb),
    ('Thời trang/Thời trang nữ/Áo thun nữ', 'material', false, 2, '{"maxSelections":2}'::jsonb),
    ('Thời trang/Thời trang nữ/Áo thun nữ', 'origin', false, 3, '{}'::jsonb),
    ('Thời trang/Thời trang nữ/Váy nữ', 'color', true, 0, '{}'::jsonb),
    ('Thời trang/Thời trang nữ/Váy nữ', 'size_clothing', true, 1, '{}'::jsonb),
    ('Thời trang/Thời trang nữ/Váy nữ', 'material', false, 2, '{"maxSelections":2}'::jsonb),
    ('Thời trang/Thời trang nữ/Váy nữ', 'origin', false, 3, '{}'::jsonb),
    ('Điện tử/Điện thoại/Smartphone', 'color', true, 0, '{}'::jsonb),
    ('Điện tử/Điện thoại/Smartphone', 'storage', true, 1, '{}'::jsonb),
    ('Điện tử/Điện thoại/Smartphone', 'ram', true, 2, '{}'::jsonb),
    ('Điện tử/Điện thoại/Smartphone', 'screen_size', true, 3, '{"min":3,"max":8}'::jsonb),
    ('Điện tử/Điện thoại/Smartphone', 'battery_capacity', false, 4, '{"min":1000,"max":10000,"integerOnly":true}'::jsonb),
    ('Điện tử/Điện thoại/Smartphone', 'warranty_months', true, 5, '{"min":0,"max":60,"integerOnly":true}'::jsonb),
    ('Điện tử/Điện thoại/Smartphone', 'release_date', false, 6, '{"inputPrecision":"MONTH","minDate":"2015-01-01"}'::jsonb),
    ('Điện tử/Điện thoại/Smartphone', 'is_waterproof', false, 7, '{}'::jsonb),
    ('Điện tử/Điện thoại/Smartphone', 'origin', false, 8, '{}'::jsonb),
    ('Điện tử/Điện thoại/Smartphone', 'model', true, 9, '{"maxLength":50}'::jsonb),
    ('Điện tử/Điện thoại/Điện thoại phổ thông', 'color', true, 0, '{}'::jsonb),
    ('Điện tử/Điện thoại/Điện thoại phổ thông', 'battery_capacity', false, 1, '{"min":1000,"max":10000,"integerOnly":true}'::jsonb),
    ('Điện tử/Điện thoại/Điện thoại phổ thông', 'warranty_months', true, 2, '{"min":0,"max":60,"integerOnly":true}'::jsonb),
    ('Điện tử/Điện thoại/Điện thoại phổ thông', 'model', true, 3, '{"maxLength":50}'::jsonb),
    ('Điện tử/Điện thoại/Điện thoại phổ thông', 'origin', false, 4, '{}'::jsonb),
    ('Điện tử/Phụ kiện điện thoại/Ốp lưng', 'color', true, 0, '{}'::jsonb),
    ('Điện tử/Phụ kiện điện thoại/Ốp lưng', 'material', false, 1, '{"maxSelections":2}'::jsonb),
    ('Điện tử/Phụ kiện điện thoại/Ốp lưng', 'model', true, 2, '{"maxLength":50}'::jsonb),
    ('Điện tử/Phụ kiện điện thoại/Sạc và cáp', 'color', true, 0, '{}'::jsonb),
    ('Điện tử/Phụ kiện điện thoại/Sạc và cáp', 'warranty_months', false, 1, '{"min":0,"max":36,"integerOnly":true}'::jsonb),
    ('Điện tử/Phụ kiện điện thoại/Sạc và cáp', 'origin', false, 2, '{}'::jsonb),
    ('Điện tử/Âm thanh/Tai nghe', 'color', true, 0, '{}'::jsonb),
    ('Điện tử/Âm thanh/Tai nghe', 'battery_capacity', false, 1, '{"min":100,"max":10000,"integerOnly":true}'::jsonb),
    ('Điện tử/Âm thanh/Tai nghe', 'is_waterproof', false, 2, '{}'::jsonb),
    ('Điện tử/Âm thanh/Tai nghe', 'warranty_months', true, 3, '{"min":0,"max":36,"integerOnly":true}'::jsonb),
    ('Điện tử/Âm thanh/Tai nghe', 'origin', false, 4, '{}'::jsonb),
    ('Điện tử/Âm thanh/Loa bluetooth', 'color', true, 0, '{}'::jsonb),
    ('Điện tử/Âm thanh/Loa bluetooth', 'battery_capacity', false, 1, '{"min":100,"max":20000,"integerOnly":true}'::jsonb),
    ('Điện tử/Âm thanh/Loa bluetooth', 'is_waterproof', false, 2, '{}'::jsonb),
    ('Điện tử/Âm thanh/Loa bluetooth', 'warranty_months', true, 3, '{"min":0,"max":36,"integerOnly":true}'::jsonb),
    ('Nhà cửa và đời sống/Đồ dùng nhà bếp/Nồi cơm điện', 'color', true, 0, '{}'::jsonb),
    ('Nhà cửa và đời sống/Đồ dùng nhà bếp/Nồi cơm điện', 'capacity_liter', true, 1, '{"min":0.5,"max":10}'::jsonb),
    ('Nhà cửa và đời sống/Đồ dùng nhà bếp/Nồi cơm điện', 'warranty_months', true, 2, '{"min":0,"max":36,"integerOnly":true}'::jsonb),
    ('Nhà cửa và đời sống/Đồ dùng nhà bếp/Nồi cơm điện', 'origin', false, 3, '{}'::jsonb),
    ('Nhà cửa và đời sống/Đồ dùng nhà bếp/Bình giữ nhiệt', 'color', true, 0, '{}'::jsonb),
    ('Nhà cửa và đời sống/Đồ dùng nhà bếp/Bình giữ nhiệt', 'capacity_liter', true, 1, '{"min":0.1,"max":5}'::jsonb),
    ('Nhà cửa và đời sống/Đồ dùng nhà bếp/Bình giữ nhiệt', 'origin', false, 2, '{}'::jsonb);

-- Ghi theo thứ tự phụ thuộc: thương hiệu, mẫu, tùy chọn, danh mục, dòng gán. Mọi thực thể đang dùng, version 0, cùng một thời điểm.
INSERT INTO brand (id, name, name_key, status, version, created_at, updated_at)
SELECT pg_temp.uuid_v7(), name, name_key, 'ACTIVE', 0, now(), now() FROM s_brand ORDER BY pos;

INSERT INTO attribute_template (id, name, display_name, hint, input_type, unit, status, version, created_at, updated_at)
SELECT id, name, display_name, hint, input_type, unit, 'ACTIVE', 0, now(), now() FROM s_template;

INSERT INTO attribute_option (id, template_id, value, display_value, sort_order, status, created_at)
SELECT o.id, t.id, o.value, o.display_value, o.pos, 'ACTIVE', now()
  FROM s_option o JOIN s_template t ON t.name = o.template_name;

INSERT INTO category (id, parent_id, level, name, name_key, sort_order, status, version, created_at, updated_at)
SELECT c.id, p.id, c.level, c.name, c.name_key, c.pos, 'ACTIVE', 0, now(), now()
  FROM s_category c LEFT JOIN s_category p ON p.path = c.parent_path;

INSERT INTO category_attribute_assignment (category_id, template_id, required, display_order, constraints)
SELECT c.id, t.id, a.required, a.display_order, a.constraints
  FROM s_assignment a JOIN s_category c ON c.path = a.category_path JOIN s_template t ON t.name = a.template_name;

SELECT format('seed-catalog: created - đã nạp bộ mẫu: brand=%s attribute_template=%s attribute_option=%s category=%s assignment=%s',
       (SELECT count(*) FROM brand), (SELECT count(*) FROM attribute_template), (SELECT count(*) FROM attribute_option),
       (SELECT count(*) FROM category), (SELECT count(*) FROM category_attribute_assignment)) AS msg
\gset
\echo :msg

\else
  \echo 'seed-catalog: exists - brand, attribute_template hoặc category đã có dòng, không thêm gì'
\endif

COMMIT;
