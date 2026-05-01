export default {
  app: {
    title: 'ReleaseHub',
  },
  lang: {
    zh: 'Chinese',
    en: 'English',
    switch: 'Language',
  },
  menu: {
    dashboard: 'Dashboard',
    calendar: 'Calendar',
    releaseWindows: 'Release Windows',
    branchRules: 'Branch Rules',
    versionPolicies: 'Version Policies',
    versionOps: 'Version Ops',
    login: 'Login',
    iterations: 'Iterations',
    repositories: 'Repositories',
    runs: 'Runs',
    blockBoard: 'Blocks',
    settings: 'Settings',
    groups: 'Groups'
  },
  common: {
    search: 'Search',
    reset: 'Reset',
    create: 'Create',
    edit: 'Edit',
    confirm: 'Confirm',
    save: 'Save',
    cancel: 'Cancel',
    back: 'Back',
    logout: 'Logout',
    refresh: 'Refresh',
    delete: 'Delete',
    remove: 'Remove',
    noData: 'No Data',
    todo: 'TODO',
    pleaseEnter: 'Please enter ',
    pleaseSelect: 'Please select ',
    startDate: 'Start Date',
    endDate: 'End Date',
    startTime: 'Start Time',
    endTime: 'End Time',
    to: 'To',
    actions: 'Actions',
    detail: 'Detail',
    export: 'Export',
    copy: 'Copy',
    yes: 'Yes',
    no: 'No',
    keyword: 'Keyword',
    requestFailed: 'Request failed',
    unknownError: 'Unknown error',
    networkError: 'Network error',
    businessError: 'Business error',
    basicInfo: 'Basic Info',
    selectDateTime: 'Select date and time',
    success: 'Success',
    warning: 'Warning',
    permissionDenied: 'Permission denied',
    partialSuccess: 'Partial success',
    sync: 'Sync',
    view: 'View',
    featureComingSoon: 'Feature coming soon',
    preview: 'Preview',
    execute: 'Execute',
    plan: 'Plan',
    clear: 'Clear',
    loginRequired: 'Please login first',
    close: 'Close',
    maxLength: 'Maximum {max} characters',
    all: 'All'
  },
  dashboard: {
    totalRepositories: 'Total Repositories',
    totalIterations: 'Total Iterations',
    activeWindows: 'Active Windows',
    recentRuns: 'Recent Runs',
    recentActivity: 'Recent Activity',
    noActivity: 'No recent activity',
    quickActions: 'Quick Actions',
    newRelease: 'New Release',
    refresh: 'Refresh',
  },
  releaseWindow: {
    name: 'ReleaseWindow Name',
    status: 'Status',
    create: 'Create ReleaseWindow',
    id: 'ID',
    createdAt: 'Created At',
    actions: 'Actions',
    view: 'View',
    freeze: 'Freeze',
    unfreeze: 'Unfreeze',
    active: 'Active',
    frozen: 'Frozen',
    windowKey: 'Window Key',
    startAt: 'Start At',
    endAt: 'End At',
    updatedAt: 'Updated At',
    publishedAt: 'Published At',
    publish: 'Publish',
    close: 'Close',
    configureTime: 'Configure Time',
    confirmFreeze: 'Confirm to freeze this release window?',
    confirmPublish: 'Confirm to publish this release window?',
    confirmClose: 'Confirm to close this release window?',
    placeholder: {
      name: 'Search by name',
      status: 'Select status',
      enterName: 'Enter name',
      enterDesc: 'Enter description',
      enterWindowKey: 'Enter window key',
      enterDescription: 'Enter description',
    },
    description: 'Description',
    plannedReleaseAt: 'Planned Release',
    attachIterations: 'Attach Iterations',
    associatedIterations: 'Associated Iterations',
    noIterations: 'No associated iterations',
    noRepos: 'No associated repositories',
    confirmDetach: 'Confirm to detach this iteration?',
    details: 'ReleaseWindow Details',
    editTitle: 'Edit ReleaseWindow',
    validation: {
      nameRequired: 'Please enter name',
      nameLength: 'Length should be 3 to 50',
      statusRequired: 'Please select status',
      required: 'This field is required'
    },
    statusText: {
      DRAFT: 'Draft',
      PUBLISHED: 'Published',
      CLOSED: 'Closed'
    },
    versionUpdate: {
      title: 'Execute Version Update',
      execute: 'Execute Version Update',
      repoId: 'Repository',
      buildTool: 'Build Tool',
      targetVersion: 'Target Version',
      repoPath: 'Repository Path',
      pomPath: 'POM File Path',
      gradlePropertiesPath: 'Gradle Properties Path',
      selectRepo: 'Please select a repository',
      targetVersionPlaceholder: 'e.g., 1.2.3',
      repoPathPlaceholder: 'Local repository path, e.g., /path/to/repo',
      pomPathPlaceholder: 'POM file path, leave empty for default pom.xml',
      gradlePropertiesPathPlaceholder: 'Gradle properties path, leave empty for default gradle.properties',
      repoIdRequired: 'Please select a repository',
      targetVersionRequired: 'Please enter target version',
      buildToolRequired: 'Please select build tool',
      repoPathRequired: 'Please enter repository path',
      targetVersionTip: 'Format examples: 1.2.3 (SemVer) or 2024.01.27 (Date version)',
      repoPathTip: 'Local file system path, e.g., /path/to/repository or C:\\path\\to\\repository',
      validationFailed: 'Please check if the form is filled correctly',
      executing: 'Executing version update, please wait...',
      success: 'Version update started, Run ID: {runId}',
      failed: 'Version update failed, please check error message'
    },
    codeMerge: {
      button: 'Code Merge',
      title: 'Code Merge',
      info: 'Merge latest code from feature branches to release branch',
      mergeAll: 'Merge All Iterations',
      mergeSingle: 'Merge Single Iteration',
      selectIteration: 'Select iteration',
      execute: 'Execute Merge',
      sourceBranch: 'Source Branch',
      targetBranch: 'Target Branch',
      status: {
        SUCCESS: 'Success',
        CONFLICT: 'Conflict',
        FAILED: 'Failed'
      },
      allSuccess: 'All repositories merged successfully',
      hasConflict: 'Some repositories have conflicts, please resolve in GitLab',
      hasFailed: 'Some repositories failed to merge, please check error messages'
    }
  },
  branchRule: {
    name: 'Rule Name',
    pattern: 'Branch Pattern',
    type: 'Type',
    create: 'Add Rule',
    edit: 'Edit Rule',
    typeAllow: 'Allow',
    typeBlock: 'Block',
    namePlaceholder: 'Enter rule name',
    patternPlaceholder: 'Enter branch pattern, e.g. feature/*',
    nameRequired: 'Rule name is required',
    patternRequired: 'Branch pattern is required',
    typeRequired: 'Please select rule type',
  },
  versionPolicy: {
    name: 'Policy Name',
    strategy: 'Strategy',
    scheme: 'Version Scheme',
    bumpRule: 'Bump Rule',
    create: 'Add Policy',
    builtInNote: 'Version policies are built-in. Supports SemVer (Semantic Versioning) and DATE (Date-based) schemes.',
  },
  versionOps: {
    scanConfig: 'Scan Configuration',
    runScan: 'Run Scan',
    targetBranch: 'Target Branch',
    selectBranch: 'Select Branch',
    scanType: 'Scan Type',
    dependencyCheck: 'Dependency Check',
    codeQuality: 'Code Quality',
    scanHistory: 'Scan History',
    runId: 'Run ID',
    date: 'Date',
    branch: 'Branch',
    status: 'Status',
    viewDetails: 'View Details',
  },
  login: {
    title: 'ReleaseHub Login',
    username: 'Username',
    password: 'Password',
    placeholder: {
      username: 'Enter username',
      password: 'Enter password',
    },
    rememberMe: 'Remember Me',
    signIn: 'Sign In',
    validation: {
      usernameRequired: 'Please input username',
      passwordRequired: 'Please input password',
    },
    message: {
      success: 'Login successfully',
      authFailed: 'Invalid username or password',
      failed: 'Login failed',
    }
  },
  iteration: {
    new: 'New Iteration',
    confirmDelete: 'Are you sure to delete iteration "{key}"? This action cannot be undone',
    columns: {
      key: 'Iteration Key',
      name: 'Iteration Name',
      description: 'Description',
      expectedReleaseAt: 'Expected Release',
      repos: 'Repos',
      mountedWindows: 'Mounted Windows',
      attachAt: 'Latest Mount Time',
      createdAt: 'Created At',
      updatedAt: 'Updated At'
    },
    detail: {
      title: 'Iteration Detail',
      associatedRepos: 'Associated Repositories',
      noRepos: 'No repositories associated',
      addRepos: 'Add Repos',
      confirmRemoveRepo: 'Are you sure to remove repository "{repoId}"?',
      mountedWindows: 'Mounted Windows',
      attachToWindow: 'Attach to Window',
      orchestrateThisIteration: 'Orchestrate (this iteration)',
      operations: 'Operations',
      searchRepos: 'Search repository name',
      alreadyAdded: 'Already Added',
      selectedCount: '{count} new repositories selected',
      noNewRepos: 'Please select at least one new repository'
    }
  },
  repository: {
    addOrSync: 'Add / Sync Repo',
    searchPlaceholder: 'Search by name/URL',
    sync: 'Sync',
    healthLabel: 'Health',
    columns: {
      repo: 'Repository',
      name: 'Repository Name',
      cloneUrl: 'Clone URL',
      defaultBranch: 'Default Branch',
      repoType: 'Repo Type',
      initialVersion: 'Initial Version',
      monoRepo: 'Mono Repo',
      writable: 'Writable?'
    },
    repoTypes: {
      SERVICE: 'Service',
      LIBRARY: 'Library'
    },
    openGitLab: 'Open in GitLab',
    gateSummary: 'Gate Summary',
    branchesMrSummary: 'Branches & MR Summary',
    placeholders: {
      name: 'e.g. backend-service',
      cloneUrl: 'git@gitlab.com:group/project.git',
      defaultBranch: 'main (default)',
      initialVersion: 'e.g. 1.2.3 (optional)'
    },
    gateSummaryLabels: {
      protectedBranch: 'Protected Branch',
      approvalRequired: 'Approval Required',
      pipelineGate: 'Pipeline Gate',
      permissionDenied: 'Permission Denied'
    },
    branchSummary: {
      totalBranches: 'Total Branches',
      activeBranches: 'Active Branches',
      nonCompliant: 'Non-Compliant',
      activeMrs: 'Active MRs',
      mergedMrs: 'Merged MRs',
      closedMrs: 'Closed MRs'
    },
    gitlabMissing: 'Configure GitLab settings first',
    gitlabUrlNotAvailable: 'GitLab URL not available',
    confirmDelete: 'Confirm to delete this repository?',
    health: {
      healthy: 'Healthy',
      risk: 'Risk'
    },
    validation: {
      name: 'Name max 128 chars',
      cloneUrl: 'Clone URL max 512 chars',
      defaultBranch: 'Default branch max 128 chars',
      initialVersion: 'Initial version max 50 chars'
    }
  },
  run: {
    filters: {
      windowKey: 'Window Key',
      repo: 'Repository',
      iterationKey: 'Iteration Key',
      status: 'Status'
    },
    columns: {
      runId: 'Run ID',
      type: 'Type',
      status: 'Status',
      start: 'Start',
      end: 'End',
      operator: 'Operator'
    },
    retryConfirm: 'Confirm to retry this run?',
    noFailedItems: 'No failed items',
    retrySuccess: 'Retry started',
    actions: {
      detail: 'Detail',
      retry: 'Retry',
      export: 'Export'
    },
    detail: {
      title: 'Run',
      triplesTitle: 'Triples (executedOrder)',
      exportJson: 'Export JSON'
    },
    steps: 'Execution Steps',
    diff: {
      title: 'Version Update Diff',
      noDiff: 'No diff information'
    }
  },
  audit: {
    retrySelected: 'Retry Selected',
    reasons: {
      conflict: 'CONFLICT',
      pipelineFailed: 'PIPELINE_FAILED',
      approvalRequired: 'APPROVAL_REQUIRED',
      protectedBranch: 'PROTECTED_BRANCH',
      permissionDenied: 'PERMISSION_DENIED',
      unknown: 'UNKNOWN'
    }
  },
  settings: {
    tabs: {
      gitlab: 'GitLab',
      naming: 'Naming Strategy',
      refs: 'Baseline Refs',
      blockPolicy: 'Block Policy',
      display: 'Display'
    },
    labels: {
      baseUrl: 'Base URL',
      token: 'Token',
      featureTemplate: 'Feature Branch Template',
      releaseTemplate: 'Release Branch Template'
    },
    buttons: {
      testConnection: 'Test Connection'
    },
    policy: {
      failFast: 'FAIL_FAST (default)',
      continueOnBlock: 'CONTINUE_ON_BLOCK (explicit)'
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
      ensureFeature: 'ensure feature/{iterationKey}',
      ensureRelease: 'ensure release/{windowKey}',
      ensureMR: 'reuse or create MR',
      tryMerge: 'try merge to release/{windowKey}'
    }
  }
  ,
  header: {
    title: 'ReleaseHub'
  },
  calendar: {
    title: 'Release Calendar',
    today: 'Today',
    more: 'more',
    month: 'Month',
    week: 'Week',
    weekOf: 'Week {n}'
  },
  group: {
    title: 'Groups',
    name: 'Name',
    code: 'Code',
    parentCode: 'Parent Code',
    codePlaceholder: 'Leave empty for auto-generation',
    codeAutoGenTip: 'Auto-generated if left empty (e.g., 001, 001001)',
    searchPlaceholder: 'Filter by name or code',
    selectGroup: 'Select a group',
    hasChildren: 'Has children',
    emptyFiltered: 'No matching groups',
    create: 'Create Group',
    createTop: 'Create Top-Level Group',
    createChild: 'Create Child Group',
    detail: 'Group Detail',
    notFound: 'Group Not Found',
    empty: 'No Groups',
    createSuccess: 'Created successfully',
    createFailed: 'Creation failed',
    edit: 'Edit Group',
    delete: 'Delete Group',
    confirmDelete: 'Confirm delete this group? This action cannot be undone.',
    updateSuccess: 'Updated successfully',
    updateFailed: 'Update failed',
    deleteSuccess: 'Deleted successfully',
    deleteBlocked: 'Cannot delete group with children',
    validation: {
      nameRequired: 'Please enter name',
      codeRequired: 'Please enter code',
      nameLength: 'Name must be at most 128 characters',
      codeLength: 'Code must be at most 64 characters',
      parentLength: 'Parent code must be at most 64 characters',
      codeImmutable: 'Code cannot be changed'
    }
  },
  conflict: {
    title: 'Conflicts',
    notScanned: 'Not scanned',
    notScannedHint: 'Click "Rescan" to run conflict detection',
    hasConflicts: '{count} conflict(s) detected, resolve all before execution',
    noConflicts: 'No conflicts, safe to proceed',
    rescan: 'Rescan',
    lastScanAt: 'Last scan',
    repoName: 'Repository',
    iteration: 'Iteration',
    type: 'Conflict Type',
    branches: 'Branches / Versions',
    message: 'Description',
    resolveBeforeExecute: 'Unresolved conflicts exist, please resolve all conflicts first',
    filterAll: 'All',
    action: 'Action',
    resolveVersion: 'Sync Version',
    resolveInGit: 'Resolve in Git',
    resolveBranch: 'Resolve Branch',
    types: {
      MISMATCH: 'Version Mismatch',
      REPO_AHEAD: 'Repo Ahead',
      SYSTEM_AHEAD: 'System Ahead',
      BRANCH_EXISTS: 'Branch Exists',
      BRANCH_NONCOMPLIANT: 'Noncompliant Name',
      CROSS_REPO_VERSION_MISMATCH: 'Cross-repo Mismatch',
      MERGE_CONFLICT: 'Merge Conflict'
    }
  },
  orchestration: {
    title: 'Release Orchestration',
    status: {
      preparing: 'Preparing',
      finishing: 'Finishing',
      completed: 'Completed',
      unknown: 'Unknown'
    },
    steps: {
      prepare: 'Prepare',
      merge: 'Code Merge',
      version: 'Version Update',
      finish: 'Finish'
    },
    iterationCount: 'Iterations',
    repoCount: 'Repositories',
    previewPlan: 'Preview Plan',
    mergeDirection: 'Merge Direction',
    mergeAll: 'Merge All',
    versionAction: 'Version Action',
    updatePomGradle: 'Update POM/Gradle Version',
    finishActions: 'Finish Actions',
    finishAction: {
      mergeToMaster: 'Merge to Master',
      createTag: 'Create Tag',
      archiveBranch: 'Archive Branch'
    },
    executeFinish: 'Execute Finish',
    recentRuns: 'Recent Runs',
    planPreview: 'Execution Plan Preview',
    confirmMergeAll: 'Confirm to merge all iterations to release branch?',
    confirmOrchestrate: 'Confirm to execute finish orchestration? Will merge to master and create tags.'
  }
}
