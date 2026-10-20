module.exports = {
  "preset": "jest-preset-angular",
  "setupFilesAfterEnv": [
    "<rootDir>/setup-jest.ts"
  ],
  "testEnvironment": "jsdom",
  "moduleNameMapper": {
    "^@t3n/model$": "<rootDir>/projects/model/src/public-api.ts",
    "^@t3n/auth$": "<rootDir>/projects/auth/src/public-api.ts",
    "^@t3n/http$": "<rootDir>/projects/http/src/public-api.ts"
  },
  "roots": ["<rootDir>/projects"],
  "testMatch": [
    "**/*.spec.ts"
  ],
  "modulePathIgnorePatterns": [
    "<rootDir>/dist/"
  ],
  "transform": {
    "^.+.(ts|js|mjs|html|svg)$": [
      "jest-preset-angular",
      {
        "tsconfig": "<rootDir>/tsconfig.spec.json",
        "stringifyContentPathRegex": ".(html|svg)$"
      }
    ]
  }
};
