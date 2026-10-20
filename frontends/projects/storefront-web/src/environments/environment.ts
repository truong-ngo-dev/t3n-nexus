/** Cấu hình theo môi trường. C0 chỉ có môi trường dev qua cổng (tech.md T7); tách theo môi trường khi có triển khai. */
export const environment = {
  /** Host `auth` qua cổng: Xác thực công khai ở đây (ADR-0006). */
  authBaseUrl: 'http://auth.localhost:8000',
};
