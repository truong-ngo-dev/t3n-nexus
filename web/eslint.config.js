const nx = require('@nx/eslint-plugin');

module.exports = [
  ...nx.configs['flat/base'],
  ...nx.configs['flat/typescript'],
  ...nx.configs['flat/angular'],
  {
    files: ['**/*.ts'],
    rules: {
      '@nx/enforce-module-boundaries': [
        'error',
        {
          enforceBuildableLibDependencyCheck: true,
          allow: [],
          depConstraints: [
            // scope: shared không được import từ app scope nào
            { sourceTag: 'scope:shared',     onlyDependOnLibsWithTags: ['scope:shared'] },
            { sourceTag: 'scope:storefront', onlyDependOnLibsWithTags: ['scope:storefront', 'scope:shared'] },
            { sourceTag: 'scope:seller',     onlyDependOnLibsWithTags: ['scope:seller',     'scope:shared'] },
            { sourceTag: 'scope:admin',      onlyDependOnLibsWithTags: ['scope:admin',      'scope:shared'] },

            // type: feature có thể dùng ui, data-access, util, model — không import feature khác
            { sourceTag: 'type:feature',     onlyDependOnLibsWithTags: ['type:ui', 'type:data-access', 'type:util', 'type:model'] },
            { sourceTag: 'type:ui',          onlyDependOnLibsWithTags: ['type:ui', 'type:util', 'type:model'] },
            { sourceTag: 'type:data-access', onlyDependOnLibsWithTags: ['type:data-access', 'type:util', 'type:model'] },
            { sourceTag: 'type:util',        onlyDependOnLibsWithTags: ['type:util', 'type:model'] },
            { sourceTag: 'type:model',       onlyDependOnLibsWithTags: ['type:model'] }
          ]
        }
      ]
    }
  }
];
