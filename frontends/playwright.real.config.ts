import { defineConfig } from '@playwright/test';

// Đầu-cuối thật (bước 11): trình duyệt Chrome đi qua cổng http://shop.localhost:8000 tới ba dịch vụ thật.
// Không có webServer: người chạy tự dựng hạ tầng (README mục "Bộ đầu-cuối thật"). Một worker vì dùng chung DB và Redis dev.
export default defineConfig({
  testDir: './e2e-real',
  timeout: 60_000,
  workers: 1,
  fullyParallel: false,
  globalSetup: './e2e-real/support/global-setup.ts',
  use: { baseURL: 'http://shop.localhost:8000', channel: 'chrome', headless: true },
});
