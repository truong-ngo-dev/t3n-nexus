import { defineConfig } from '@playwright/test';

// Playwright giả của seller-web (KT-05): chạy thẳng ng serve ở cổng 4202, phiên và hồ sơ giả bằng page.route (không có BFF).
// Chạy thật qua cổng nằm ở bước 9 của lát 11a.
export default defineConfig({
  testDir: './e2e-seller',
  timeout: 30_000,
  use: { baseURL: 'http://localhost:4202', channel: 'chrome', headless: true },
  webServer: {
    command: 'npm run start:seller',
    url: 'http://localhost:4202',
    reuseExistingServer: true,
    timeout: 240_000,
  },
});
