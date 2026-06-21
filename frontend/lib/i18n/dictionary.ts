export const LANGUAGES = ["en", "ru", "uz"] as const

export type Lang = (typeof LANGUAGES)[number]

export const DEFAULT_LANG: Lang = "en"

export const LANGUAGE_LABELS: Record<Lang, string> = {
  en: "English",
  ru: "Русский",
  uz: "O‘zbekcha",
}

export const LANGUAGE_SHORT_LABELS: Record<Lang, string> = {
  en: "EN",
  ru: "RU",
  uz: "UZ",
}

export type Dictionary = {
  brand: {
    name: string
    tagline: string
    bullets: string[]
    footer: string
  }
  common: {
    back: string
    loading: string
    errorTitle: string
    retry: string
    language: string
    theme: string
    themeLight: string
    themeDark: string
    themeSystem: string
  }
  login: {
    title: string
    subtitle: string
    continueWithGoogle: string
    moreOptions: string
    noAccount: string
    createAccount: string
  }
  signup: {
    title: string
    subtitle: string
    continueWithGoogle: string
    haveAccount: string
    signIn: string
    terms: string
    termsLink: string
    privacyLink: string
  }
  errors: {
    generic: string
    network: string
    signInFailed: string
    sessionExpired: string
  }
  settings: {
    title: string
    subtitle: string
    back: string
    hub: {
      subtitle: string
      accountDescription: string
      telegramDescription: string
      subscriptionDescription: string
      dataPrivacyDescription: string
    }
    account: {
      title: string
      subtitle: string
      name: string
      email: string
      accountId: string
      memberSince: string
      unknownName: string
      statusActive: string
      statusDisabled: string
    }
    telegram: {
      title: string
      subtitle: string
      telegramLabel: string
      telegramDescription: string
      statusConnected: string
      statusNotConnected: string
      reminderTime: string
      streak: string
      days: string
      totalReminders: string
      manage: string
    }
    subscription: {
      title: string
      subtitle: string
      planLabel: string
      tierFree: string
      tierPremium: string
      freeDescription: string
      premiumDescription: string
      remainingQuizzes: string
      remainingUploads: string
      unlimited: string
      monthly: string
      resetsMonthly: string
      renewsOn: string
      upgrade: string
      managePlan: string
    }
    danger: {
      title: string
      subtitle: string
      resetLabel: string
      resetDescription: string
      resetButton: string
      resetConfirm: string
      resetSuccess: string
      signOutLabel: string
      signOutDescription: string
      signOutButton: string
      warning: string
    }
  }
  topics: {
    title: string
    subtitle: string
    sectionTopics: string
    sectionItems: string
    itemsSubtitle: string
    empty: string
    createCta: string
    createTitle: string
    createSubtitle: string
    createPlaceholder: string
    createSubmit: string
    createCancel: string
    failedToLoad: string
    backToTopics: string
    rename: string
    delete: string
    confirmDelete: string
    loadMore: string
    errors: {
      nameBlank: string
      nameTaken: string
      notFound: string
      generic: string
    }
  }
  materials: {
    title: string
    subtitle: string
    empty: string
    uploadTitle: string
    uploadSubtitle: string
    uploadDropZone: string
    uploadBrowse: string
    uploadSubmit: string
    pasteTitle: string
    pasteSubtitle: string
    pasteTitlePlaceholder: string
    pasteContentPlaceholder: string
    pasteSubmit: string
    listTitle: string
    delete: string
    replace: string
    confirmDelete: string
    failedToLoad: string
    move: {
      action: string
      title: string
      subtitle: string
      root: string
      current: string
      empty: string
      success: string
    }
    status: {
      pending: string
      processing: string
      ready: string
      failed: string
    }
    errors: {
      notFound: string
      topicNotFound: string
      contentRequired: string
      titleBlank: string
      unsupportedType: string
      tooLarge: string
      storageFailed: string
      generic: string
    }
  }
  nav: {
    home: string
    topics: string
    companion: string
    chat: string
    quiz: string
    gaps: string
    plan: string
    stats: string
    profile: string
    telegram: string
    premium: string
    settings: string
    workspace: string
    signOut: string
    upgrade: string
    demoMode: string
    resetDemo: string
    userMenu: string
  }
  chat: {
    title: string
    placeholder: string
    send: string
    empty: string
    emptyTitle: string
    emptySubtitle: string
    starterPrompts: string[]
    ungrounded: string
    citations: string
    citationsHint: string
    chunkLabel: string
    typing: string
    newConversation: string
  }
  companion: {
    title: string
    subtitle: string
    newSession: string
    sessionsTitle: string
    noSessions: string
    untitled: string
    inputPlaceholder: string
    send: string
    stop: string
    thinking: string
    emptyTitle: string
    emptyDescription: string
    ungrounded: string
    citations: string
    usedTool: string
    quizSubmit: string
    quizCorrect: string
    quizIncorrect: string
    quizCorrectAnswer: string
    quizExplanation: string
    quizAnswerPlaceholder: string
    quizTitle: string
    quizStart: string
    quizStartHint: string
    quizQuestionsLabel: string
    quizQuestionOf: string
    quizNext: string
    quizFinish: string
    quizTimeLeft: string
    quizResultsTitle: string
    quizScore: string
    quizYourAnswer: string
    quizSkipped: string
    quizGrading: string
    quizComposerBlocked: string
    quizModeTimed: string
    quizModeExplain: string
    quizModeSingle: string
    askAboutTopic: string
    topicSeedPrompt: string
    failedToLoad: string
    attachFile: string
    attachmentProcessing: string
    attachmentReady: string
    attachmentFailed: string
    attachmentRemove: string
    attachmentGate: string
    disclaimer: string
    copy: string
    copied: string
  }
  quiz: {
    title: string
    subtitle: string
    startCta: string
    needMaterials: string
    difficulty: string
    difficultyEasy: string
    difficultyMedium: string
    difficultyHard: string
    questionCount: string
    submit: string
    skip: string
    next: string
    finish: string
    yourAnswer: string
    correct: string
    incorrect: string
    score: string
    completedTitle: string
    completedSubtitle: string
    historyTitle: string
    historyEmpty: string
    questionOf: string
    citationLabel: string
    quotaReached: string
    open: string
  }
  gaps: {
    title: string
    subtitle: string
    regenerate: string
    generatedAt: string
    summary: string
    strongTitle: string
    weakTitle: string
    recommendationsTitle: string
    openMaterial: string
    quizMeCta: string
    empty: string
  }
  plan: {
    title: string
    subtitle: string
    goal: string
    goalPlaceholder: string
    target: string
    regenerate: string
    generatedAt: string
    progress: string
    upcoming: string
    today: string
    todayBadge: string
    minutes: string
    actionRead: string
    actionQuiz: string
    actionReview: string
    markDone: string
    completed: string
    replanTitle: string
    replanDescription: string
    refresh: string
    dayLabel: string
    openCta: string
    editGoal: string
    setGoal: string
    startLesson: string
    openLesson: string
    regenerateLesson: string
    lessonHeading: string
    lessonSources: string
    lessonGenerating: string
    startQuiz: string
    hideLesson: string
    empty: string
  }
  stats: {
    title: string
    subtitle: string
    sessionsCompleted: string
    topicsCount: string
    materialsCount: string
    streakDays: string
    weeklyMinutes: string
    knowledgeScore: string
    currentLevel: string
    historyTitle: string
    perTopicTitle: string
  }
  profile: {
    title: string
    subtitle: string
    identityTitle: string
    statsTitle: string
    trendTitle: string
    perTopicTitle: string
    goalTitle: string
    goalSubtitle: string
    goalLabel: string
    goalPlaceholder: string
    targetLabel: string
    saveGoal: string
    goalSaved: string
    memberSince: string
  }
  telegram: {
    title: string
    subtitle: string
    statusLinked: string
    statusPending: string
    statusNotLinked: string
    capabilitiesTitle: string
    capabilityReminderTitle: string
    capabilityReminderDescription: string
    capabilityQuizTitle: string
    capabilityQuizDescription: string
    capabilityStreakTitle: string
    capabilityStreakDescription: string
    connectTitle: string
    connectSubtitle: string
    generateCode: string
    regenerateCode: string
    pendingTitle: string
    pendingSubtitle: string
    codeLabel: string
    openInTelegram: string
    pendingHint: string
    waitingForLink: string
    codeExpires: string
    linkedTitle: string
    linkedAs: string
    linkedOn: string
    unlink: string
    unlinkConfirmTitle: string
    unlinkConfirmDescription: string
    unlinkConfirmAction: string
    unlinkConfirmDismiss: string
    linkedToast: string
    unlinkedToast: string
    codeCopied: string
    reminderNote: string
  }
  premium: {
    title: string
    subtitle: string
    tierFree: string
    tierPremium: string
    statusActive: string
    statusPending: string
    statusCanceled: string
    statusExpired: string
    currentTitle: string
    renewsOn: string
    endsOn: string
    willNotRenew: string
    cancel: string
    cancelConfirmTitle: string
    cancelConfirmDescription: string
    cancelConfirmAction: string
    cancelConfirmDismiss: string
    canceledToast: string
    choosePlanTitle: string
    choosePlanSubtitle: string
    providerLabel: string
    providerTest: string
    planMonthly: string
    planYearly: string
    billedMonthly: string
    billedYearly: string
    subscribe: string
    redirecting: string
    checkoutSuccess: string
    checkoutCanceled: string
    freeFeatures: string[]
    premiumFeatures: string[]
    paymentsTitle: string
    paymentsEmpty: string
    subscriptionsTitle: string
    subscriptionsEmpty: string
    colPlan: string
    colStatus: string
    colProvider: string
    colPeriod: string
    colAmount: string
    colDate: string
    paySucceeded: string
    payPending: string
    payFailed: string
    payRefunded: string
    quotaQuizzes: string
    quotaUploads: string
  }
  topicCard: {
    materials: string
    chunks: string
    updatedAt: string
    openChat: string
  }
  home: {
    greetingMorning: string
    greetingAfternoon: string
    greetingEvening: string
    streak: {
      title: string
      subtitle: string
      days: string
      day: string
      weeklyMinutes: string
      keepGoing: string
      startToday: string
      toNextMilestone: string
      todayLabel: string
      weekdays: {
        mon: string
        tue: string
        wed: string
        thu: string
        fri: string
        sat: string
        sun: string
      }
    }
    path: {
      title: string
      subtitle: string
      aggregate: string
      empty: string
      addGoal: string
      viewPlan: string
      percentLabel: string
      activeLabel: string
      completedLabel: string
      daysLabel: string
    }
    goals: {
      title: string
      subtitle: string
      emptyTitle: string
      emptyDescription: string
    }
    goal: {
      targetDate: string
      noTargetDate: string
      progress: string
      daysProgress: string
      nextStep: string
      statusActive: string
      statusPaused: string
      statusCompleted: string
    }
    today: {
      title: string
      subtitle: string
      emptyTitle: string
      emptyDescription: string
      createPlan: string
      viewPlan: string
      doneLabel: string
      minutesLeft: string
      minutesTotal: string
      allDone: string
      restDay: string
      pathLine: string
      filterAll: string
      goalLabel: string
    }
    addGoalDialog: {
      trigger: string
      title: string
      description: string
      goalLabel: string
      goalPlaceholder: string
      deadlineLabel: string
      deadlineHint: string
      cancel: string
      submit: string
      seedWithDeadline: string
      seedWithoutDeadline: string
    }
    onboarding: {
      title: string
      subtitle: string
      step1Title: string
      step1Description: string
      step1Cta: string
      step2Title: string
      step2Description: string
      step3Title: string
      step3Description: string
    }
  }
  onboarding: {
    skip: string
    next: string
    back: string
    saving: string
    stepOf: string
    welcome: {
      title: string
      subtitle: string
      languageLabel: string
      cta: string
    }
    goal: {
      title: string
      subtitle: string
      goalLabel: string
      goalPlaceholder: string
      targetLabel: string
      targetHint: string
      dailyLabel: string
      dailyHint: string
      minutesSuffix: string
      customOption: string
      customPlaceholder: string
    }
    upload: {
      title: string
      subtitle: string
      fileTab: string
      pasteTab: string
      dropZone: string
      browse: string
      formatsHint: string
      privacyNote: string
      pasteTitlePlaceholder: string
      pasteContentPlaceholder: string
      pasteSubmit: string
      uploading: string
      processing: string
      ready: string
      failed: string
      retry: string
      waiting: string
      continue: string
      showAll: string
      showLess: string
      delete: string
    }
    finish: {
      title: string
      subtitle: string
      askPrompt: string
      starters: string[]
      startChat: string
      explore: string
    }
  }
}

export const DICTIONARIES: Record<Lang, Dictionary> = {
  en: {
    brand: {
      name: "Ilm AI",
      tagline: "Your personal AI learning companion.",
      bullets: [
        "Upload your own materials — PDFs, notes, books.",
        "Chat, quiz, and find your knowledge gaps.",
        "Get a plan that fits your goal and your time.",
      ],
      footer: "Learn in Uzbek, Russian, or English.",
    },
    common: {
      back: "Back",
      loading: "Loading…",
      errorTitle: "Something went wrong",
      retry: "Try again",
      language: "Language",
      theme: "Theme",
      themeLight: "Light",
      themeDark: "Dark",
      themeSystem: "System",
    },
    login: {
      title: "Welcome back",
      subtitle: "Sign in to keep learning where you left off.",
      continueWithGoogle: "Continue with Google",
      moreOptions: "Telegram sign-in is on the way.",
      noAccount: "New to Ilm AI?",
      createAccount: "Create an account",
    },
    signup: {
      title: "Create your account",
      subtitle:
        "Bring your own materials and let Ilm AI become your tutor for them.",
      continueWithGoogle: "Continue with Google",
      haveAccount: "Already have an account?",
      signIn: "Sign in",
      terms: "By continuing you agree to our",
      termsLink: "Terms",
      privacyLink: "Privacy Policy",
    },
    errors: {
      generic: "Something went wrong. Please try again.",
      network: "Network error. Check your connection and try again.",
      signInFailed: "We couldn’t sign you in. Please try again.",
      sessionExpired: "Your session has expired. Please sign in again.",
    },
    settings: {
      title: "Settings",
      subtitle:
        "Personalise Ilm AI, manage Telegram connection, your plan, and your data.",
      back: "Back to settings",
      hub: {
        subtitle: "Manage your account, Telegram connection, plan, and data.",
        accountDescription: "Your profile, email, and member status.",
        telegramDescription: "Link your Telegram bot for daily quiz reminders.",
        subscriptionDescription: "Your plan, quotas, and renewal.",
        dataPrivacyDescription: "Reset demo data or sign out of this device.",
      },
      account: {
        title: "Account",
        subtitle: "Details from the account you signed in with.",
        name: "Name",
        email: "Email",
        accountId: "Account ID",
        memberSince: "Member since",
        unknownName: "Learner",
        statusActive: "Active",
        statusDisabled: "Disabled",
      },
      telegram: {
        title: "Telegram",
        subtitle: "Daily quiz reminders and study nudges.",
        telegramLabel: "Telegram reminders",
        telegramDescription: "Get a nudge every day with what to study next.",
        statusConnected: "Connected",
        statusNotConnected: "Not connected",
        reminderTime: "Reminder",
        streak: "Streak",
        days: "days",
        totalReminders: "Reminders sent",
        manage: "Manage",
      },
      subscription: {
        title: "Subscription",
        subtitle: "Your plan, quotas, and renewal.",
        planLabel: "Plan",
        tierFree: "Free",
        tierPremium: "Premium",
        freeDescription: "Free quotas reset every month.",
        premiumDescription: "Unlimited quizzes, uploads, and priority models.",
        remainingQuizzes: "Quizzes left",
        remainingUploads: "Uploads left",
        unlimited: "Unlimited",
        monthly: "Monthly",
        resetsMonthly: "Resets",
        renewsOn: "Renews on",
        upgrade: "Upgrade to Premium",
        managePlan: "Manage plan",
      },
      danger: {
        title: "Data & privacy",
        subtitle: "Reset your demo data or sign out of this device.",
        resetLabel: "Reset demo data",
        resetDescription:
          "Restore the seeded topics, materials, and stats. Irreversible.",
        resetButton: "Reset",
        resetConfirm:
          "This will replace everything with the original demo data. Continue?",
        resetSuccess: "Demo data restored.",
        signOutLabel: "Sign out",
        signOutDescription: "End this session on this device.",
        signOutButton: "Sign out",
        warning:
          "These actions are not destructive in real mode — they only sign you out.",
      },
    },
    topics: {
      title: "Your data",
      subtitle: "All your study materials in one place.",
      sectionTopics: "Topics",
      sectionItems: "Items",
      itemsSubtitle: "Files and notes in this space that aren’t in a topic.",
      empty: "No topics yet. Create one to start uploading materials.",
      createCta: "New topic",
      createTitle: "Create a topic",
      createSubtitle: "Give your topic a short, descriptive name.",
      createPlaceholder: "e.g. Cloud Architecture",
      createSubmit: "Create collection",
      createCancel: "Cancel",
      failedToLoad: "We couldn’t load your topics. Please try again.",
      backToTopics: "Back to topics",
      rename: "Rename",
      delete: "Delete",
      confirmDelete: "Delete this topic and all its materials?",
      loadMore: "Load more",
      errors: {
        nameBlank: "Topic name must not be blank.",
        nameTaken: "You already have a topic with this name.",
        notFound: "Topic not found.",
        generic: "Something went wrong. Please try again.",
      },
    },
    materials: {
      title: "Materials",
      subtitle: "Upload files or paste text into this topic.",
      empty: "No materials yet. Upload a file or paste text to start.",
      uploadTitle: "Upload a file",
      uploadSubtitle: "PDF, Word, or plain text — up to 20 MB.",
      uploadDropZone: "Drop a file here or",
      uploadBrowse: "browse",
      uploadSubmit: "Upload",
      pasteTitle: "Paste text",
      pasteSubtitle: "Paste an article, notes, or any text.",
      pasteTitlePlaceholder: "Title (required)",
      pasteContentPlaceholder: "Paste content here",
      pasteSubmit: "Add",
      listTitle: "Your materials",
      delete: "Delete",
      replace: "Replace",
      confirmDelete: "Delete this material?",
      failedToLoad: "We couldn’t load the materials. Please try again.",
      move: {
        action: "Move",
        title: "Move item",
        subtitle: "Choose where to keep this item.",
        root: "No folder (root)",
        current: "Current",
        empty: "Create a folder first to move items into it.",
        success: "Item moved.",
      },
      status: {
        pending: "Pending",
        processing: "Processing",
        ready: "Ready",
        failed: "Failed",
      },
      errors: {
        notFound: "Material not found.",
        topicNotFound: "Topic not found.",
        contentRequired: "Provide either a file or pasted text.",
        titleBlank: "Title is required.",
        unsupportedType: "This file type is not supported yet.",
        tooLarge: "File is too large.",
        storageFailed: "Storage is currently unavailable.",
        generic: "Something went wrong. Please try again.",
      },
    },
    nav: {
      home: "Home",
      topics: "Data",
      chat: "Chat",
      quiz: "Quiz",
      gaps: "Knowledge gaps",
      plan: "Learning plan",
      profile: "Profile",
      stats: "Stats",
      telegram: "Telegram",
      premium: "Premium",
      settings: "Settings",
      workspace: "Workspace",
      signOut: "Sign out",
      upgrade: "Upgrade",
      demoMode: "Demo mode",
      resetDemo: "Reset demo data",
      userMenu: "Account menu",
      companion: "Companion",
    },
    companion: {
      title: "Companion",
      subtitle: "Your AI tutor across everything you’ve uploaded.",
      newSession: "New chat",
      sessionsTitle: "Chats",
      noSessions: "No chats yet. Start one below.",
      untitled: "New chat",
      inputPlaceholder: "Ask anything about your materials…",
      send: "Send",
      stop: "Stop",
      thinking: "Thinking…",
      emptyTitle: "Ask your companion",
      emptyDescription:
        "It answers from your uploaded materials and cites its sources.",
      ungrounded: "Not grounded in your materials",
      citations: "Sources",
      usedTool: "Used {tool}",
      quizSubmit: "Submit",
      quizCorrect: "Correct",
      quizIncorrect: "Not quite",
      quizCorrectAnswer: "Answer",
      quizExplanation: "Why",
      quizAnswerPlaceholder: "Type your answer…",
      quizTitle: "Quiz",
      quizStart: "Start quiz",
      quizStartHint: "Solve and submit the quiz to keep chatting.",
      quizQuestionsLabel: "{count} questions",
      quizQuestionOf: "Question {current} of {total}",
      quizNext: "Next question",
      quizFinish: "Submit quiz",
      quizTimeLeft: "Time left",
      quizResultsTitle: "Quiz results",
      quizScore: "Score",
      quizYourAnswer: "Your answer",
      quizSkipped: "Skipped",
      quizGrading: "Grading…",
      quizComposerBlocked: "Finish the quiz above before sending a message.",
      quizModeTimed: "Timed",
      quizModeExplain: "Step by step",
      quizModeSingle: "All questions",
      askAboutTopic: "Ask about this topic",
      topicSeedPrompt: "Help me study {topic}.",
      failedToLoad: "We couldn’t load your chats. Please try again.",
      attachFile: "Attach a file",
      attachmentProcessing: "Processing {name}…",
      attachmentReady: "{name} is ready",
      attachmentFailed: "We couldn’t process {name}",
      attachmentRemove: "Remove file",
      attachmentGate: "Wait until your file is ready before sending a message.",
      disclaimer: "Ilm AI can make mistakes. Please double-check responses.",
      copy: "Copy",
      copied: "Copied",
    },
    chat: {
      title: "Chat with your materials",
      placeholder: "Ask a question about your uploaded materials…",
      send: "Send",
      empty:
        "Ask any question — answers come straight from your uploaded materials with citations.",
      emptyTitle: "What do you want to figure out today?",
      emptySubtitle:
        "I only answer from your uploaded sources — and I'll quiz you back.",
      starterPrompts: [
        "Explain the hardest concept here",
        "Quiz me on this material",
        "What should I review first?",
      ],
      ungrounded: "This answer didn't reference any of your sources.",
      citations: "Citations",
      citationsHint:
        "Every claim is backed by a chunk from your materials. Click a chip to see the source.",
      chunkLabel: "Chunk",
      typing: "Ilm AI is reading your materials…",
      newConversation: "New conversation",
    },
    quiz: {
      title: "Quiz mode",
      subtitle:
        "Test yourself on the material — Ilm AI grades you and shows the source.",
      startCta: "Start quiz",
      needMaterials: "Add a material to this topic before starting a quiz.",
      difficulty: "Difficulty",
      difficultyEasy: "Gentle review",
      difficultyMedium: "Solid understanding",
      difficultyHard: "Expert challenge",
      questionCount: "Questions",
      submit: "Submit",
      skip: "Skip",
      next: "Next",
      finish: "Finish",
      yourAnswer: "Your answer",
      correct: "Correct",
      incorrect: "Not quite",
      score: "Score",
      completedTitle: "Quiz complete",
      completedSubtitle: "Great work — here's how you did.",
      historyTitle: "Recent quizzes",
      historyEmpty: "No quizzes yet — start one to fill this list.",
      questionOf: "Question {current} of {total}",
      citationLabel: "Source",
      quotaReached:
        "You've reached your free quiz limit. Upgrade to keep practicing.",
      open: "Open quiz",
    },
    gaps: {
      title: "Knowledge gaps",
      subtitle:
        "Where Ilm AI thinks you’re strong, weak, and what to read next.",
      regenerate: "Re-analyse",
      generatedAt: "Generated",
      summary: "Summary",
      strongTitle: "Strengths",
      weakTitle: "Gaps",
      recommendationsTitle: "What to read next",
      openMaterial: "Open material",
      quizMeCta: "Quiz me on this",
      empty:
        "Take a quiz or chat with your materials to generate your first report.",
    },
    plan: {
      title: "Learning plan",
      subtitle: "A day-by-day route from where you are to your goal.",
      goal: "Goal",
      goalPlaceholder: "e.g. Pass the AWS SAA exam",
      target: "Target date",
      regenerate: "Generate new plan",
      generatedAt: "Generated",
      progress: "Progress",
      upcoming: "Upcoming days",
      today: "Today",
      todayBadge: "Today",
      minutes: "min",
      actionRead: "Read",
      actionQuiz: "Quiz",
      actionReview: "Review",
      markDone: "Mark done",
      completed: "Completed",
      replanTitle: "Your plan may be out of date",
      replanDescription:
        "Ask the Coach in chat to rebuild it, then refresh this page.",
      refresh: "Refresh",
      dayLabel: "Day {n}",
      openCta: "Open",
      editGoal: "Edit goal",
      setGoal: "Set goal",
      startLesson: "Start lesson",
      openLesson: "Open lesson",
      regenerateLesson: "Regenerate",
      lessonHeading: "Lesson",
      lessonSources: "From your materials",
      lessonGenerating: "Building your lesson from your materials…",
      startQuiz: "Start quiz",
      hideLesson: "Hide",
      empty: "Set a goal and target date on your profile to generate a plan.",
    },
    stats: {
      title: "Your stats",
      subtitle: "Track your knowledge over time.",
      sessionsCompleted: "Study sessions",
      topicsCount: "Topics",
      materialsCount: "Materials",
      streakDays: "Day streak",
      weeklyMinutes: "Minutes this week",
      knowledgeScore: "Knowledge score",
      currentLevel: "Current level",
      historyTitle: "Knowledge score over time",
      perTopicTitle: "Per topic",
    },
    profile: {
      title: "Profile",
      subtitle: "Your learning identity, progress, and goal.",
      identityTitle: "You",
      statsTitle: "At a glance",
      trendTitle: "Knowledge score over time",
      perTopicTitle: "Per topic",
      goalTitle: "Your goal",
      goalSubtitle: "Set what you're working toward — Ilm AI plans around it.",
      goalLabel: "Goal",
      goalPlaceholder: "e.g. Pass the AWS SAA exam",
      targetLabel: "Target date",
      saveGoal: "Save goal",
      goalSaved: "Goal saved",
      memberSince: "Learning since",
    },
    telegram: {
      title: "Telegram reminders",
      subtitle: "Get a daily nudge with what to study next.",
      statusLinked: "Linked",
      statusPending: "Waiting for confirmation",
      statusNotLinked: "Not linked",
      capabilitiesTitle: "What the bot can do",
      capabilityReminderTitle: "Daily reminder",
      capabilityReminderDescription:
        "A short nudge at the time you choose with what to study today.",
      capabilityQuizTitle: "On-demand 5-question quiz",
      capabilityQuizDescription:
        "Send /quiz in Telegram and we'll grade you on the spot — no need to open the app.",
      capabilityStreakTitle: "Streak celebrations",
      capabilityStreakDescription:
        "We mark consistent days so you can keep the habit going.",
      connectTitle: "Connect your Telegram",
      connectSubtitle:
        "Generate a one-time code, open our bot, and it links to your account automatically.",
      generateCode: "Generate link code",
      regenerateCode: "Generate a new code",
      pendingTitle: "Finish linking in Telegram",
      pendingSubtitle:
        "Open the bot and press Start — the code is sent for you. This page updates on its own.",
      codeLabel: "Your link code",
      openInTelegram: "Open in Telegram",
      pendingHint:
        "No Telegram on this device? Open @{bot} on your phone and send: /start {code}",
      waitingForLink: "Waiting for you to confirm in Telegram…",
      codeExpires: "Code expires {time}",
      linkedTitle: "Telegram is linked",
      linkedAs: "Linked account",
      linkedOn: "Linked on",
      unlink: "Unlink Telegram",
      unlinkConfirmTitle: "Unlink Telegram?",
      unlinkConfirmDescription:
        "You'll stop receiving reminders and quizzes in Telegram. You can link again anytime.",
      unlinkConfirmAction: "Unlink",
      unlinkConfirmDismiss: "Keep linked",
      linkedToast: "Telegram linked!",
      unlinkedToast: "Telegram unlinked.",
      codeCopied: "Code copied",
      reminderNote:
        "Reminder time follows your study schedule — change it under Settings → Telegram.",
    },
    premium: {
      title: "Ilm AI Premium",
      subtitle:
        "Unlimited quizzes, full multi-language support, priority models.",
      tierFree: "Free",
      tierPremium: "Premium",
      statusActive: "Active",
      statusPending: "Pending",
      statusCanceled: "Canceled",
      statusExpired: "Expired",
      currentTitle: "Your subscription",
      renewsOn: "Renews on",
      endsOn: "Access until",
      willNotRenew: "Your plan won't renew.",
      cancel: "Cancel subscription",
      cancelConfirmTitle: "Cancel your subscription?",
      cancelConfirmDescription:
        "Your plan stays active until the end of the current period, then won't renew.",
      cancelConfirmAction: "Cancel subscription",
      cancelConfirmDismiss: "Keep my plan",
      canceledToast: "Your subscription won't renew.",
      choosePlanTitle: "Go Premium",
      choosePlanSubtitle: "Pick a plan and a payment method to continue.",
      providerLabel: "Payment method",
      providerTest: "Test",
      planMonthly: "Monthly",
      planYearly: "Yearly",
      billedMonthly: "Billed every month",
      billedYearly: "Billed once a year · best value",
      subscribe: "Subscribe",
      redirecting: "Redirecting to checkout…",
      checkoutSuccess: "Subscription activated. Welcome to Premium!",
      checkoutCanceled: "Checkout canceled — no charge was made.",
      freeFeatures: [
        "3 quiz sessions per day",
        "Up to 5 file uploads",
        "Companion chat with citations",
        "UZ / RU / EN responses",
      ],
      premiumFeatures: [
        "Unlimited quiz sessions",
        "Unlimited uploads",
        "Full learning plan with knowledge gap detection",
        "Priority response speed",
        "Telegram reminders, daily streaks, on-demand quizzes",
      ],
      paymentsTitle: "Payment history",
      paymentsEmpty: "No payments yet.",
      subscriptionsTitle: "Subscription history",
      subscriptionsEmpty: "No subscriptions yet.",
      colPlan: "Plan",
      colStatus: "Status",
      colProvider: "Method",
      colPeriod: "Period",
      colAmount: "Amount",
      colDate: "Date",
      paySucceeded: "Paid",
      payPending: "Pending",
      payFailed: "Failed",
      payRefunded: "Refunded",
      quotaQuizzes: "You're on the Free plan — upgrade for unlimited quizzes.",
      quotaUploads: "You're on the Free plan — upgrade for unlimited uploads.",
    },
    topicCard: {
      materials: "materials",
      chunks: "chunks",
      updatedAt: "Updated",
      openChat: "Open",
    },
    home: {
      greetingMorning: "Good morning, {name}",
      greetingAfternoon: "Good afternoon, {name}",
      greetingEvening: "Good evening, {name}",
      streak: {
        title: "Day streak",
        subtitle: "Showing up every day is how it sticks.",
        days: "days",
        day: "day",
        weeklyMinutes: "{minutes} min this week",
        keepGoing: "Keep the streak alive — open any goal below.",
        startToday: "Start today to begin your streak.",
        toNextMilestone: "{days} to go · target {milestone}",
        todayLabel: "Today",
        weekdays: {
          mon: "Mon",
          tue: "Tue",
          wed: "Wed",
          thu: "Thu",
          fri: "Fri",
          sat: "Sat",
          sun: "Sun",
        },
      },
      path: {
        title: "Your path",
        subtitle: "All your goals, one continuous journey.",
        aggregate: "{completed} of {total} days across all goals",
        empty: "No goals yet. Set your first goal to start your path.",
        addGoal: "Add a goal",
        viewPlan: "View plan",
        percentLabel: "complete",
        activeLabel: "Active",
        completedLabel: "Completed",
        daysLabel: "Days done",
      },
      goals: {
        title: "Your goals",
        subtitle: "Pick the one you want to work on next.",
        emptyTitle: "Start your path",
        emptyDescription:
          "Add your first goal — we'll build the plan with you.",
      },
      goal: {
        targetDate: "By {date}",
        noTargetDate: "No target date",
        progress: "{percent}%",
        daysProgress: "{completed} of {total} days",
        nextStep: "Next",
        statusActive: "Active",
        statusPaused: "Paused",
        statusCompleted: "Done",
      },
      today: {
        title: "Today",
        subtitle: "Your plan for today.",
        emptyTitle: "No plan yet",
        emptyDescription:
          "Set a goal and we'll lay out a day-by-day route to get there.",
        createPlan: "Create a plan",
        viewPlan: "View full plan",
        doneLabel: "done",
        minutesLeft: "{minutes} min left",
        minutesTotal: "{minutes} min total",
        allDone: "All done — great work",
        restDay: "Rest day — no tasks scheduled.",
        pathLine: "Path · {goal}",
        filterAll: "All goals",
        goalLabel: "Goal",
      },
      addGoalDialog: {
        trigger: "Add a goal",
        title: "Add a new goal",
        description:
          "Tell us what you want to learn. We'll set it as a goal and build a day-by-day plan from your materials.",
        goalLabel: "Your goal",
        goalPlaceholder: 'e.g. "Pass IELTS in 6 weeks"',
        deadlineLabel: "Target date",
        deadlineHint: "Optional — leave empty if there's no deadline.",
        cancel: "Cancel",
        submit: "Create goal",
        seedWithDeadline:
          "I want to set a new learning goal: {goal}, by {deadline}. Please set it as my goal and build a study plan from my materials.",
        seedWithoutDeadline:
          "I want to set a new learning goal: {goal}. Please set it as my goal and build a study plan from my materials.",
      },
      onboarding: {
        title: "Welcome to Ilm AI",
        subtitle: "Set up your personal tutor in 3 quick steps.",
        step1Title: "Create your first topic",
        step1Description:
          'Name what you\'re learning — e.g. "Cloud Architecture" or "Italian Cooking Theory".',
        step1Cta: "Create a topic",
        step2Title: "Upload your materials",
        step2Description:
          "PDF, Word, plain text, or paste anything. Ilm AI grounds every answer in your sources.",
        step3Title: "Ask me anything",
        step3Description:
          "Chat, quiz yourself, and I'll quiz you back — citing the exact paragraph each time.",
      },
    },
    onboarding: {
      skip: "Skip for now",
      next: "Next",
      back: "Back",
      saving: "Saving…",
      stepOf: "Step {current} of {total}",
      welcome: {
        title: "Welcome to Ilm AI",
        subtitle:
          "Upload anything you're studying. I'll tutor you on it — and only it.",
        languageLabel: "Your language",
        cta: "Let's go",
      },
      goal: {
        title: "What do you want to achieve?",
        subtitle: "This shapes your plan. You can change it anytime.",
        goalLabel: "Your goal",
        goalPlaceholder: 'e.g. "Pass IELTS in 6 weeks"',
        targetLabel: "Target date",
        targetHint: "Optional — leave empty if there's no deadline.",
        dailyLabel: "Daily study time",
        dailyHint: "How much can you study most days?",
        minutesSuffix: "min",
        customOption: "Custom",
        customPlaceholder: "Minutes",
      },
      upload: {
        title: "Upload your first material",
        subtitle: "PDF, Word, plain text — or paste your notes.",
        fileTab: "Upload file",
        pasteTab: "Paste text",
        dropZone: "Drag & drop a file here",
        browse: "Browse files",
        formatsHint: "PDF, Word, or .txt.",
        privacyNote: "Your content stays private to you.",
        pasteTitlePlaceholder: "Title",
        pasteContentPlaceholder: "Paste your notes here…",
        pasteSubmit: "Add text",
        uploading: "Uploading…",
        processing: "Preparing your material…",
        ready: "Ready",
        failed: "Upload failed",
        retry: "Try again",
        waiting: "Add a material to continue.",
        continue: "Continue",
        showAll: "Show all ({count})",
        showLess: "Show less",
        delete: "Delete",
      },
      finish: {
        title: "You're all set",
        subtitle: "Your material is ready. Ask me anything about it.",
        askPrompt: "Try asking…",
        starters: [
          "Summarize the key points",
          "Quiz me on this",
          "What should I focus on first?",
        ],
        startChat: "Start chatting",
        explore: "Explore on my own",
      },
    },
  },
  ru: {
    brand: {
      name: "Ilm AI",
      tagline: "Ваш персональный ИИ-наставник для учёбы.",
      bullets: [
        "Загружайте свои материалы — PDF, заметки, книги.",
        "Общайтесь, проходите тесты и находите пробелы в знаниях.",
        "Получайте план под вашу цель и ваше время.",
      ],
      footer: "Учитесь на узбекском, русском или английском.",
    },
    common: {
      back: "Назад",
      loading: "Загрузка…",
      errorTitle: "Что-то пошло не так",
      retry: "Повторить",
      language: "Язык",
      theme: "Тема",
      themeLight: "Светлая",
      themeDark: "Тёмная",
      themeSystem: "Системная",
    },
    login: {
      title: "С возвращением",
      subtitle: "Войдите, чтобы продолжить с того места, где остановились.",
      continueWithGoogle: "Войти через Google",
      moreOptions: "Вход через Telegram — уже скоро.",
      noAccount: "Впервые в Ilm AI?",
      createAccount: "Создать аккаунт",
    },
    signup: {
      title: "Создайте аккаунт",
      subtitle:
        "Загрузите свои материалы — и Ilm AI станет наставником именно по ним.",
      continueWithGoogle: "Продолжить через Google",
      haveAccount: "Уже есть аккаунт?",
      signIn: "Войти",
      terms: "Продолжая, вы соглашаетесь с",
      termsLink: "Условиями",
      privacyLink: "Политикой конфиденциальности",
    },
    errors: {
      generic: "Что-то пошло не так. Попробуйте ещё раз.",
      network: "Ошибка сети. Проверьте подключение и повторите попытку.",
      signInFailed: "Не удалось войти. Попробуйте ещё раз.",
      sessionExpired: "Срок сессии истёк. Войдите снова.",
    },
    settings: {
      title: "Настройки",
      subtitle: "Настройте Ilm AI: связь с Telegram, тариф и ваши данные.",
      back: "К настройкам",
      hub: {
        subtitle:
          "Управляйте аккаунтом, подключением Telegram, тарифом и данными.",
        accountDescription: "Ваш профиль, email и статус аккаунта.",
        telegramDescription:
          "Привяжите Telegram-бота для ежедневных напоминаний.",
        subscriptionDescription: "Ваш тариф, лимиты и продление.",
        dataPrivacyDescription: "Сбросить демо-данные или выйти из аккаунта.",
      },
      account: {
        title: "Аккаунт",
        subtitle: "Данные из аккаунта, через который вы вошли.",
        name: "Имя",
        email: "Email",
        accountId: "ID аккаунта",
        memberSince: "С нами с",
        unknownName: "Ученик",
        statusActive: "Активен",
        statusDisabled: "Отключён",
      },
      telegram: {
        title: "Telegram",
        subtitle: "Ежедневные напоминания и подсказки для учёбы.",
        telegramLabel: "Напоминания в Telegram",
        telegramDescription: "Каждый день получайте подсказку, с чего начать.",
        statusConnected: "Подключено",
        statusNotConnected: "Не подключено",
        reminderTime: "Время",
        streak: "Серия",
        days: "дней",
        totalReminders: "Отправлено",
        manage: "Управлять",
      },
      subscription: {
        title: "Подписка",
        subtitle: "Ваш тариф, лимиты и продление.",
        planLabel: "Тариф",
        tierFree: "Бесплатный",
        tierPremium: "Премиум",
        freeDescription: "Лимиты бесплатного тарифа обновляются каждый месяц.",
        premiumDescription:
          "Безлимитные тесты, загрузки и приоритетные модели.",
        remainingQuizzes: "Тестов осталось",
        remainingUploads: "Загрузок осталось",
        unlimited: "Без лимита",
        monthly: "Ежемесячно",
        resetsMonthly: "Сброс",
        renewsOn: "Продление",
        upgrade: "Перейти на Премиум",
        managePlan: "Управлять подпиской",
      },
      danger: {
        title: "Данные и приватность",
        subtitle: "Сбросьте демо-данные или выйдите из аккаунта.",
        resetLabel: "Сбросить демо-данные",
        resetDescription:
          "Восстановить исходные темы, материалы и статистику. Действие необратимо.",
        resetButton: "Сбросить",
        resetConfirm:
          "Все ваши данные будут заменены исходными демо-данными. Продолжить?",
        resetSuccess: "Демо-данные восстановлены.",
        signOutLabel: "Выйти",
        signOutDescription: "Завершить сеанс на этом устройстве.",
        signOutButton: "Выйти",
        warning:
          "В обычном режиме эти действия не удаляют ваши данные — только завершают сеанс.",
      },
    },
    topics: {
      title: "Ваши данные",
      subtitle: "Все учебные материалы в одном месте.",
      sectionTopics: "Темы",
      sectionItems: "Материалы",
      itemsSubtitle: "Файлы и заметки в этом пространстве вне темы.",
      empty: "Тем пока нет. Создайте первую, чтобы загружать материалы.",
      createCta: "Новая тема",
      createTitle: "Создание темы",
      createSubtitle: "Дайте теме короткое и понятное имя.",
      createPlaceholder: "например: Облачная архитектура",
      createSubmit: "Создать коллекцию",
      createCancel: "Отмена",
      failedToLoad: "Не удалось загрузить темы. Попробуйте ещё раз.",
      backToTopics: "К списку тем",
      rename: "Переименовать",
      delete: "Удалить",
      confirmDelete: "Удалить эту тему вместе со всеми материалами?",
      loadMore: "Показать ещё",
      errors: {
        nameBlank: "Название темы не должно быть пустым.",
        nameTaken: "У вас уже есть тема с таким именем.",
        notFound: "Тема не найдена.",
        generic: "Что-то пошло не так. Попробуйте ещё раз.",
      },
    },
    materials: {
      title: "Материалы",
      subtitle: "Загрузите файлы или вставьте текст в эту тему.",
      empty: "Материалов пока нет. Загрузите файл или вставьте текст.",
      uploadTitle: "Загрузить файл",
      uploadSubtitle: "PDF, Word или простой текст — до 20 МБ.",
      uploadDropZone: "Перетащите файл сюда или",
      uploadBrowse: "выберите",
      uploadSubmit: "Загрузить",
      pasteTitle: "Вставить текст",
      pasteSubtitle: "Вставьте статью, заметки или любой текст.",
      pasteTitlePlaceholder: "Заголовок (обязательно)",
      pasteContentPlaceholder: "Вставьте содержимое здесь",
      pasteSubmit: "Добавить",
      listTitle: "Ваши материалы",
      replace: "Заменить",
      delete: "Удалить",
      confirmDelete: "Удалить этот материал?",
      failedToLoad: "Не удалось загрузить материалы. Попробуйте ещё раз.",
      move: {
        action: "Переместить",
        title: "Переместить материал",
        subtitle: "Выберите, где хранить этот материал.",
        root: "Без темы",
        current: "Текущее",
        empty: "Сначала создайте тему, чтобы перемещать в неё материалы.",
        success: "Материал перемещён.",
      },
      status: {
        pending: "Ожидание",
        processing: "Обработка",
        ready: "Готово",
        failed: "Ошибка",
      },
      errors: {
        notFound: "Материал не найден.",
        topicNotFound: "Тема не найдена.",
        contentRequired: "Укажите файл или вставленный текст.",
        titleBlank: "Заголовок обязателен.",
        unsupportedType: "Этот тип файла пока не поддерживается.",
        tooLarge: "Файл слишком большой.",
        storageFailed: "Хранилище временно недоступно.",
        generic: "Что-то пошло не так. Попробуйте ещё раз.",
      },
    },
    nav: {
      home: "Главная",
      topics: "Данные",
      chat: "Чат",
      quiz: "Тесты",
      gaps: "Пробелы",
      plan: "План",
      profile: "Профиль",
      stats: "Статистика",
      telegram: "Telegram",
      premium: "Premium",
      settings: "Настройки",
      workspace: "Рабочее пространство",
      signOut: "Выйти",
      upgrade: "Перейти на Premium",
      demoMode: "Демо-режим",
      resetDemo: "Сбросить демо-данные",
      userMenu: "Меню аккаунта",
      companion: "Компаньон",
    },
    companion: {
      title: "Компаньон",
      subtitle: "Ваш ИИ-наставник по всем загруженным материалам.",
      newSession: "Новый чат",
      sessionsTitle: "Чаты",
      noSessions: "Чатов пока нет. Начните ниже.",
      untitled: "Новый чат",
      inputPlaceholder: "Спросите что-нибудь о ваших материалах…",
      send: "Отправить",
      stop: "Стоп",
      thinking: "Думаю…",
      emptyTitle: "Спросите компаньона",
      emptyDescription:
        "Он отвечает на основе ваших материалов и указывает источники.",
      ungrounded: "Не основано на ваших материалах",
      citations: "Источники",
      usedTool: "Использован инструмент: {tool}",
      quizSubmit: "Ответить",
      quizCorrect: "Верно",
      quizIncorrect: "Не совсем",
      quizCorrectAnswer: "Ответ",
      quizExplanation: "Почему",
      quizAnswerPlaceholder: "Введите ответ…",
      quizTitle: "Тест",
      quizStart: "Начать тест",
      quizStartHint: "Пройдите и отправьте тест, чтобы продолжить общение.",
      quizQuestionsLabel: "Вопросов: {count}",
      quizQuestionOf: "Вопрос {current} из {total}",
      quizNext: "Следующий вопрос",
      quizFinish: "Отправить тест",
      quizTimeLeft: "Осталось времени",
      quizResultsTitle: "Результаты теста",
      quizScore: "Результат",
      quizYourAnswer: "Ваш ответ",
      quizSkipped: "Пропущено",
      quizGrading: "Проверка…",
      quizComposerBlocked:
        "Завершите тест выше, прежде чем отправлять сообщение.",
      quizModeTimed: "На время",
      quizModeExplain: "Пошагово",
      quizModeSingle: "Все вопросы",
      askAboutTopic: "Спросить об этой теме",
      topicSeedPrompt: "Помоги мне изучить тему «{topic}».",
      failedToLoad: "Не удалось загрузить чаты. Попробуйте ещё раз.",
      attachFile: "Прикрепить файл",
      attachmentProcessing: "Обрабатываем {name}…",
      attachmentReady: "{name} готов",
      attachmentFailed: "Не удалось обработать {name}",
      attachmentRemove: "Удалить файл",
      attachmentGate:
        "Дождитесь готовности файла, прежде чем отправлять сообщение.",
      disclaimer: "Ilm AI может ошибаться. Проверяйте важные ответы.",
      copy: "Копировать",
      copied: "Скопировано",
    },
    chat: {
      title: "Чат по вашим материалам",
      placeholder: "Задайте вопрос по загруженным материалам…",
      send: "Отправить",
      empty:
        "Задайте любой вопрос — ответы строятся по вашим материалам и сопровождаются ссылками.",
      emptyTitle: "С чем разобраться сегодня?",
      emptySubtitle:
        "Я отвечаю только по вашим материалам — и обязательно задам встречный вопрос.",
      starterPrompts: [
        "Объясни самое сложное здесь",
        "Проверь меня по этому материалу",
        "С чего лучше начать повторение?",
      ],
      ungrounded: "В этом ответе не было ссылок на ваши источники.",
      citations: "Источники",
      citationsHint:
        "Каждое утверждение опирается на фрагмент ваших материалов.",
      chunkLabel: "Фрагмент",
      typing: "Ilm AI читает ваши материалы…",
      newConversation: "Новый разговор",
    },
    quiz: {
      title: "Режим тестов",
      subtitle:
        "Проверьте себя — Ilm AI оценивает ответы и показывает источник.",
      startCta: "Начать тест",
      needMaterials: "Сначала добавьте материалы в эту тему.",
      difficulty: "Сложность",
      difficultyEasy: "Мягкий повтор",
      difficultyMedium: "Уверенное понимание",
      difficultyHard: "Экспертный уровень",
      questionCount: "Вопросов",
      submit: "Ответить",
      skip: "Пропустить",
      next: "Далее",
      finish: "Завершить",
      yourAnswer: "Ваш ответ",
      correct: "Верно",
      incorrect: "Не совсем",
      score: "Результат",
      completedTitle: "Тест завершён",
      completedSubtitle: "Отличная работа — вот ваши результаты.",
      historyTitle: "Недавние тесты",
      historyEmpty: "Тестов пока нет — начните первый.",
      questionOf: "Вопрос {current} из {total}",
      citationLabel: "Источник",
      quotaReached:
        "Вы достигли лимита бесплатных тестов. Оформите Premium, чтобы продолжить.",
      open: "Открыть тест",
    },
    gaps: {
      title: "Пробелы в знаниях",
      subtitle: "В чём вы сильны, где есть пробелы и что почитать дальше.",
      regenerate: "Пересчитать",
      generatedAt: "Сгенерировано",
      summary: "Кратко",
      strongTitle: "Сильные стороны",
      weakTitle: "Пробелы",
      recommendationsTitle: "Что почитать дальше",
      openMaterial: "Открыть материал",
      quizMeCta: "Проверить меня",
      empty:
        "Пройдите тест или пообщайтесь в чате, чтобы получить первый отчёт.",
    },
    plan: {
      title: "План обучения",
      subtitle: "Маршрут от вашего текущего уровня до цели по дням.",
      goal: "Цель",
      goalPlaceholder: "например: Сдать AWS SAA",
      target: "Срок",
      regenerate: "Создать новый план",
      generatedAt: "Сгенерировано",
      progress: "Прогресс",
      upcoming: "Ближайшие дни",
      today: "Сегодня",
      todayBadge: "Сегодня",
      minutes: "мин",
      actionRead: "Чтение",
      actionQuiz: "Тест",
      actionReview: "Повторение",
      markDone: "Отметить",
      completed: "Выполнено",
      replanTitle: "План мог устареть",
      replanDescription:
        "Попросите Коуча в чате перестроить план, затем обновите страницу.",
      refresh: "Обновить",
      dayLabel: "День {n}",
      openCta: "Открыть",
      editGoal: "Изменить цель",
      setGoal: "Задать цель",
      startLesson: "Начать урок",
      openLesson: "Открыть урок",
      regenerateLesson: "Сгенерировать заново",
      lessonHeading: "Урок",
      lessonSources: "Из ваших материалов",
      lessonGenerating: "Готовим урок по вашим материалам…",
      startQuiz: "Начать тест",
      hideLesson: "Скрыть",
      empty: "Задайте цель и дату в профиле, чтобы создать план.",
    },
    stats: {
      title: "Ваша статистика",
      subtitle: "Отслеживайте свои знания во времени.",
      sessionsCompleted: "Учебные сессии",
      topicsCount: "Темы",
      materialsCount: "Материалы",
      streakDays: "Дней подряд",
      weeklyMinutes: "Минут за неделю",
      knowledgeScore: "Балл знаний",
      currentLevel: "Текущий уровень",
      historyTitle: "Динамика балла знаний",
      perTopicTitle: "По темам",
    },
    profile: {
      title: "Профиль",
      subtitle: "Ваша учебная идентичность, прогресс и цель.",
      identityTitle: "О вас",
      statsTitle: "Кратко",
      trendTitle: "Динамика балла знаний",
      perTopicTitle: "По темам",
      goalTitle: "Ваша цель",
      goalSubtitle: "Поставьте цель — Ilm AI выстроит план вокруг неё.",
      goalLabel: "Цель",
      goalPlaceholder: "напр. Сдать AWS SAA",
      targetLabel: "Целевая дата",
      saveGoal: "Сохранить",
      goalSaved: "Цель сохранена",
      memberSince: "Учится с",
    },
    telegram: {
      title: "Напоминания в Telegram",
      subtitle: "Получайте ежедневные напоминания: что повторить и пройти.",
      statusLinked: "Подключено",
      statusPending: "Ожидаем подтверждения",
      statusNotLinked: "Не подключено",
      capabilitiesTitle: "Что умеет бот",
      capabilityReminderTitle: "Ежедневное напоминание",
      capabilityReminderDescription:
        "Короткое сообщение в выбранное время — что изучить сегодня.",
      capabilityQuizTitle: "Квиз из 5 вопросов по запросу",
      capabilityQuizDescription:
        "Отправьте /quiz в Telegram — проверим вас сразу, без открытия приложения.",
      capabilityStreakTitle: "Серии и привычка",
      capabilityStreakDescription:
        "Отмечаем стабильные дни, чтобы удержать привычку.",
      connectTitle: "Подключите Telegram",
      connectSubtitle:
        "Сгенерируйте одноразовый код, откройте нашего бота — он сам привяжет аккаунт.",
      generateCode: "Сгенерировать код",
      regenerateCode: "Сгенерировать новый код",
      pendingTitle: "Завершите привязку в Telegram",
      pendingSubtitle:
        "Откройте бота и нажмите Start — код подставится сам. Страница обновится автоматически.",
      codeLabel: "Ваш код привязки",
      openInTelegram: "Открыть в Telegram",
      pendingHint:
        "Нет Telegram на этом устройстве? Откройте @{bot} на телефоне и отправьте: /start {code}",
      waitingForLink: "Ждём подтверждения в Telegram…",
      codeExpires: "Код истекает {time}",
      linkedTitle: "Telegram подключён",
      linkedAs: "Привязанный аккаунт",
      linkedOn: "Подключено",
      unlink: "Отвязать Telegram",
      unlinkConfirmTitle: "Отвязать Telegram?",
      unlinkConfirmDescription:
        "Вы перестанете получать напоминания и квизы в Telegram. Привязать можно в любой момент.",
      unlinkConfirmAction: "Отвязать",
      unlinkConfirmDismiss: "Оставить",
      linkedToast: "Telegram подключён!",
      unlinkedToast: "Telegram отвязан.",
      codeCopied: "Код скопирован",
      reminderNote:
        "Время напоминаний зависит от вашего расписания — измените его в «Настройки → Telegram».",
    },
    premium: {
      title: "Ilm AI Premium",
      subtitle:
        "Безлимитные тесты, полная поддержка трёх языков, приоритетные модели.",
      tierFree: "Free",
      tierPremium: "Premium",
      statusActive: "Активна",
      statusPending: "Ожидает",
      statusCanceled: "Отменена",
      statusExpired: "Истекла",
      currentTitle: "Ваша подписка",
      renewsOn: "Продлится",
      endsOn: "Доступ до",
      willNotRenew: "Подписка не будет продлена.",
      cancel: "Отменить подписку",
      cancelConfirmTitle: "Отменить подписку?",
      cancelConfirmDescription:
        "Подписка останется активной до конца текущего периода, затем не продлится.",
      cancelConfirmAction: "Отменить подписку",
      cancelConfirmDismiss: "Оставить подписку",
      canceledToast: "Подписка не будет продлена.",
      choosePlanTitle: "Перейти на Premium",
      choosePlanSubtitle: "Выберите план и способ оплаты, чтобы продолжить.",
      providerLabel: "Способ оплаты",
      providerTest: "Тест",
      planMonthly: "Месячный",
      planYearly: "Годовой",
      billedMonthly: "Оплата каждый месяц",
      billedYearly: "Оплата раз в год · выгоднее",
      subscribe: "Оформить",
      redirecting: "Переходим к оплате…",
      checkoutSuccess: "Подписка активирована. Добро пожаловать в Premium!",
      checkoutCanceled: "Оплата отменена — списания не было.",
      freeFeatures: [
        "3 квиза в день",
        "До 5 загружаемых файлов",
        "Чат с цитатами из ваших материалов",
        "Ответы на UZ / RU / EN",
      ],
      premiumFeatures: [
        "Безлимитные квизы",
        "Безлимитные загрузки",
        "Полный план обучения с детекцией пробелов",
        "Приоритетная скорость ответов",
        "Telegram-напоминания, серии, квизы по запросу",
      ],
      paymentsTitle: "История платежей",
      paymentsEmpty: "Платежей пока нет.",
      subscriptionsTitle: "История подписок",
      subscriptionsEmpty: "Подписок пока нет.",
      colPlan: "План",
      colStatus: "Статус",
      colProvider: "Способ",
      colPeriod: "Период",
      colAmount: "Сумма",
      colDate: "Дата",
      paySucceeded: "Оплачено",
      payPending: "Ожидает",
      payFailed: "Ошибка",
      payRefunded: "Возврат",
      quotaQuizzes:
        "Вы на бесплатном плане — оформите Premium для безлимитных квизов.",
      quotaUploads:
        "Вы на бесплатном плане — оформите Premium для безлимитных загрузок.",
    },
    topicCard: {
      materials: "материалов",
      chunks: "фрагментов",
      updatedAt: "Обновлено",
      openChat: "Открыть",
    },
    home: {
      greetingMorning: "Доброе утро, {name}",
      greetingAfternoon: "Добрый день, {name}",
      greetingEvening: "Добрый вечер, {name}",
      streak: {
        title: "Серия дней",
        subtitle: "Главное — приходить каждый день.",
        days: "дней",
        day: "день",
        weeklyMinutes: "{minutes} мин за неделю",
        keepGoing: "Поддержите серию — откройте любую цель ниже.",
        startToday: "Начните сегодня, чтобы запустить серию.",
        toNextMilestone: "Осталось {days} · цель {milestone}",
        todayLabel: "Сегодня",
        weekdays: {
          mon: "Пн",
          tue: "Вт",
          wed: "Ср",
          thu: "Чт",
          fri: "Пт",
          sat: "Сб",
          sun: "Вс",
        },
      },
      path: {
        title: "Ваш путь",
        subtitle: "Все ваши цели — в одном пути.",
        aggregate: "{completed} из {total} дней по всем целям",
        empty: "Целей пока нет. Поставьте первую — и путь начнётся.",
        addGoal: "Добавить цель",
        viewPlan: "Открыть план",
        percentLabel: "пройдено",
        activeLabel: "Активные",
        completedLabel: "Завершено",
        daysLabel: "Пройдено дней",
      },
      goals: {
        title: "Ваши цели",
        subtitle: "Выберите, над чем работать дальше.",
        emptyTitle: "Начните свой путь",
        emptyDescription:
          "Поставьте первую цель — и мы вместе соберём для неё план.",
      },
      goal: {
        targetDate: "К {date}",
        noTargetDate: "Без срока",
        progress: "{percent}%",
        daysProgress: "{completed} из {total} дней",
        nextStep: "Дальше",
        statusActive: "Активна",
        statusPaused: "На паузе",
        statusCompleted: "Готово",
      },
      today: {
        title: "Сегодня",
        subtitle: "Ваш план на сегодня.",
        emptyTitle: "Плана пока нет",
        emptyDescription:
          "Поставьте цель — и мы соберём маршрут по дням до неё.",
        createPlan: "Создать план",
        viewPlan: "Открыть план",
        doneLabel: "сделано",
        minutesLeft: "осталось {minutes} мин",
        minutesTotal: "всего {minutes} мин",
        allDone: "Готово — отлично!",
        restDay: "День отдыха — задач нет.",
        pathLine: "Путь · {goal}",
        filterAll: "Все цели",
        goalLabel: "Цель",
      },
      addGoalDialog: {
        trigger: "Добавить цель",
        title: "Новая цель",
        description:
          "Скажите, что хотите изучить. Мы сделаем это вашей целью и построим план по дням на основе ваших материалов.",
        goalLabel: "Ваша цель",
        goalPlaceholder: "напр. «Сдать IELTS за 6 недель»",
        deadlineLabel: "Дата цели",
        deadlineHint: "Необязательно — оставьте пустым, если срока нет.",
        cancel: "Отмена",
        submit: "Создать цель",
        seedWithDeadline:
          "Хочу поставить новую учебную цель: {goal}, к {deadline}. Сделайте её моей целью и постройте план по моим материалам.",
        seedWithoutDeadline:
          "Хочу поставить новую учебную цель: {goal}. Сделайте её моей целью и постройте план по моим материалам.",
      },
      onboarding: {
        title: "Добро пожаловать в Ilm AI",
        subtitle: "Настроим вашего наставника за 3 шага.",
        step1Title: "Создайте первую тему",
        step1Description:
          "Назовите то, что изучаете — напр. «Облачная архитектура» или «Османская история».",
        step1Cta: "Создать тему",
        step2Title: "Загрузите материалы",
        step2Description:
          "PDF, Word, простой текст или вставка. Ilm AI отвечает только по вашим источникам.",
        step3Title: "Спросите что угодно",
        step3Description:
          "Общайтесь, спрашивайте — и я буду задавать вопросы вам в ответ, ссылаясь на конкретные фрагменты.",
      },
    },
    onboarding: {
      skip: "Пропустить",
      next: "Далее",
      back: "Назад",
      saving: "Сохранение…",
      stepOf: "Шаг {current} из {total}",
      welcome: {
        title: "Добро пожаловать в Ilm AI",
        subtitle:
          "Загрузите то, что изучаете. Я буду наставником именно по этому материалу.",
        languageLabel: "Ваш язык",
        cta: "Начать",
      },
      goal: {
        title: "Чего вы хотите достичь?",
        subtitle: "Это формирует ваш план. Можно изменить в любой момент.",
        goalLabel: "Ваша цель",
        goalPlaceholder: "напр. «Сдать IELTS за 6 недель»",
        targetLabel: "Дата цели",
        targetHint: "Необязательно — оставьте пустым, если срока нет.",
        dailyLabel: "Время на учёбу в день",
        dailyHint: "Сколько вы готовы заниматься в большинство дней?",
        minutesSuffix: "мин",
        customOption: "Своё",
        customPlaceholder: "Минуты",
      },
      upload: {
        title: "Загрузите первый материал",
        subtitle: "PDF, Word, обычный текст — или вставьте заметки.",
        fileTab: "Загрузить файл",
        pasteTab: "Вставить текст",
        dropZone: "Перетащите файл сюда",
        browse: "Выбрать файл",
        formatsHint: "PDF, Word или .txt.",
        privacyNote: "Ваш контент остаётся приватным.",
        pasteTitlePlaceholder: "Название",
        pasteContentPlaceholder: "Вставьте заметки сюда…",
        pasteSubmit: "Добавить текст",
        uploading: "Загрузка…",
        processing: "Готовим ваш материал…",
        ready: "Готово",
        failed: "Не удалось загрузить",
        retry: "Повторить",
        waiting: "Добавьте материал, чтобы продолжить.",
        continue: "Продолжить",
        showAll: "Показать все ({count})",
        showLess: "Свернуть",
        delete: "Удалить",
      },
      finish: {
        title: "Всё готово",
        subtitle: "Ваш материал готов. Спросите меня о чём угодно.",
        askPrompt: "Попробуйте спросить…",
        starters: [
          "Кратко перескажи главное",
          "Проверь меня тестом",
          "С чего начать?",
        ],
        startChat: "Начать чат",
        explore: "Осмотреться самому",
      },
    },
  },
  uz: {
    brand: {
      name: "Ilm AI",
      tagline: "Sizning shaxsiy sun’iy intellekt yordamchingiz.",
      bullets: [
        "O‘z materiallaringizni yuklang — PDF, qaydnoma, kitoblar.",
        "Suhbatlashing, testdan o‘ting va bilim bo‘shliqlarini toping.",
        "Maqsadingiz va vaqtingizga mos reja oling.",
      ],
      footer: "O‘zbek, rus yoki ingliz tilida o‘rganing.",
    },
    common: {
      back: "Orqaga",
      loading: "Yuklanmoqda…",
      errorTitle: "Xatolik yuz berdi",
      retry: "Qayta urinish",
      language: "Til",
      theme: "Mavzu",
      themeLight: "Yorug‘",
      themeDark: "Qorong‘i",
      themeSystem: "Tizim",
    },
    login: {
      title: "Xush kelibsiz",
      subtitle: "Qoldirgan joyingizdan davom etish uchun tizimga kiring.",
      continueWithGoogle: "Google bilan davom etish",
      moreOptions: "Telegram orqali kirish tez kunda.",
      noAccount: "Ilm AI’da yangimisiz?",
      createAccount: "Hisob yaratish",
    },
    signup: {
      title: "Hisob yarating",
      subtitle:
        "O‘z materiallaringizni keltiring — Ilm AI ular bo‘yicha sizga ustoz bo‘ladi.",
      continueWithGoogle: "Google bilan davom etish",
      haveAccount: "Hisobingiz bormi?",
      signIn: "Kirish",
      terms: "Davom etish bilan siz",
      termsLink: "Shartlar",
      privacyLink: "Maxfiylik siyosati",
    },
    errors: {
      generic: "Nimadir xato ketdi. Qayta urinib ko‘ring.",
      network:
        "Tarmoqda xato. Internet aloqasini tekshirib, qayta urinib ko‘ring.",
      signInFailed: "Tizimga kirib bo‘lmadi. Qayta urinib ko‘ring.",
      sessionExpired: "Sessiya muddati tugadi. Qaytadan tizimga kiring.",
    },
    settings: {
      title: "Sozlamalar",
      subtitle:
        "Ilm AI’ni o‘zingizga moslang: Telegram ulanishi, tarif va ma’lumotlaringiz.",
      back: "Sozlamalarga qaytish",
      hub: {
        subtitle:
          "Hisob, Telegram ulanishi, tarif va ma’lumotlaringizni boshqaring.",
        accountDescription: "Profilingiz, email va hisob holati.",
        telegramDescription:
          "Kundalik test eslatmalari uchun Telegram botni ulang.",
        subscriptionDescription: "Tarifingiz, limitlar va yangilanish.",
        dataPrivacyDescription:
          "Demo ma’lumotlarini tiklash yoki hisobdan chiqish.",
      },
      account: {
        title: "Hisob",
        subtitle: "Tizimga kirgan hisobingizdan olingan ma’lumotlar.",
        name: "Ism",
        email: "Email",
        accountId: "Hisob ID",
        memberSince: "Ro‘yxatdan o‘tgan sana",
        unknownName: "O‘quvchi",
        statusActive: "Faol",
        statusDisabled: "O‘chirilgan",
      },
      telegram: {
        title: "Telegram",
        subtitle: "Kundalik test eslatmalari va o‘rganish turtkilari.",
        telegramLabel: "Telegram eslatmalari",
        telegramDescription:
          "Har kuni nimani o‘rganish kerakligi haqida xabar oling.",
        statusConnected: "Ulangan",
        statusNotConnected: "Ulanmagan",
        reminderTime: "Vaqt",
        streak: "Ketma-ketlik",
        days: "kun",
        totalReminders: "Yuborilgan",
        manage: "Boshqarish",
      },
      subscription: {
        title: "Obuna",
        subtitle: "Tarifingiz, limitlar va yangilanish.",
        planLabel: "Tarif",
        tierFree: "Bepul",
        tierPremium: "Premium",
        freeDescription: "Bepul tarif limitlari har oy yangilanadi.",
        premiumDescription:
          "Cheksiz testlar, yuklashlar va birinchi navbatdagi modellar.",
        remainingQuizzes: "Qolgan testlar",
        remainingUploads: "Qolgan yuklashlar",
        unlimited: "Cheksiz",
        monthly: "Oylik",
        resetsMonthly: "Yangilanish",
        renewsOn: "Yangilanadi",
        upgrade: "Premium’ga o‘tish",
        managePlan: "Tarifni boshqarish",
      },
      danger: {
        title: "Ma’lumotlar va maxfiylik",
        subtitle: "Demo ma’lumotlarini tiklash yoki hisobdan chiqish.",
        resetLabel: "Demo ma’lumotlarini tiklash",
        resetDescription:
          "Asl mavzular, materiallar va statistikani tiklash. Bekor qilib bo‘lmaydi.",
        resetButton: "Tiklash",
        resetConfirm:
          "Hamma narsa boshlang‘ich demo ma’lumotlari bilan almashtiriladi. Davom etilsinmi?",
        resetSuccess: "Demo ma’lumotlari tiklandi.",
        signOutLabel: "Chiqish",
        signOutDescription: "Bu qurilmada seansni tugatish.",
        signOutButton: "Chiqish",
        warning:
          "Haqiqiy rejimda bu amallar ma’lumotlarni o‘chirmaydi — faqat seansni tugatadi.",
      },
    },
    topics: {
      title: "Ma’lumotlaringiz",
      subtitle: "Barcha o‘quv materiallaringiz bir joyda.",
      sectionTopics: "Mavzular",
      sectionItems: "Materiallar",
      itemsSubtitle: "Bu makondagi mavzuga kirmagan fayl va qaydlar.",
      empty:
        "Hozircha mavzular yo‘q. Material yuklash uchun bittasini yarating.",
      createCta: "Yangi mavzu",
      createTitle: "Mavzu yaratish",
      createSubtitle: "Mavzuga qisqa va aniq nom bering.",
      createPlaceholder: "masalan: Bulutli arxitektura",
      createSubmit: "To‘plam yaratish",
      createCancel: "Bekor qilish",
      failedToLoad: "Mavzularni yuklab bo‘lmadi. Qayta urinib ko‘ring.",
      backToTopics: "Mavzular ro‘yxatiga qaytish",
      rename: "Qayta nomlash",
      delete: "O‘chirish",
      confirmDelete:
        "Ushbu mavzuni va uning barcha materiallarini o‘chirasizmi?",
      loadMore: "Ko‘proq ko‘rsatish",
      errors: {
        nameBlank: "Mavzu nomi bo‘sh bo‘lmasligi kerak.",
        nameTaken: "Sizda shu nomli mavzu allaqachon bor.",
        notFound: "Mavzu topilmadi.",
        generic: "Nimadir xato ketdi. Qayta urinib ko‘ring.",
      },
    },
    materials: {
      title: "Materiallar",
      subtitle: "Ushbu mavzuga fayl yuklang yoki matn joylashtiring.",
      empty: "Hozircha material yo‘q. Fayl yuklang yoki matn joylashtiring.",
      uploadTitle: "Fayl yuklash",
      uploadSubtitle: "PDF, Word yoki oddiy matn — 20 MB gacha.",
      uploadDropZone: "Faylni shu yerga tashlang yoki",
      uploadBrowse: "tanlang",
      uploadSubmit: "Yuklash",
      pasteTitle: "Matn joylashtirish",
      pasteSubtitle: "Maqola, qaydlar yoki istalgan matnni joylashtiring.",
      pasteTitlePlaceholder: "Sarlavha (majburiy)",
      pasteContentPlaceholder: "Matnni shu yerga joylashtiring",
      pasteSubmit: "Qo‘shish",
      listTitle: "Materiallaringiz",
      replace: "Almashtirish",
      delete: "O‘chirish",
      confirmDelete: "Ushbu materialni o‘chirasizmi?",
      failedToLoad: "Materiallarni yuklab bo‘lmadi. Qayta urinib ko‘ring.",
      move: {
        action: "Ko‘chirish",
        title: "Materialni ko‘chirish",
        subtitle: "Bu materialni qayerda saqlashni tanlang.",
        root: "Mavzusiz",
        current: "Joriy",
        empty: "Avval mavzu yarating, so‘ng materiallarni unga ko‘chiring.",
        success: "Material ko‘chirildi.",
      },
      status: {
        pending: "Kutilmoqda",
        processing: "Qayta ishlanmoqda",
        ready: "Tayyor",
        failed: "Xato",
      },
      errors: {
        notFound: "Material topilmadi.",
        topicNotFound: "Mavzu topilmadi.",
        contentRequired: "Fayl yoki matnni taqdim eting.",
        titleBlank: "Sarlavha kerak.",
        unsupportedType: "Ushbu fayl turi hozircha qoʻllab-quvvatlanmaydi.",
        tooLarge: "Fayl juda katta.",
        storageFailed: "Saqlash xizmati hozircha mavjud emas.",
        generic: "Nimadir xato ketdi. Qayta urinib ko‘ring.",
      },
    },
    nav: {
      home: "Bosh sahifa",
      topics: "Ma’lumotlar",
      chat: "Suhbat",
      quiz: "Test",
      gaps: "Bo‘shliqlar",
      plan: "Reja",
      profile: "Profil",
      stats: "Statistika",
      telegram: "Telegram",
      premium: "Premium",
      settings: "Sozlamalar",
      workspace: "Ish maydoni",
      signOut: "Chiqish",
      upgrade: "Premium’ga o‘tish",
      demoMode: "Demo-rejim",
      resetDemo: "Demo ma’lumotlarini tiklash",
      userMenu: "Hisob menyusi",
      companion: "Hamroh",
    },
    companion: {
      title: "Hamroh",
      subtitle: "Yuklagan barcha materiallaringiz bo‘yicha AI ustozingiz.",
      newSession: "Yangi suhbat",
      sessionsTitle: "Suhbatlar",
      noSessions: "Hozircha suhbatlar yo‘q. Quyidan boshlang.",
      untitled: "Yangi suhbat",
      inputPlaceholder: "Materiallaringiz haqida so‘rang…",
      send: "Yuborish",
      stop: "To‘xtatish",
      thinking: "O‘ylayapman…",
      emptyTitle: "Hamrohingizdan so‘rang",
      emptyDescription:
        "U yuklagan materiallaringiz asosida javob beradi va manbalarni ko‘rsatadi.",
      ungrounded: "Materiallaringizga asoslanmagan",
      citations: "Manbalar",
      usedTool: "Ishlatilgan vosita: {tool}",
      quizSubmit: "Javob berish",
      quizCorrect: "To‘g‘ri",
      quizIncorrect: "Unchalik emas",
      quizCorrectAnswer: "Javob",
      quizExplanation: "Nega",
      quizAnswerPlaceholder: "Javobingizni yozing…",
      quizTitle: "Test",
      quizStart: "Testni boshlash",
      quizStartHint:
        "Suhbatni davom ettirish uchun testni yeching va yuboring.",
      quizQuestionsLabel: "{count} ta savol",
      quizQuestionOf: "{current}-savol / {total}",
      quizNext: "Keyingi savol",
      quizFinish: "Testni yuborish",
      quizTimeLeft: "Qolgan vaqt",
      quizResultsTitle: "Test natijalari",
      quizScore: "Natija",
      quizYourAnswer: "Sizning javobingiz",
      quizSkipped: "O‘tkazib yuborildi",
      quizGrading: "Baholanmoqda…",
      quizComposerBlocked:
        "Xabar yuborishdan oldin yuqoridagi testni yakunlang.",
      quizModeTimed: "Vaqtli",
      quizModeExplain: "Bosqichma-bosqich",
      quizModeSingle: "Barcha savollar",
      askAboutTopic: "Shu mavzu haqida so‘rash",
      topicSeedPrompt: "“{topic}” mavzusini o‘rganishga yordam ber.",
      failedToLoad: "Suhbatlarni yuklab bo‘lmadi. Qayta urinib ko‘ring.",
      attachFile: "Fayl biriktirish",
      attachmentProcessing: "{name} qayta ishlanmoqda…",
      attachmentReady: "{name} tayyor",
      attachmentFailed: "{name} ni qayta ishlab bo‘lmadi",
      attachmentRemove: "Faylni o‘chirish",
      attachmentGate: "Xabar yuborishdan oldin fayl tayyor bo‘lishini kuting.",
      disclaimer: "Ilm AI xato qilishi mumkin. Javoblarni tekshirib ko‘ring.",
      copy: "Nusxalash",
      copied: "Nusxalandi",
    },
    chat: {
      title: "Materiallaringiz bo‘yicha suhbat",
      placeholder: "Yuklangan materiallaringiz bo‘yicha savol bering…",
      send: "Yuborish",
      empty:
        "Har qanday savol bering — javoblar materiallaringizdan olinadi va manba ko‘rsatiladi.",
      emptyTitle: "Bugun nimani tushunmoqchisiz?",
      emptySubtitle:
        "Men faqat yuklagan manbalaringizdan javob beraman — keyin sizdan ham so‘rayman.",
      starterPrompts: [
        "Eng qiyin tushunchani tushuntir",
        "Shu material bo‘yicha menga test ber",
        "Avval nimani takrorlay?",
      ],
      ungrounded: "Bu javob sizning manbalaringizga tayanmadi.",
      citations: "Manbalar",
      citationsHint:
        "Har bir gap sizning materiallaringizdagi parchadan olingan.",
      chunkLabel: "Parcha",
      typing: "Ilm AI materiallaringizni o‘qiyapti…",
      newConversation: "Yangi suhbat",
    },
    quiz: {
      title: "Test rejimi",
      subtitle:
        "O‘zingizni sinab ko‘ring — Ilm AI javoblarni baholaydi va manbani ko‘rsatadi.",
      startCta: "Testni boshlash",
      needMaterials: "Test boshlashdan oldin ushbu mavzuga material qo‘shing.",
      difficulty: "Murakkablik",
      difficultyEasy: "Yumshoq takror",
      difficultyMedium: "Ishonchli tushunish",
      difficultyHard: "Ekspert darajasi",
      questionCount: "Savollar",
      submit: "Javob berish",
      skip: "O‘tkazib yuborish",
      next: "Keyingi",
      finish: "Tugatish",
      yourAnswer: "Sizning javobingiz",
      correct: "To‘g‘ri",
      incorrect: "To‘g‘ri emas",
      score: "Natija",
      completedTitle: "Test tugadi",
      completedSubtitle: "Yaxshi ish — mana natijangiz.",
      historyTitle: "Yaqindagi testlar",
      historyEmpty: "Hozircha testlar yo‘q — birinchisini boshlang.",
      questionOf: "{total} dan {current}-savol",
      citationLabel: "Manba",
      quotaReached:
        "Bepul testlar chekloviga yetdingiz. Davom etish uchun Premium’ga o‘ting.",
      open: "Testni ochish",
    },
    gaps: {
      title: "Bilim bo‘shliqlari",
      subtitle:
        "Qaerda kuchli, qaerda zaif ekanligingiz va keyin nima o‘qish kerakligi.",
      regenerate: "Qayta tahlil",
      generatedAt: "Yaratildi",
      summary: "Qisqacha",
      strongTitle: "Kuchli tomonlar",
      weakTitle: "Bo‘shliqlar",
      recommendationsTitle: "Keyingi o‘qish",
      openMaterial: "Materialni ochish",
      quizMeCta: "Meni tekshir",
      empty:
        "Test yoki suhbatdan o‘ting — birinchi hisobot shu yerda paydo bo‘ladi.",
    },
    plan: {
      title: "O‘quv rejasi",
      subtitle: "Hozirgi darajangizdan maqsadingizgacha kunma-kun yo‘l.",
      goal: "Maqsad",
      goalPlaceholder: "masalan: AWS SAA imtihonidan o‘tish",
      target: "Muddat",
      regenerate: "Yangi reja tuzish",
      generatedAt: "Yaratildi",
      progress: "Jarayon",
      upcoming: "Yaqin kunlar",
      today: "Bugun",
      todayBadge: "Bugun",
      minutes: "daq",
      actionRead: "O‘qish",
      actionQuiz: "Test",
      actionReview: "Takrorlash",
      markDone: "Belgilash",
      completed: "Bajarildi",
      replanTitle: "Rejangiz eskirgan bo‘lishi mumkin",
      replanDescription:
        "Murabbiydan suhbatda rejani qayta tuzishni so‘rang, so‘ng sahifani yangilang.",
      refresh: "Yangilash",
      dayLabel: "{n}-kun",
      openCta: "Ochish",
      editGoal: "Maqsadni o‘zgartirish",
      setGoal: "Maqsad qo‘yish",
      startLesson: "Darsni boshlash",
      openLesson: "Darsni ochish",
      regenerateLesson: "Qayta yaratish",
      lessonHeading: "Dars",
      lessonSources: "Materiallaringizdan",
      lessonGenerating: "Materiallaringiz asosida dars tayyorlanmoqda…",
      startQuiz: "Testni boshlash",
      hideLesson: "Yashirish",
      empty: "Reja olish uchun profilingizda maqsad va sana kiriting.",
    },
    stats: {
      title: "Statistikangiz",
      subtitle: "Vaqt davomida bilimingizni kuzating.",
      sessionsCompleted: "O‘quv sessiyalari",
      topicsCount: "Mavzular",
      materialsCount: "Materiallar",
      streakDays: "Kunlik seriya",
      weeklyMinutes: "Bu haftadagi daqiqalar",
      knowledgeScore: "Bilim balli",
      currentLevel: "Joriy daraja",
      historyTitle: "Bilim balli dinamikasi",
      perTopicTitle: "Mavzular bo‘yicha",
    },
    profile: {
      title: "Profil",
      subtitle: "Sizning o‘quv identifikatingiz, jarayoni va maqsadi.",
      identityTitle: "Siz",
      statsTitle: "Qisqacha",
      trendTitle: "Bilim balli dinamikasi",
      perTopicTitle: "Mavzular bo‘yicha",
      goalTitle: "Maqsadingiz",
      goalSubtitle:
        "Maqsadingizni belgilang — Ilm AI uni atrofida reja tuzadi.",
      goalLabel: "Maqsad",
      goalPlaceholder: "masalan, AWS SAA imtihonini topshirish",
      targetLabel: "Mo‘ljal sanasi",
      saveGoal: "Saqlash",
      goalSaved: "Maqsad saqlandi",
      memberSince: "O‘rganadi:",
    },
    telegram: {
      title: "Telegram eslatmalari",
      subtitle: "Har kuni nima o‘qish kerakligi haqida eslatma oling.",
      statusLinked: "Ulangan",
      statusPending: "Tasdiqlash kutilmoqda",
      statusNotLinked: "Ulanmagan",
      capabilitiesTitle: "Bot nima qila oladi",
      capabilityReminderTitle: "Kunlik eslatma",
      capabilityReminderDescription:
        "Siz tanlagan vaqtda qisqa eslatma — bugun nimani o‘qish kerakligi haqida.",
      capabilityQuizTitle: "Talab bo‘yicha 5 ta savol",
      capabilityQuizDescription:
        "Telegramda /quiz yuboring — ilovani ochmasdan sizni tekshiramiz.",
      capabilityStreakTitle: "Seriyani nishonlash",
      capabilityStreakDescription:
        "Doimiy kunlarni belgilaymiz — odat shaklini saqlash uchun.",
      connectTitle: "Telegram’ni ulang",
      connectSubtitle:
        "Bir martalik kod yarating, botimizni oching — u hisobingizga avtomatik bog‘lanadi.",
      generateCode: "Bog‘lanish kodini yaratish",
      regenerateCode: "Yangi kod yaratish",
      pendingTitle: "Telegramda ulashni yakunlang",
      pendingSubtitle:
        "Botni oching va Start’ni bosing — kod o‘zi yuboriladi. Bu sahifa o‘zi yangilanadi.",
      codeLabel: "Bog‘lanish kodingiz",
      openInTelegram: "Telegramda ochish",
      pendingHint:
        "Bu qurilmada Telegram yo‘qmi? Telefoningizda @{bot}’ni oching va yuboring: /start {code}",
      waitingForLink: "Telegramda tasdiqlashingizni kutmoqdamiz…",
      codeExpires: "Kod amal qilish muddati: {time}",
      linkedTitle: "Telegram ulangan",
      linkedAs: "Ulangan hisob",
      linkedOn: "Ulangan sana",
      unlink: "Telegram’ni uzish",
      unlinkConfirmTitle: "Telegram uzilsinmi?",
      unlinkConfirmDescription:
        "Telegramda eslatma va quizlarni olmaysiz. Istalgan vaqtda qayta ulashingiz mumkin.",
      unlinkConfirmAction: "Uzish",
      unlinkConfirmDismiss: "Ulangan qoldirish",
      linkedToast: "Telegram ulandi!",
      unlinkedToast: "Telegram uzildi.",
      codeCopied: "Kod nusxalandi",
      reminderNote:
        "Eslatma vaqti o‘qish jadvalingizga bog‘liq — uni «Sozlamalar → Telegram» bo‘limida o‘zgartiring.",
    },
    premium: {
      title: "Ilm AI Premium",
      subtitle:
        "Cheksiz testlar, to‘liq uch tilli qo‘llab-quvvatlash, ustuvor modellar.",
      tierFree: "Free",
      tierPremium: "Premium",
      statusActive: "Faol",
      statusPending: "Kutilmoqda",
      statusCanceled: "Bekor qilingan",
      statusExpired: "Muddati tugagan",
      currentTitle: "Obunangiz",
      renewsOn: "Yangilanadi",
      endsOn: "Kirish muddati",
      willNotRenew: "Reja yangilanmaydi.",
      cancel: "Obunani bekor qilish",
      cancelConfirmTitle: "Obuna bekor qilinsinmi?",
      cancelConfirmDescription:
        "Reja joriy davr oxirigacha faol qoladi, so‘ng yangilanmaydi.",
      cancelConfirmAction: "Obunani bekor qilish",
      cancelConfirmDismiss: "Rejani qoldirish",
      canceledToast: "Obuna yangilanmaydi.",
      choosePlanTitle: "Premium’ga o‘tish",
      choosePlanSubtitle: "Davom etish uchun reja va to‘lov usulini tanlang.",
      providerLabel: "To‘lov usuli",
      providerTest: "Test",
      planMonthly: "Oylik",
      planYearly: "Yillik",
      billedMonthly: "Har oy to‘lov",
      billedYearly: "Yiliga bir marta · eng foydali",
      subscribe: "Obuna bo‘lish",
      redirecting: "To‘lovga o‘tilmoqda…",
      checkoutSuccess: "Obuna faollashtirildi. Premium’ga xush kelibsiz!",
      checkoutCanceled: "To‘lov bekor qilindi — hech narsa yechilmadi.",
      freeFeatures: [
        "Kuniga 3 ta quiz",
        "5 tagacha fayl yuklash",
        "Manbalardan iqtibos bilan suhbat",
        "UZ / RU / EN javoblar",
      ],
      premiumFeatures: [
        "Cheksiz quizlar",
        "Cheksiz yuklamalar",
        "Bilim bo‘shliqlari aniqlanadigan to‘liq reja",
        "Tezroq javob",
        "Telegram eslatmalar, kunlik seriya, talab bo‘yicha quizlar",
      ],
      paymentsTitle: "To‘lovlar tarixi",
      paymentsEmpty: "Hozircha to‘lovlar yo‘q.",
      subscriptionsTitle: "Obunalar tarixi",
      subscriptionsEmpty: "Hozircha obunalar yo‘q.",
      colPlan: "Reja",
      colStatus: "Holat",
      colProvider: "Usul",
      colPeriod: "Davr",
      colAmount: "Summa",
      colDate: "Sana",
      paySucceeded: "To‘langan",
      payPending: "Kutilmoqda",
      payFailed: "Xato",
      payRefunded: "Qaytarilgan",
      quotaQuizzes:
        "Siz bepul rejadasiz — cheksiz quizlar uchun Premium oling.",
      quotaUploads:
        "Siz bepul rejadasiz — cheksiz yuklashlar uchun Premium oling.",
    },
    topicCard: {
      materials: "material",
      chunks: "parcha",
      updatedAt: "Yangilangan",
      openChat: "Ochish",
    },
    home: {
      greetingMorning: "Xayrli tong, {name}",
      greetingAfternoon: "Xayrli kun, {name}",
      greetingEvening: "Xayrli oqshom, {name}",
      streak: {
        title: "Kunlik seriya",
        subtitle: "Har kuni davom etish — eng muhimi.",
        days: "kun",
        day: "kun",
        weeklyMinutes: "Bu hafta {minutes} daq",
        keepGoing: "Seriyani uzmang — quyidagi maqsadlardan birini oching.",
        startToday: "Bugundan boshlab seriyangizni yo‘lga qo‘ying.",
        toNextMilestone: "{milestone} kungacha {days} qoldi",
        todayLabel: "Bugun",
        weekdays: {
          mon: "Du",
          tue: "Se",
          wed: "Cho",
          thu: "Pa",
          fri: "Ju",
          sat: "Sha",
          sun: "Ya",
        },
      },
      path: {
        title: "Sizning yo‘lingiz",
        subtitle: "Barcha maqsadlaringiz — bitta yo‘lda.",
        aggregate: "Barcha maqsadlar bo‘yicha {total} kundan {completed} kun",
        empty:
          "Hozircha maqsadlar yo‘q. Birinchisini qo‘ying — yo‘lingiz boshlanadi.",
        addGoal: "Maqsad qo‘shish",
        viewPlan: "Rejani ochish",
        percentLabel: "bajarildi",
        activeLabel: "Faol",
        completedLabel: "Bajarilgan",
        daysLabel: "Bajarilgan kunlar",
      },
      goals: {
        title: "Maqsadlaringiz",
        subtitle: "Keyingi qadamni qaysisida bosishni tanlang.",
        emptyTitle: "Yo‘lingizni boshlang",
        emptyDescription:
          "Birinchi maqsadingizni qo‘ying — birga reja tuzamiz.",
      },
      goal: {
        targetDate: "{date} gacha",
        noTargetDate: "Muddatsiz",
        progress: "{percent}%",
        daysProgress: "{total} kundan {completed}",
        nextStep: "Keyingi",
        statusActive: "Faol",
        statusPaused: "To‘xtatilgan",
        statusCompleted: "Bajarildi",
      },
      today: {
        title: "Bugun",
        subtitle: "Bugungi rejangiz.",
        emptyTitle: "Hozircha reja yo‘q",
        emptyDescription:
          "Maqsad qo‘ying — biz unga olib boruvchi kunlik yo‘lni tuzamiz.",
        createPlan: "Reja tuzish",
        viewPlan: "To‘liq rejani ochish",
        doneLabel: "bajarildi",
        minutesLeft: "{minutes} daq qoldi",
        minutesTotal: "jami {minutes} daq",
        allDone: "Bugungi reja bajarildi — zo‘r!",
        restDay: "Dam olish kuni — vazifalar yo‘q.",
        pathLine: "Yo‘l · {goal}",
        filterAll: "Barcha maqsadlar",
        goalLabel: "Maqsad",
      },
      addGoalDialog: {
        trigger: "Maqsad qo‘shish",
        title: "Yangi maqsad",
        description:
          "Nimani o‘rganmoqchi ekaningizni ayting. Buni maqsad qilib qo‘yamiz va materiallaringizdan kunlik reja tuzamiz.",
        goalLabel: "Maqsadingiz",
        goalPlaceholder: "masalan, «6 haftada IELTS topshirish»",
        deadlineLabel: "Maqsad sanasi",
        deadlineHint: "Ixtiyoriy — muddat bo‘lmasa, bo‘sh qoldiring.",
        cancel: "Bekor qilish",
        submit: "Maqsad yaratish",
        seedWithDeadline:
          "Yangi o‘quv maqsadini qo‘ymoqchiman: {goal}, {deadline} gacha. Uni maqsadim qilib qo‘ying va materiallarimdan reja tuzing.",
        seedWithoutDeadline:
          "Yangi o‘quv maqsadini qo‘ymoqchiman: {goal}. Uni maqsadim qilib qo‘ying va materiallarimdan reja tuzing.",
      },
      onboarding: {
        title: "Ilm AI ga xush kelibsiz",
        subtitle: "Shaxsiy ustozingizni 3 ta qadamda sozlang.",
        step1Title: "Birinchi mavzuni yarating",
        step1Description:
          "O‘rganayotgan narsangizni nomlang — masalan, «Bulutli arxitektura» yoki «Italyan oshpazlik nazariyasi».",
        step1Cta: "Mavzu yaratish",
        step2Title: "Materiallarni yuklang",
        step2Description:
          "PDF, Word, oddiy matn yoki nusxa. Ilm AI faqat sizning manbalaringizdan javob beradi.",
        step3Title: "Istalgan savol bering",
        step3Description:
          "Suhbatlashing — men aniq parchaga havola bilan javob beraman va o‘zim ham sizdan so‘rayman.",
      },
    },
    onboarding: {
      skip: "Hozircha o‘tkazib yuborish",
      next: "Keyingi",
      back: "Orqaga",
      saving: "Saqlanmoqda…",
      stepOf: "{total} dan {current}-qadam",
      welcome: {
        title: "Ilm AI ga xush kelibsiz",
        subtitle:
          "O‘rganayotgan narsangizni yuklang. Men aynan shu material bo‘yicha ustoz bo‘laman.",
        languageLabel: "Tilingiz",
        cta: "Boshladik",
      },
      goal: {
        title: "Nimaga erishmoqchisiz?",
        subtitle:
          "Bu rejangizni shakllantiradi. Istalgan vaqtda o‘zgartirasiz.",
        goalLabel: "Maqsadingiz",
        goalPlaceholder: "masalan, «6 haftada IELTS topshirish»",
        targetLabel: "Maqsad sanasi",
        targetHint: "Ixtiyoriy — muddat bo‘lmasa, bo‘sh qoldiring.",
        dailyLabel: "Kunlik o‘qish vaqti",
        dailyHint: "Ko‘p kunlari qancha shug‘ullana olasiz?",
        minutesSuffix: "daq",
        customOption: "Boshqa",
        customPlaceholder: "Daqiqa",
      },
      upload: {
        title: "Birinchi materialni yuklang",
        subtitle: "PDF, Word, oddiy matn — yoki qaydlaringizni joylashtiring.",
        fileTab: "Fayl yuklash",
        pasteTab: "Matn joylash",
        dropZone: "Faylni shu yerga tashlang",
        browse: "Fayl tanlash",
        formatsHint: "PDF, Word yoki .txt.",
        privacyNote: "Kontentingiz faqat sizga tegishli bo‘lib qoladi.",
        pasteTitlePlaceholder: "Sarlavha",
        pasteContentPlaceholder: "Qaydlaringizni shu yerga joylang…",
        pasteSubmit: "Matn qo‘shish",
        uploading: "Yuklanmoqda…",
        processing: "Materialingiz tayyorlanmoqda…",
        ready: "Tayyor",
        failed: "Yuklab bo‘lmadi",
        retry: "Qayta urinish",
        waiting: "Davom etish uchun material qo‘shing.",
        continue: "Davom etish",
        showAll: "Hammasini ko‘rsatish ({count})",
        showLess: "Yig‘ish",
        delete: "O‘chirish",
      },
      finish: {
        title: "Hammasi tayyor",
        subtitle: "Materialingiz tayyor. U haqida istalgan savol bering.",
        askPrompt: "So‘rab ko‘ring…",
        starters: [
          "Asosiy fikrlarni qisqacha ayt",
          "Meni test bilan sina",
          "Nimadan boshlasam bo‘ladi?",
        ],
        startChat: "Suhbatni boshlash",
        explore: "O‘zim ko‘rib chiqaman",
      },
    },
  },
}
