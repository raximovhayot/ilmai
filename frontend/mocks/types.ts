export type ProfileMe = {
  id: string
  username: string
  status: "ACTIVE" | "DISABLED" | string
  createdAt: string
  fullName: string
  picture: string | null
}

export type Topic = {
  id: string
  spaceId: string
  name: string
  description?: string
  createdAt: string
  updatedAt: string
  materialCount: number
  chunkCount: number
}

export type MaterialStatus = "PENDING" | "PROCESSING" | "READY" | "FAILED"

export type Material = {
  id: string
  topicId?: string
  title: string
  originalFilename?: string
  contentType?: string
  sizeBytes?: number
  status: MaterialStatus
  errorCode?: string
  chunkCount: number
  createdAt: string
  updatedAt: string
  body: string
}

export type Citation = {
  materialId: string
  materialName: string
  chunkIndex: number
  preview: string
  score: number
}

export type ChatRole = "USER" | "ASSISTANT"

export type ChatMessage = {
  id: string
  conversationId: string
  role: ChatRole
  content: string
  citations: Citation[]
  createdAt: string
}

export type Conversation = {
  id: string
  topicId: string
  title: string
  createdAt: string
  updatedAt: string
  lastPreview?: string
}

export type QuizDifficulty = "EASY" | "MEDIUM" | "HARD"
export type QuizQuestionType = "MCQ" | "SHORT_ANSWER" | "OPEN"
export type QuizStatus = "IN_PROGRESS" | "COMPLETED"

export type QuizQuestion = {
  id: string
  sessionId: string
  index: number
  type: QuizQuestionType
  prompt: string
  choices?: string[]
  correctAnswer: string
  citation: Citation
  userAnswer?: string
  isCorrect?: boolean
  feedback?: string
  hint?: string
}

export type QuizSession = {
  id: string
  topicId: string
  difficulty: QuizDifficulty
  status: QuizStatus
  score: number
  totalQuestions: number
  correctCount: number
  createdAt: string
  completedAt?: string
}

export type PlanActivity = "READ" | "QUIZ" | "REVIEW"

export type PlanMaterial = {
  id: string
  title: string | null
  topicId: string | null
}

export type PlanStep = {
  dayIndex: number
  scheduledDate: string | null
  title: string
  activity: PlanActivity
  materials: PlanMaterial[]
  note: string | null
  done: boolean
  completedAt: string | null
}

export type LearningPlan = {
  id: string
  goal: string | null
  targetDate: string | null
  status: "ACTIVE" | "SUPERSEDED"
  replanNeeded: boolean
  createdAt: string | null
  daysTotal: number
  daysCompleted: number
  steps: PlanStep[]
}

export type GoalStatus = "ACTIVE" | "COMPLETED" | "PAUSED"

export type Goal = {
  id: string
  title: string
  description: string | null
  targetDate: string | null
  status: GoalStatus
  progress: number
  daysTotal: number
  daysCompleted: number
  topicIds: string[]
  nextAction: string | null
  createdAt: string
  updatedAt: string
}

export type Stats = {
  sessionsCompleted: number
  topicsCount: number
  materialsCount: number
  streakDays: number
  currentLevel: string
  knowledgeScore: number
  weeklyMinutes: number
  weekActivity: boolean[]
  knowledgeHistory: { date: string; score: number }[]
  perTopic: { topicId: string; topicName: string; score: number }[]
}

export type GapsReport = {
  generatedAt: string
  summary: string
  strong: {
    label: string
    topicId: string
    score: number
    materialId?: string
  }[]
  weak: {
    label: string
    topicId: string
    score: number
    materialId?: string
  }[]
  recommendations: {
    materialId: string
    topicId: string
    reason: string
  }[]
}

export type SubscriptionPlanCode = "FREE" | "PREMIUM_MONTHLY" | "PREMIUM_YEARLY"
export type SubscriptionStatusCode =
  | "PENDING"
  | "ACTIVE"
  | "CANCELED"
  | "EXPIRED"
export type PaymentProviderCode = "STRIPE" | "PAYME" | "CLICK"
export type PaymentStatusCode = "PENDING" | "SUCCEEDED" | "FAILED" | "REFUNDED"

export type Subscription = {
  id: string
  plan: SubscriptionPlanCode
  status: SubscriptionStatusCode
  provider: PaymentProviderCode
  currentPeriodStart: string | null
  currentPeriodEnd: string | null
  cancelAtPeriodEnd: boolean
}

export type Payment = {
  id: string
  provider: PaymentProviderCode
  externalId: string | null
  amountMinor: number
  currency: string
  status: PaymentStatusCode
  occurredAt: string
}

export type CheckoutSession = {
  provider: PaymentProviderCode
  externalId: string | null
  redirectUrl: string | null
}

export type TelegramLink = {
  id?: string | null
  telegramUsername?: string | null
  chatId?: number | null
  linkedAt?: string | null
  linkCode?: string | null
  linkCodeExpiresAt?: string | null
  botUsername?: string | null
}
