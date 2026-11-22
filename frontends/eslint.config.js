const tseslint = require('typescript-eslint');

const legacyVocabulary = '^(CUSTOMER|SELLER|GUEST|SHIPPER|webgw|oss_id|fullName)$';
const legacyMessage = 'Từ vựng của thiết kế cũ không quay lại (features 05 flow.md R13); vai chỉ là USER hoặc ADMIN';

const uiLibraries = { group: ['@angular/material', '@angular/material/*', '@angular/cdk', '@angular/cdk/*'], message: 'Thư viện logic không import giao diện' };
const appCode = { group: ['**/storefront-web/**', '@t3n/storefront-web', '**/seller-web/**', '@t3n/seller-web'], message: 'Thư viện không import mã ứng dụng' };
// ứng dụng không import ứng dụng khác (ADR-0007)
const otherApp = (name) => ({ group: [`**/${name}/**`, `@t3n/${name}`], message: 'Ứng dụng không import ứng dụng khác' });
const appBoundary = (app, other) => ({
  files: [`projects/${app}/**/*.ts`],
  rules: { 'no-restricted-imports': ['error', { patterns: [otherApp(other)] }] },
});

// Tầng trong một ứng dụng (5.convention/frontend-structure.md mục 3):
//   features/<A> -> core, shared, @t3n/*   (không import features/<B>)
//   core         -> shared, @t3n/*         (không import features; app.routes.ts ở gốc app/ mới trỏ tới features)
//   shared       -> @t3n/*                 (không import core, features)
// Cấu hình phẳng của ESLint: khối sau ghi đè giá trị luật của khối trước cho cùng tệp, nên mỗi khối phải mang lại luật otherApp.
const fs = require('fs');
const path = require('path');
const featureNames = (app) => {
  const dir = path.join(__dirname, 'projects', app, 'src', 'app', 'features');
  return fs.existsSync(dir) ? fs.readdirSync(dir, { withFileTypes: true }).filter((d) => d.isDirectory()).map((d) => d.name) : [];
};
// import tương đối không có chữ "features" khi đi từ features/A sang features/B (`../../B/...`), nên liệt kê theo tên và độ sâu
const featureImport = (name) => [`**/features/${name}`, `**/features/${name}/**`, ...['..', '../..', '../../..', '../../../..'].flatMap((up) => [`${up}/${name}`, `${up}/${name}/**`])];
const layer = (files, other, ...banned) => ({
  files,
  rules: { 'no-restricted-imports': ['error', { patterns: [otherApp(other), ...banned] }] },
});
const appLayers = (app, other) => {
  const root = `projects/${app}/src/app`;
  const features = featureNames(app);
  return [
    layer([`${root}/core/**/*.ts`], other, { group: ['**/features', '**/features/**'], message: 'core không import features (chỉ app.routes.ts trỏ tới features)' }),
    layer([`${root}/shared/**/*.ts`], other, { group: ['**/core', '**/core/**', '**/features', '**/features/**'], message: 'shared không import core, features' }),
    ...features.filter(() => features.length > 1).map((name) => layer([`${root}/features/${name}/**/*.ts`], other, {
      group: features.filter((f) => f !== name).flatMap(featureImport),
      message: `features/${name} không import tính năng khác; thứ dùng chung chuyển xuống core hoặc shared`,
    })),
  ];
};
// import tương đối chui qua public-api của thư viện khác
const siblingInternals = { group: ['**/projects/auth/**', '**/projects/http/**', '**/projects/model/**', '../../auth/**', '../../http/**', '../../model/**', '../../../auth/**', '../../../http/**', '../../../model/**'], message: 'Dùng @t3n/<thư viện>, không import đường dẫn tương đối sang thư viện khác' };

// hướng phụ thuộc: model <- auth <- http <- (storefront-web, seller-web) (tech.md T1)
const boundary = (files, ...banned) => ({
  files,
  rules: { 'no-restricted-imports': ['error', { patterns: [uiLibraries, appCode, siblingInternals, ...banned] }] },
});

module.exports = tseslint.config(
  { ignores: ['dist/**', 'node_modules/**', '.angular/**', 'out-tsc/**', 'test-results/**'] },
  {
    files: ['projects/**/*.ts', 'e2e/**/*.ts', 'e2e-real/**/*.ts', 'e2e-seller/**/*.ts'],
    extends: [...tseslint.configs.recommended],
    rules: {
      'no-restricted-syntax': ['error',
        { selector: `Literal[value=/${legacyVocabulary}/]`, message: legacyMessage },
        { selector: `Identifier[name=/${legacyVocabulary}/]`, message: legacyMessage },
      ],
    },
  },
  boundary(['projects/model/**/*.ts'], { group: ['@t3n/auth', '@t3n/http'], message: 'model không phụ thuộc thư viện nào của dự án' }),
  boundary(['projects/auth/**/*.ts'], { group: ['@t3n/http'], message: 'auth không import http (http import auth)' }),
  boundary(['projects/http/**/*.ts']),
  appBoundary('storefront-web', 'seller-web'),
  appBoundary('seller-web', 'storefront-web'),
  ...appLayers('storefront-web', 'seller-web'),
  ...appLayers('seller-web', 'storefront-web'),
);
