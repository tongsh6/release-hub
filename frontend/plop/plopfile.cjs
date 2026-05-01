/* global module, require, process */

module.exports = function (plop) {
  // Helper registration
  plop.setHelper('upper', (txt) => txt.toUpperCase());
  // camel/pascal/kebab are usually built-in or provided by plop-pack-fancy-comments etc, but plop v4 might need explicit or relies on handlebars-helpers.
  // Actually plop uses `change-case` internally but sometimes helpers need explicit registration if we want to use them in templates like {{camel name}}
  // Let's check standard plop helpers. Usually `camelCase`, `pascalCase` etc are available.
  // I will register shortcuts for my templates: camel, pascal, kebab
  
  // eslint-disable-next-line @typescript-eslint/no-require-imports
  const { camelCase, pascalCase, kebabCase } = require('change-case');

  plop.setHelper('camel', (txt) => camelCase(txt));
  plop.setHelper('pascal', (txt) => pascalCase(txt));
  plop.setHelper('kebab', (txt) => kebabCase(txt));

  plop.setGenerator('crud', {
    description: 'Generate a CRUD module (Views, API, Router, Types)',
    prompts: [
      {
        type: 'input',
        name: 'moduleName',
        message: 'Module name (e.g. releaseWindow / branchRule):',
        validate: (value) => {
          if (/.+/.test(value)) { return true; }
          return 'moduleName is required';
        }
      },
      {
        type: 'input',
        name: 'entityName',
        message: 'Entity name (e.g. ReleaseWindow / BranchRule):',
        default: (answers) => pascalCase(answers.moduleName)
      },
      {
        type: 'confirm',
        name: 'withDetailPage',
        message: 'Generate Detail Page?',
        default: true
      },
      {
        type: 'confirm',
        name: 'withDialog',
        message: 'Generate Dialog Component?',
        default: true
      }
    ],
    actions: (data) => {
      const actions = [];
      
      // Default derived values
      data.apiName = camelCase(data.moduleName) + 'Api';

      const force = process.argv.includes('--force');

      // 1. Views
      actions.push({
        type: 'add',
        path: '../src/views/{{dashCase moduleName}}/{{pascal moduleName}}List.vue',
        templateFile: 'templates/crud/list.vue.hbs',
        force: force
      });

      if (data.withDetailPage) {
        actions.push({
          type: 'add',
          path: '../src/views/{{dashCase moduleName}}/{{pascal moduleName}}Detail.vue',
          templateFile: 'templates/crud/detail.vue.hbs',
          force: force
        });
      }

      if (data.withDialog) {
        actions.push({
          type: 'add',
          path: '../src/views/{{dashCase moduleName}}/{{pascal moduleName}}Dialog.vue',
          templateFile: 'templates/crud/dialog.vue.hbs',
          force: force
        });
      }

      // 2. Types
      actions.push({
        type: 'add',
        path: '../src/types/{{camel moduleName}}.ts',
        templateFile: 'templates/crud/types.ts.hbs',
        force: force
      });

      // 3. API
      actions.push({
        type: 'add',
        path: '../src/api/{{camel apiName}}.ts',
        templateFile: 'templates/crud/api.ts.hbs',
        force: force
      });

      // 4. Router
      actions.push({
        type: 'add',
        path: '../src/router/modules/{{camel moduleName}}.ts',
        templateFile: 'templates/crud/router.ts.hbs',
        force: force
      });

      return actions;
    }
  });
};
