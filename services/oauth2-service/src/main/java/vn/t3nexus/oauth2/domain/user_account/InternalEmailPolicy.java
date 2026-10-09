package vn.t3nexus.oauth2.domain.user_account;

/** Chính sách miền email nội bộ (INV-ATH-07). Miền khai báo, cấu hình đưa vào ở hạ tầng. */
public interface InternalEmailPolicy {
    boolean isInternal(Email email);
}
