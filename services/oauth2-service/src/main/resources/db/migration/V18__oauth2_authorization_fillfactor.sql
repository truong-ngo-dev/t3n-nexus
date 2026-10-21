-- ============================================================
-- V18 — fillfactor cho bảng bản ghi ủy quyền (feature 02, ADR-0003 mục 8)
--
-- Làm mới token cập nhật cùng một dòng của oauth2_authorization (Spring lưu token mới tại chỗ), nên bảng bị UPDATE nhiều hơn INSERT.
-- Postgres ghi phiên bản dòng mới thay vì sửa tại chỗ: nếu trang còn chỗ trống, phiên bản mới nằm cùng trang và không phải cập nhật
-- chỉ mục (HOT update); trang đầy thì phải sang trang khác, cập nhật mọi chỉ mục và bảng phình dần. fillfactor 70 chừa khoảng 30% mỗi
-- trang cho việc đó, đổi lấy bảng chiếm nhiều chỗ hơn khoảng 30%.
--
-- 70 là số khởi đầu, chưa đo: chỉnh theo phép đo làm mới token (02-login-session/plan.md bước 14, so với fillfactor mặc định 100).
-- Chỉ áp cho dòng ghi từ sau lệnh này; dòng có sẵn không tự xếp lại (bảng mới hoặc nhỏ nên không đáng kể).
-- ============================================================

ALTER TABLE oauth2_authorization SET (fillfactor = 70);
