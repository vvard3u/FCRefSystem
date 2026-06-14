import js from '@eslint/js';
import globals from 'globals';

export default [
  {
    ignores: ['node_modules/**']
  },
  js.configs.recommended,
  {
    files: ['**/*.js'],
    languageOptions: {
      ecmaVersion: 'latest',
      globals: globals.browser,
      sourceType: 'script'
    },
    rules: {
      'no-unused-vars': ['error', { caughtErrorsIgnorePattern: '^ignored$' }]
    }
  }
];
