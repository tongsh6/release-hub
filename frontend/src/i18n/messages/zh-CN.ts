export default {
  app: {
    title: 'ReleaseHub',
  },
  lang: {
    zh: '中文',
    en: 'English',
    switch: '语言',
  },
  menu: {
    dashboard: '仪表盘',
    calendar: '发布日历',
    releaseWindows: '发布窗口',
    branchRules: '分支规则',
    versionPolicies: '版本策略',
    versionOps: '版本操作',
    login: '登录',
    iterations: '迭代',
    repositories: '仓库',
    runs: '执行记录',
    blockBoard: '阻塞看板',
    settings: '配置',
    groups: '分组设置'
  },
  common: {
    search: '查询',
    reset: '重置',
    create: '新增',
    edit: '编辑',
    confirm: '确认',
    save: '保存',
    cancel: '取消',
    back: '返回',
    logout: '退出登录',
    refresh: '刷新',
    delete: '删除',
    remove: '移除',
    noData: '暂无数据',
    todo: '待完善',
    pleaseEnter: '请输入',
    pleaseSelect: '请选择',
    startDate: '开始日期',
    endDate: '结束日期',
    startTime: '开始时间',
    endTime: '结束时间',
    to: '至',
    actions: '操作',
    detail: '详情',
    export: '导出',
    copy: '复制',
    yes: '是',
    no: '否',
    keyword: '关键字',
    requestFailed: '请求失败',
    unknownError: '未知错误',
    networkError: '网络异常',
    businessError: '业务异常',
    basicInfo: '基本信息',
    selectDateTime: '请选择日期时间',
    success: '操作成功',
    warning: '警告',
    permissionDenied: '权限不足',
    partialSuccess: '部分成功',
    sync: '同步',
    view: '查看',
    featureComingSoon: '功能开发中',
    preview: '预览',
    execute: '执行',
    plan: '计划',
    clear: '清空',
    loginRequired: '请先登录',
    close: '关闭',
    maxLength: '最多输入 {max} 个字符',
    all: '全部'
  },
  dashboard: {
    totalRepositories: '仓库总数',
    totalIterations: '迭代总数',
    activeWindows: '活跃发布窗口',
    recentRuns: '近期执行',
    recentActivity: '近期活动',
    noActivity: '暂无活动',
    quickActions: '快捷操作',
    newRelease: '新建发布',
    refresh: '刷新',
  },
  releaseWindow: {
    name: '发布窗口名称',
    status: '状态',
    create: '创建发布窗口',
    id: 'ID',
    createdAt: '创建时间',
    actions: '操作',
    view: '查看',
    freeze: '冻结',
    unfreeze: '解冻',
    active: '活跃',
    frozen: '已冻结',
    windowKey: '发布窗口标识',
    startAt: '开始时间',
    endAt: '结束时间',
    updatedAt: '更新时间',
    publishedAt: '发布时间',
    publish: '发布',
    close: '关闭',
    configureTime: '配置时间',
    confirmFreeze: '确认冻结此发布窗口？',
    confirmPublish: '确认发布此发布窗口？',
    confirmClose: '确认关闭此发布窗口？',
    placeholder: {
      name: '按名称搜索',
      status: '选择状态',
      enterName: '请输入名称',
      enterDesc: '请输入描述',
      enterWindowKey: '请输入窗口标识',
      enterDescription: '请输入描述'
    },
    description: '描述',
    plannedReleaseAt: '计划发布时间',
    attachIterations: '关联迭代',
    associatedIterations: '关联的迭代',
    noIterations: '暂无关联迭代',
    noRepos: '暂无关联仓库',
    confirmDetach: '确认移除此迭代关联？',
    details: '发布窗口详情',
    editTitle: '编辑发布窗口',
    validation: {
      nameRequired: '请输入名称',
      nameLength: '长度应在 3 到 50 个字符之间',
      statusRequired: '请选择状态',
      required: '该字段为必填项'
    },
    statusText: {
      DRAFT: '草稿',
      PUBLISHED: '已发布',
      CLOSED: '已关闭'
    },
    versionUpdate: {
      title: '执行版本更新',
      execute: '执行版本更新',
      repoId: '仓库',
      buildTool: '构建工具',
      targetVersion: '目标版本号',
      repoPath: '仓库路径',
      pomPath: 'POM 文件路径',
      gradlePropertiesPath: 'Gradle Properties 路径',
      selectRepo: '请选择仓库',
      targetVersionPlaceholder: '例如: 1.2.3',
      repoPathPlaceholder: '本地仓库路径，例如: /path/to/repo',
      pomPathPlaceholder: 'POM 文件路径，留空则使用默认 pom.xml',
      gradlePropertiesPathPlaceholder: 'Gradle Properties 路径，留空则使用默认 gradle.properties',
      repoIdRequired: '请选择仓库',
      targetVersionRequired: '请输入目标版本号',
      buildToolRequired: '请选择构建工具',
      repoPathRequired: '请输入仓库路径',
      targetVersionTip: '格式示例：1.2.3（SemVer）或 2024.01.27（日期版本）',
      repoPathTip: '本地文件系统路径，例如：/path/to/repository 或 C:\\path\\to\\repository',
      validationFailed: '请检查表单填写是否正确',
      executing: '正在执行版本更新，请稍候...',
      success: '版本更新已启动，执行记录 ID: {runId}',
      failed: '版本更新失败，请查看错误信息'
    },
    codeMerge: {
      button: '代码合并',
      title: '代码合并',
      info: '将 feature 分支的最新代码合并到 release 分支',
      mergeAll: '合并所有迭代',
      mergeSingle: '合并单个迭代',
      selectIteration: '请选择迭代',
      execute: '执行合并',
      sourceBranch: '源分支',
      targetBranch: '目标分支',
      status: {
        SUCCESS: '成功',
        CONFLICT: '冲突',
        FAILED: '失败'
      },
      allSuccess: '所有仓库合并成功',
      hasConflict: '部分仓库存在冲突，请在 GitLab 中手动解决',
      hasFailed: '部分仓库合并失败，请检查错误信息'
    }
  },
  branchRule: {
    name: '规则名称',
    pattern: '分支模式',
    type: '类型',
    create: '新增规则',
    edit: '编辑规则',
    typeAllow: '允许',
    typeBlock: '阻止',
    namePlaceholder: '请输入规则名称',
    patternPlaceholder: '请输入分支模式，如 feature/*',
    nameRequired: '规则名称不能为空',
    patternRequired: '分支模式不能为空',
    typeRequired: '请选择规则类型',
  },
  versionPolicy: {
    name: '策略名称',
    strategy: '策略描述',
    scheme: '版本方案',
    bumpRule: '递增规则',
    create: '新增策略',
    builtInNote: '版本策略为系统内置，支持 SemVer（语义化版本）和 DATE（日期版本）两种方案'
  },
  versionOps: {
    scanConfig: '扫描配置',
    runScan: '执行扫描',
    targetBranch: '目标分支',
    selectBranch: '选择分支',
    scanType: '扫描类型',
    dependencyCheck: '依赖检查',
    codeQuality: '代码质量',
    scanHistory: '扫描历史',
    runId: '运行ID',
    date: '日期',
    branch: '分支',
    status: '状态',
    viewDetails: '查看详情',
  },
  login: {
    title: 'ReleaseHub 登录',
    username: '用户名',
    password: '密码',
    placeholder: {
      username: '请输入用户名',
      password: '请输入密码',
    },
    rememberMe: '记住我',
    signIn: '登录',
    validation: {
      usernameRequired: '请输入用户名',
      passwordRequired: '请输入密码',
    },
    message: {
      success: '登录成功',
      authFailed: '用户名或密码错误',
      failed: '登录失败',
    }
  },
  iteration: {
    new: '新建迭代',
    confirmDelete: '确认删除迭代 "{key}"？此操作不可恢复',
    columns: {
      key: '迭代标识',
      name: '迭代名称',
      description: '描述',
      expectedReleaseAt: '期望上线时间',
      repos: '仓库数',
      mountedWindows: '挂载窗口数',
      attachAt: '最近挂载时间',
      createdAt: '创建时间',
      updatedAt: '更新时间'
    },
    detail: {
      title: '迭代详情',
      associatedRepos: '关联仓库',
      noRepos: '暂无关联仓库',
      addRepos: '添加仓库',
      confirmRemoveRepo: '确认移除仓库 "{repoId}"？',
      mountedWindows: '挂载窗口',
      attachToWindow: '挂载到窗口',
      orchestrateThisIteration: '编排（此迭代）',
      operations: '操作',
      searchRepos: '搜索仓库名称',
      alreadyAdded: '已添加',
      selectedCount: '已选择 {count} 个新仓库',
      noNewRepos: '请至少选择一个新仓库'
    },
    version: {
      baseVersion: '基准版本',
      devVersion: '开发版本',
      targetVersion: '目标版本',
      featureBranch: 'Feature 分支',
      sync: '同步',
      syncSuccess: '版本同步成功',
      conflictDetected: '检测到版本冲突',
      resolveConflict: '解决冲突',
      useSystem: '使用系统版本',
      useRepo: '使用仓库版本',
      systemVersion: '系统版本',
      repoVersion: '仓库版本'
    }
  },
  repository: {
    addOrSync: '新增/同步仓库',
    searchPlaceholder: '按名称/URL 搜索',
    sync: '同步',
    healthLabel: '健康',
    columns: {
      repo: '仓库',
      name: '仓库名称',
      cloneUrl: '仓库地址',
      defaultBranch: '默认分支',
      repoType: '仓库类型',
      initialVersion: '初始版本号',
      monoRepo: '单仓',
      writable: '可写？'
    },
    repoTypes: {
      SERVICE: '服务',
      LIBRARY: '类库'
    },
    openGitLab: '打开 GitLab',
    gateSummary: '门禁摘要',
    branchesMrSummary: '分支与 MR 摘要',
    placeholders: {
      name: '例如 backend-service',
      cloneUrl: 'git@gitlab.com:group/project.git',
      defaultBranch: 'main（默认）',
      initialVersion: '例如 1.2.3（可选）'
    },
    gateSummaryLabels: {
      protectedBranch: '保护分支',
      approvalRequired: '需要审批',
      pipelineGate: '流水线门禁',
      permissionDenied: '权限不足'
    },
    branchSummary: {
      totalBranches: '分支总数',
      activeBranches: '活跃分支',
      nonCompliant: '不合规',
      activeMrs: '活跃 MR',
      mergedMrs: '已合并 MR',
      closedMrs: '已关闭 MR'
    },
    gitlabMissing: '请先在系统设置中配置 GitLab',
    gitlabUrlNotAvailable: '无法获取 GitLab 地址',
    confirmDelete: '确认删除此仓库？',
    health: {
      healthy: '健康',
      risk: '风险'
    },
    validation: {
      name: '名称最长 128 个字符',
      cloneUrl: '仓库地址最长 512 个字符',
      defaultBranch: '默认分支最长 128 个字符',
      initialVersion: '初始版本号最长 50 个字符'
    }
  },
  run: {
    filters: {
      windowKey: '发布窗口标识',
      repo: '仓库',
      iterationKey: '迭代标识',
      status: '状态'
    },
    columns: {
      runId: '运行ID',
      type: '类型',
      status: '状态',
      start: '开始时间',
      end: '结束时间',
      operator: '操作人'
    },
    retryConfirm: '确认重试此运行？',
    noFailedItems: '没有失败的执行项',
    retrySuccess: '重试已启动',
    actions: {
      detail: '详情',
      retry: '重试',
      export: '导出'
    },
    detail: {
      title: '运行',
      triplesTitle: '三元组（executedOrder）',
      exportJson: '导出 JSON',
      tasksTitle: '运行任务'
    },
    steps: '执行步骤',
    task: {
      order: '序号',
      type: '任务类型',
      targetType: '目标类型',
      targetId: '目标 ID',
      retries: '重试次数',
      error: '错误信息',
      retry: '重试',
      status: {
        PENDING: '待执行',
        RUNNING: '执行中',
        COMPLETED: '已完成',
        FAILED: '失败',
        SKIPPED: '已跳过'
      },
      types: {
        CLOSE_ITERATION: '关闭迭代',
        ARCHIVE_FEATURE_BRANCH: '归档 Feature 分支',
        MERGE_RELEASE_TO_MASTER: '合并到 Master',
        CREATE_TAG: '创建标签',
        UPDATE_POM_VERSION: '更新 POM 版本',
        TRIGGER_CI_BUILD: '触发 CI 构建',
        CREATE_RELEASE_BRANCH: '创建 Release 分支',
        MERGE_FEATURE_TO_RELEASE: '合并到 Release'
      }
    },
    diff: {
      title: '版本更新差异',
      noDiff: '无差异信息'
    }
  },
  audit: {
    retrySelected: '批量重试',
    reasons: {
      conflict: '冲突',
      pipelineFailed: '流水线失败',
      approvalRequired: '需要审批',
      protectedBranch: '受保护分支',
      permissionDenied: '权限不足',
      unknown: '未知'
    }
  },
  settings: {
    tabs: {
      gitlab: 'GitLab',
      naming: '命名策略',
      refs: '基线 Ref',
      blockPolicy: '阻塞策略',
      display: '显示偏好'
    },
    labels: {
      baseUrl: '基础 URL',
      token: '令牌',
      featureTemplate: 'Feature 分支模板',
      releaseTemplate: 'Release 分支模板'
    },
    buttons: {
      testConnection: '测试连接'
    },
    policy: {
      failFast: 'FAIL_FAST（默认）',
      continueOnBlock: 'CONTINUE_ON_BLOCK（显式选择）'
    }
  },
  evidence: {
    projectId: 'projectId',
    mrIid: 'MR iid',
    source: 'source',
    target: 'target',
    mrUrl: 'MR url',
    blockReason: 'blockReason'
  },
  timeline: {
    steps: {
      ensureFeature: 'ENSURE_FEATURE',
      ensureRelease: 'ENSURE_RELEASE',
      ensureMR: 'ENSURE_MR',
      tryMerge: 'TRY_MERGE'
    },
    desc: {
      ensureFeature: '确保 feature/{iterationKey}',
      ensureRelease: '确保 release/{windowKey}',
      ensureMR: '复用或创建 MR',
      tryMerge: '尝试合并到 release/{windowKey}'
    }
  }
  ,
  header: {
    title: 'ReleaseHub'
  },
  calendar: {
    title: '发布日历',
    today: '今天',
    more: '更多',
    month: '月',
    week: '周',
    weekOf: '第{n}周'
  },
  group: {
    title: '分组设置',
    name: '名称',
    code: '编码',
    parentCode: '父级编码',
    codePlaceholder: '留空自动生成',
    codeAutoGenTip: '留空时系统自动生成编码（如 001、001001）',
    searchPlaceholder: '按名称或编码筛选',
    selectGroup: '请选择分组',
    hasChildren: '有子分组',
    emptyFiltered: '没有匹配的分组',
    create: '新增分组',
    createTop: '新增顶层分组',
    createChild: '新增下级分组',
    detail: '分组详情',
    notFound: '未找到分组',
    empty: '暂无分组',
    createSuccess: '创建成功',
    createFailed: '创建失败',
    edit: '编辑分组',
    delete: '删除分组',
    confirmDelete: '确认删除该分组？此操作不可恢复',
    updateSuccess: '更新成功',
    updateFailed: '更新失败',
    deleteSuccess: '删除成功',
    deleteBlocked: '存在子分组，无法删除',
    validation: {
      nameRequired: '请输入名称',
      codeRequired: '请输入编码',
      nameLength: '名称最长 128 个字符',
      codeLength: '编码最长 64 个字符',
      parentLength: '父级编码最长 64 个字符',
      codeImmutable: '编码不可修改'
    }
  },
  conflict: {
    title: '冲突检测',
    notScanned: '尚未扫描',
    notScannedHint: '请点击「重新扫描」执行冲突检测',
    hasConflicts: '检测到 {count} 个冲突，请先解决所有冲突后再执行操作',
    noConflicts: '无冲突，可以安全执行',
    rescan: '重新扫描',
    lastScanAt: '上次扫描时间',
    repoName: '仓库',
    iteration: '迭代',
    type: '冲突类型',
    branches: '分支 / 版本',
    message: '描述',
    resolveBeforeExecute: '存在未解决的冲突，请先解决所有冲突',
    filterAll: '全部',
    action: '操作',
    resolveVersion: '同步版本',
    resolveInGit: '请到 Git 平台解决',
    resolveBranch: '请处理此分支',
    types: {
      MISMATCH: '版本不一致',
      REPO_AHEAD: '仓库版本较新',
      SYSTEM_AHEAD: '系统版本较新',
      BRANCH_EXISTS: '分支已存在',
      BRANCH_NONCOMPLIANT: '分支名不合规',
      CROSS_REPO_VERSION_MISMATCH: '跨仓库版本不一致',
      MERGE_CONFLICT: '合并冲突'
    }
  },
  orchestration: {
    title: '发布编排',
    status: {
      preparing: '准备中',
      finishing: '收尾中',
      completed: '已完成',
      unknown: '未知'
    },
    steps: {
      prepare: '准备',
      merge: '代码合并',
      version: '版本更新',
      finish: '收尾'
    },
    iterationCount: '迭代数',
    repoCount: '仓库数',
    previewPlan: '预览计划',
    mergeDirection: '合并方向',
    mergeAll: '合并所有',
    versionAction: '版本操作',
    updatePomGradle: '更新 POM/Gradle 版本',
    finishActions: '收尾操作',
    finishAction: {
      mergeToMaster: '合并到 Master',
      createTag: '创建标签',
      archiveBranch: '归档分支'
    },
    executeFinish: '执行收尾',
    recentRuns: '最近执行记录',
    planPreview: '执行计划预览',
    confirmMergeAll: '确认合并所有迭代的代码到 release 分支？',
    confirmOrchestrate: '确认执行收尾编排？将合并到 master 并创建标签'
  }
}
