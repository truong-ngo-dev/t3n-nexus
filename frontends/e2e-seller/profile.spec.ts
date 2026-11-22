import { expect, test, type Page, type Route } from '@playwright/test';

/*
 * Phần hồ sơ, các ca `phần` của `docs/7.feature/c1a-listing/08-seller-ui/1-seller-profile/test.md`:
 * K3 (thông luồng sửa khi chờ duyệt), K10 (thông luồng sửa khi đã duyệt), K7 (bấm đúp chỉ một lời gọi).
 * Chạy thẳng `ng serve`; phiên và hồ sơ giả bằng `page.route`.
 */
const SESSION = '**/bff/auth/session';
const PROFILE = '**/api/seller/seller/profile';
const SHOP_INFO = '**/api/seller/seller/profile/shop-info';

type ProfileStatus = 'PENDING_REVIEW' | 'APPROVED';

const json = (status: number, body: unknown) => ({ status, contentType: 'application/json', body: JSON.stringify(body) });

function profile(status: ProfileStatus) {
  return {
    id: 's-1',
    status,
    sellerType: 'HOUSEHOLD',
    legalInfo: { legalName: 'Nguyễn Văn An', identityNumber: '001203004567', taxCode: '0123456789', businessAddress: '12 Bát Tràng, Gia Lâm, Hà Nội' },
    shop: { shopName: 'Gốm Minh An', description: 'Gốm thủ công', contact: { personName: 'Nguyễn Văn An', phone: '0912345678', address: null } },
    rejectionReason: null,
    submittedAt: '2026-11-18T02:12:00Z',
  };
}

/** Phiên USER và hồ sơ ở trạng thái cho trước. Trả danh sách lời gọi ghi (PUT, PATCH) để ca kiểm. */
async function setup(page: Page, status: ProfileStatus, onWrite: (route: Route) => Promise<void> | void) {
  const writes: { method: string; body: unknown }[] = [];
  await page.route(SESSION, (route) => route.fulfill(json(200, { userId: 'u-1', role: 'USER' })));
  const handler = async (route: Route) => {
    const request = route.request();
    if (request.method() === 'GET') return route.fulfill(json(200, { success: true, data: profile(status) }));
    writes.push({ method: request.method(), body: request.postDataJSON() });
    return onWrite(route);
  };
  await page.route(PROFILE, handler);
  await page.route(SHOP_INFO, handler);
  return writes;
}

const ok = (route: Route) => route.fulfill(json(200, { success: true, data: null }));

test('K3 sửa khi chờ duyệt: "Lưu thay đổi" khóa tới khi đổi một ô; lưu thì PUT đủ thân và về trang xem', async ({ page }) => {
  const writes = await setup(page, 'PENDING_REVIEW', ok);

  await page.goto('/profile');
  await page.getByTestId('edit-profile').click();
  await expect(page).toHaveURL(/\/profile\/edit$/);
  await expect(page.getByTestId('pending-notice')).toBeVisible();

  const save = page.getByTestId('submit');
  await expect(save).toHaveText('Lưu thay đổi');
  await expect(save).toBeDisabled();

  await page.getByTestId('description').fill('Gốm thủ công Bát Tràng');
  await expect(save).toBeEnabled();
  await save.click();

  await expect(page).toHaveURL(/\/profile$/);
  expect(writes).toHaveLength(1);
  expect(writes[0].method).toBe('PUT');
  expect(writes[0].body).toMatchObject({
    sellerType: 'HOUSEHOLD',
    legalInfo: { legalName: 'Nguyễn Văn An', identityNumber: '001203004567' },
    shop: { shopName: 'Gốm Minh An', description: 'Gốm thủ công Bát Tràng' },
  });
});

test('K10 sửa khi đã duyệt: tên và loại hình hiện bằng chữ; lưu thì PATCH chỉ mang ô đã đổi', async ({ page }) => {
  const writes = await setup(page, 'APPROVED', ok);

  await page.goto('/profile');
  await page.getByTestId('edit-shop').click();
  await expect(page).toHaveURL(/\/profile\/edit-shop$/);

  await expect(page.getByTestId('locked-shopName')).toHaveText('Gốm Minh An');
  await expect(page.getByTestId('locked-sellerType')).toHaveText('Hộ kinh doanh');
  await expect(page.getByTestId('shopName')).toHaveCount(0);
  await expect(page.getByTestId('legal-section')).toHaveCount(0);
  await expect(page.getByTestId('sellerType')).toHaveCount(0);

  await page.getByTestId('contactPhone').fill('0987654321');
  await page.getByTestId('submit').click();

  await expect(page).toHaveURL(/\/profile$/);
  expect(writes).toEqual([{ method: 'PATCH', body: { contact: { phone: '0987654321' } } }]);
});

test('K7 bấm đúp "Lưu thay đổi": chỉ một lời gọi', async ({ page }) => {
  let release!: () => void;
  const held = new Promise<void>((resolve) => (release = resolve));
  const writes = await setup(page, 'APPROVED', async (route) => {
    await held;
    await ok(route);
  });

  await page.goto('/profile/edit-shop');
  await page.getByTestId('description').fill('Mô tả mới');
  await page.getByTestId('submit').dblclick();
  await expect(page.getByTestId('submit')).toHaveText('Đang gửi…');
  release();

  await expect(page).toHaveURL(/\/profile$/);
  expect(writes).toHaveLength(1);
});
