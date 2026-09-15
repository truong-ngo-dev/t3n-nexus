-- ============================================================
-- scheduler-service — V4: scheduled_job.job_name
-- ============================================================

-- job_name — nhan hien thi do Admin dat luc tao, KHAC task_type (dinh tuyen ky thuat cho consumer,
-- opaque, khong sua duoc). Sua duoc qua edit(). Dung de search (ILIKE-style, LOWER()+LIKE o tang JPQL)
-- tren Admin List UI thay vi loc theo task_type. Bang dang rong (chua seed job that) nen NOT NULL
-- them thang duoc, khong can DEFAULT.
--
-- KHONG tao index cho cot nay: search dung LIKE '%...%' (wildcard ca 2 dau) khong tan dung duoc btree
-- index thuong. O cardinality hien tai (vai chuc job) sequential scan la du re; can GIN + pg_trgm mo
-- rong sau neu that su can, chua lam o day.
ALTER TABLE scheduled_job ADD COLUMN job_name VARCHAR(200) NOT NULL;
