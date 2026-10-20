const tseslint = require('typescript-eslint');

const legacyVocabulary = '^(CUSTOMER|SELLER|GUEST|SHIPPER|webgw|oss_id|fullName)$';
const legacyMessage = 'Từ vựng của thiết kế cũ không quay lại (feature 05 flow.md R13); vai chỉ là USER hoặc ADMIN';

const uiLibraries = { group: ['@angular/material', '@angular/material/*', '@angular/cdk', '@angular/cdk/*'], message: 'Thư viện logic không import giao diện' };
const appCode = { group: ['**/storefront-web/**', '@t3n/storefront-web'], message: 'Thư viện không import mã ứng dụng' };
// import tương đối chui qua public-api của thư viện khác
const siblingInternals = { group: ['**/projects/auth/**', '**/projects/http/**', '**/projects/model/**', '../../auth/**', '../../http/**', '../../model/**', '../../../auth/**', '../../../http/**', '../../../model/**'], message: 'Dùng @t3n/<thư viện>, không import đường dẫn tương đối sang thư viện khác' };

// hướng phụ thuộc: model <- auth <- http <- storefront-web (tech.md T1)
const boundary = (files, ...banned) => ({
  files,
  rules: { 'no-restricted-imports': ['error', { patterns: [uiLibraries, appCode, siblingInternals, ...banned] }] },
});

module.exports = tseslint.config(
  { ignores: ['dist/**', 'node_modules/**', '.angular/**', 'out-tsc/**', 'test-results/**'] },
  {
    files: ['projects/**/*.ts', 'e2e/**/*.ts', 'e2e-real/**/*.ts'],
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
);
