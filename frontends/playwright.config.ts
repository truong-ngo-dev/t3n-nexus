import { defineConfig } from '@playwright/test';

// Khung (bước 3): chạy thẳng ng serve ở cổng 4201. Đầu-cuối thật (bước 11) đi qua cổng ở http://shop.localhost:8000.
export default defineConfig({
  testDir: './e2e',
  timeout: 30_000,
  use: { baseURL: 'http://localhost:4201', channel: 'chrome', headless: true },
  webServer: {
    command: 'npm run start',
    url: 'http://localhost:4201',
    reuseExistingServer: true,
    timeout: 240_000,
  },
});
