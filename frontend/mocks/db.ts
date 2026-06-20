import type {
  GapsReport,
  Goal,
  LearningPlan,
  Material,
  Payment,
  ProfileMe,
  QuizQuestion,
  QuizSession,
  Stats,
  Subscription,
  Topic,
} from "./types"

const DEMO_USER: ProfileMe = {
  id: "00000000-0000-0000-0000-000000000001",
  username: "demo@ilmai.dev",
  status: "ACTIVE",
  createdAt: "2026-04-12T09:00:00.000Z",
}

const now = () => new Date().toISOString()

function uuid(): string {
  if (typeof globalThis.crypto?.randomUUID === "function") {
    return globalThis.crypto.randomUUID()
  }
  return (
    "00000000-0000-0000-0000-" +
    Math.random().toString(16).slice(2, 14).padEnd(12, "0")
  )
}

const SEED_TOPICS: Topic[] = [
  {
    id: "11111111-1111-1111-1111-111111111111",
    spaceId: "space-1",
    name: "Cloud Architecture",
    createdAt: "2026-05-04T10:00:00.000Z",
    updatedAt: "2026-05-17T14:20:00.000Z",
    materialCount: 3,
    chunkCount: 142,
    description:
      "AWS, GCP and the cost-vs-availability tradeoffs of multi-region designs.",
  },
  {
    id: "22222222-2222-2222-2222-222222222222",
    spaceId: "space-1",
    name: "Ottoman History",
    createdAt: "2026-04-21T09:15:00.000Z",
    updatedAt: "2026-05-12T16:00:00.000Z",
    materialCount: 2,
    chunkCount: 88,
    description:
      "From Osman I to the dissolution — a personal reading list for the entrance exam.",
  },
  {
    id: "33333333-3333-3333-3333-333333333333",
    spaceId: "space-1",
    name: "Italian Cooking Theory",
    createdAt: "2026-05-09T08:00:00.000Z",
    updatedAt: "2026-05-18T11:30:00.000Z",
    materialCount: 1,
    chunkCount: 24,
    description:
      "Sauce mother-recipes, regional pasta shapes, and the chemistry behind emulsions.",
  },
]

const SEED_MATERIALS: Material[] = [
  {
    id: "m-1",
    topicId: "11111111-1111-1111-1111-111111111111",
    title: "AWS Well-Architected Framework — Reliability Pillar",
    originalFilename: "well-architected-reliability.pdf",
    contentType: "application/pdf",
    sizeBytes: 482_134,
    status: "READY",
    chunkCount: 68,
    createdAt: "2026-05-04T10:05:00.000Z",
    updatedAt: "2026-05-04T10:08:00.000Z",
    body:
      "The Reliability pillar focuses on the ability of a workload to perform its intended function correctly and consistently when expected. " +
      "Key design principles include automatically recover from failure, test recovery procedures, scale horizontally, stop guessing capacity, and manage change in automation. " +
      "Multi-region active-active increases availability but raises cross-region data-transfer cost; an active-passive failover is cheaper but introduces RTO measured in minutes. " +
      "Quotas are a frequent root cause of outages — track service limits and request increases ahead of growth, not during incidents.",
  },
  {
    id: "m-2",
    topicId: "11111111-1111-1111-1111-111111111111",
    title: "Designing Data-Intensive Applications — Chapter 5",
    originalFilename: "ddia-chapter-5.pdf",
    contentType: "application/pdf",
    sizeBytes: 1_223_440,
    status: "READY",
    chunkCount: 54,
    createdAt: "2026-05-06T12:00:00.000Z",
    updatedAt: "2026-05-06T12:03:00.000Z",
    body:
      "Single-leader replication is simple to reason about but creates a bottleneck on writes. " +
      "Multi-leader replication tolerates more failure modes but conflict resolution is hard — last-write-wins loses data silently. " +
      "Leaderless (Dynamo-style) replication uses quorum reads and writes (R+W>N) to balance consistency and availability. " +
      "Read-your-writes consistency requires session stickiness or causal timestamps; CRDTs let you skip conflict resolution but only for restricted data types.",
  },
  {
    id: "m-3",
    topicId: "11111111-1111-1111-1111-111111111111",
    title: "My notes on observability",
    contentType: "text/plain",
    sizeBytes: 1_842,
    status: "READY",
    chunkCount: 20,
    createdAt: "2026-05-17T14:18:00.000Z",
    updatedAt: "2026-05-17T14:20:00.000Z",
    body:
      "Three pillars: metrics (numeric, aggregable), logs (textual, high cardinality), traces (request-level, causal). " +
      "Metrics are cheap to store but lose context — pair them with exemplars to jump to the originating trace. " +
      "Tail-based sampling preserves slow requests; head-based sampling is cheaper. " +
      "SLO error budget should drive deploy throttling, not the other way around.",
  },
  {
    id: "m-4",
    topicId: "22222222-2222-2222-2222-222222222222",
    title: "Origins of the Ottoman Empire — Cambridge",
    originalFilename: "origins-ottoman.pdf",
    contentType: "application/pdf",
    sizeBytes: 2_300_000,
    status: "READY",
    chunkCount: 50,
    createdAt: "2026-04-21T09:30:00.000Z",
    updatedAt: "2026-04-21T09:34:00.000Z",
    body:
      "Osman I (c. 1258–1326) founded the dynasty in north-western Anatolia, exploiting the power vacuum left by the Mongol weakening of the Seljuk Sultanate of Rum. " +
      "His son Orhan captured Bursa in 1326 and made it the capital. " +
      "The early Ottomans relied on a flexible mix of Turkmen ghazi warriors, Christian renegades, and timar (land-grant) cavalry — there was no nation-state in the modern sense.",
  },
  {
    id: "m-5",
    topicId: "22222222-2222-2222-2222-222222222222",
    title: "Lecture notes — Mehmed II",
    contentType: "text/plain",
    sizeBytes: 2_104,
    status: "READY",
    chunkCount: 38,
    createdAt: "2026-05-12T15:55:00.000Z",
    updatedAt: "2026-05-12T16:00:00.000Z",
    body:
      "Mehmed II (1432–1481) — 'the Conqueror' — took Constantinople in 1453 at age 21. " +
      "He reorganised the state around himself as autocrat, codifying the kanun alongside sharia. " +
      "Brought Greek scholars to his court and commissioned Gentile Bellini to paint his portrait.",
  },
  {
    id: "m-6",
    topicId: "33333333-3333-3333-3333-333333333333",
    title: "On the mother sauces (and why béchamel is not one of them)",
    originalFilename: "italian-cooking-theory.pdf",
    contentType: "application/pdf",
    sizeBytes: 612_000,
    status: "READY",
    chunkCount: 24,
    createdAt: "2026-05-09T08:05:00.000Z",
    updatedAt: "2026-05-09T08:07:00.000Z",
    body:
      "In Italian tradition the canonical sauces are sugo (tomato), ragù (meat-based long-cook), and a handful of regional bases like pesto and carbonara. " +
      "Béchamel is borrowed from French haute cuisine and is rarely used in Italian cooking outside of lasagne alla bolognese. " +
      "Emulsions: carbonara depends on the starch in pasta water to keep egg yolk from scrambling — never use cream.",
  },
]

const SEED_PLAN: LearningPlan = {
  id: "lp-1",
  goal: "Pass the AWS Solutions Architect Associate exam",
  targetDate: "2026-06-30",
  status: "ACTIVE",
  replanNeeded: false,
  createdAt: "2026-05-18T08:00:00.000Z",
  daysTotal: 4,
  daysCompleted: 1,
  steps: [
    {
      dayIndex: 1,
      scheduledDate: "2026-05-19",
      title: "Reliability pillar — failure modes",
      activity: "READ",
      materials: [
        {
          id: "m-1",
          title: "Reliability & failure modes",
          topicId: "11111111-1111-1111-1111-111111111111",
        },
      ],
      note: "Skim the failure-mode taxonomy first.",
      done: true,
      completedAt: "2026-05-19T18:00:00.000Z",
    },
    {
      dayIndex: 2,
      scheduledDate: "2026-05-20",
      title: "Replication patterns",
      activity: "READ",
      materials: [
        {
          id: "m-2",
          title: "Replication patterns",
          topicId: "11111111-1111-1111-1111-111111111111",
        },
      ],
      note: null,
      done: false,
      completedAt: null,
    },
    {
      dayIndex: 3,
      scheduledDate: "2026-05-21",
      title: "Observability — recap your notes",
      activity: "REVIEW",
      materials: [
        {
          id: "m-3",
          title: "Observability basics",
          topicId: "11111111-1111-1111-1111-111111111111",
        },
      ],
      note: null,
      done: false,
      completedAt: null,
    },
    {
      dayIndex: 4,
      scheduledDate: "2026-05-22",
      title: "Mixed quiz — Cloud Architecture",
      activity: "QUIZ",
      materials: [],
      note: "5 questions across the week's topics.",
      done: false,
      completedAt: null,
    },
  ],
}

const SEED_GOALS: Goal[] = [
  {
    id: "g-1",
    title: "Pass the AWS Solutions Architect Associate exam",
    description:
      "Steady prep across reliability, data-intensive design and observability before the exam date.",
    targetDate: "2026-06-30",
    status: "ACTIVE",
    progress: 42,
    daysTotal: 42,
    daysCompleted: 18,
    topicIds: ["11111111-1111-1111-1111-111111111111"],
    nextAction: "Re-read DDIA Ch. 5 — multi-leader conflicts",
    createdAt: "2026-05-04T10:00:00.000Z",
    updatedAt: "2026-05-18T08:00:00.000Z",
  },
  {
    id: "g-2",
    title: "Prepare for the Ottoman History entrance exam",
    description:
      "From Osman I to Mehmed II — cover the lecture notes and the Cambridge chapter before October.",
    targetDate: "2026-10-15",
    status: "ACTIVE",
    progress: 28,
    daysTotal: 60,
    daysCompleted: 17,
    topicIds: ["22222222-2222-2222-2222-222222222222"],
    nextAction: "Recap Beyliks of Anatolia (c. 1280–1300)",
    createdAt: "2026-04-21T09:15:00.000Z",
    updatedAt: "2026-05-15T10:30:00.000Z",
  },
  {
    id: "g-3",
    title: "Understand the theory of Italian cooking",
    description:
      "Sauce mother-recipes, regional pasta shapes and emulsion chemistry — a personal hobby goal.",
    targetDate: null,
    status: "ACTIVE",
    progress: 60,
    daysTotal: 20,
    daysCompleted: 12,
    topicIds: ["33333333-3333-3333-3333-333333333333"],
    nextAction: "Quiz yourself on emulsions — carbonara vs alfredo",
    createdAt: "2026-05-09T08:00:00.000Z",
    updatedAt: "2026-05-18T11:30:00.000Z",
  },
]

function buildWeekActivity(): boolean[] {
  const jsDay = new Date().getDay()
  const todayIdx = jsDay === 0 ? 6 : jsDay - 1
  return Array.from({ length: 7 }, (_, i) => i <= todayIdx)
}

const SEED_STATS: Stats = {
  sessionsCompleted: 24,
  topicsCount: 3,
  materialsCount: 6,
  streakDays: 8,
  currentLevel: "Steady",
  knowledgeScore: 72,
  weeklyMinutes: 215,
  weekActivity: buildWeekActivity(),
  knowledgeHistory: [
    { date: "2026-04-22", score: 38 },
    { date: "2026-04-29", score: 47 },
    { date: "2026-05-06", score: 55 },
    { date: "2026-05-13", score: 64 },
    { date: "2026-05-18", score: 72 },
  ],
  perTopic: [
    {
      topicId: "11111111-1111-1111-1111-111111111111",
      topicName: "Cloud Architecture",
      score: 78,
    },
    {
      topicId: "22222222-2222-2222-2222-222222222222",
      topicName: "Ottoman History",
      score: 64,
    },
    {
      topicId: "33333333-3333-3333-3333-333333333333",
      topicName: "Italian Cooking Theory",
      score: 81,
    },
  ],
}

const SEED_GAPS: GapsReport = {
  generatedAt: "2026-05-18T08:30:00.000Z",
  summary:
    "You are strong on read-your-writes guarantees and on the cost trade-offs of multi-region designs, but weak on conflict resolution in multi-leader replication and on Anatolian beyliks before 1300.",
  strong: [
    {
      label: "AWS Reliability — design principles",
      topicId: "11111111-1111-1111-1111-111111111111",
      score: 0.86,
    },
    {
      label: "Pasta water as emulsion stabiliser",
      topicId: "33333333-3333-3333-3333-333333333333",
      score: 0.93,
    },
  ],
  weak: [
    {
      label: "Multi-leader conflict resolution",
      topicId: "11111111-1111-1111-1111-111111111111",
      score: 0.41,
      materialId: "m-2",
    },
    {
      label: "Beyliks of Anatolia before Osman I",
      topicId: "22222222-2222-2222-2222-222222222222",
      score: 0.38,
      materialId: "m-4",
    },
    {
      label: "Differences between kanun and sharia",
      topicId: "22222222-2222-2222-2222-222222222222",
      score: 0.49,
      materialId: "m-5",
    },
  ],
  recommendations: [
    {
      materialId: "m-2",
      topicId: "11111111-1111-1111-1111-111111111111",
      reason:
        "Re-read DDIA Ch. 5 §5.4 — Multi-leader replication and conflict resolution.",
    },
    {
      materialId: "m-4",
      topicId: "22222222-2222-2222-2222-222222222222",
      reason:
        "Reread the section on the Anatolian beyliks (c. 1280–1300) — the political vacuum that let Osman I emerge.",
    },
  ],
}

const SEED_SUBSCRIPTIONS: Subscription[] = []
const SEED_PAYMENTS: Payment[] = []

type TelegramState = {
  id: string | null
  telegramUsername: string | null
  chatId: number | null
  linkedAt: string | null
  linkCode: string | null
  linkCodeExpiresAt: string | null
  autoLinkAt: string | null
}

function createTelegramState(): TelegramState {
  return {
    id: null,
    telegramUsername: null,
    chatId: null,
    linkedAt: null,
    linkCode: null,
    linkCodeExpiresAt: null,
    autoLinkAt: null,
  }
}

type DbState = {
  user: ProfileMe
  topics: Topic[]
  materials: Material[]
  quizSessions: QuizSession[]
  quizQuestions: QuizQuestion[]
  plan: LearningPlan
  goals: Goal[]
  stats: Stats
  gaps: GapsReport
  subscriptions: Subscription[]
  payments: Payment[]
  telegram: TelegramState
  refreshTokens: Map<string, { userId: string; expiresAt: string }>
}

function createInitialState(): DbState {
  return {
    user: { ...DEMO_USER },
    topics: SEED_TOPICS.map((t) => ({ ...t })),
    materials: SEED_MATERIALS.map((m) => ({ ...m })),
    quizSessions: [],
    quizQuestions: [],
    plan: structuredClone(SEED_PLAN),
    goals: SEED_GOALS.map((g) => ({ ...g, topicIds: [...g.topicIds] })),
    stats: structuredClone(SEED_STATS),
    gaps: structuredClone(SEED_GAPS),
    subscriptions: SEED_SUBSCRIPTIONS.map((s) => ({ ...s })),
    payments: SEED_PAYMENTS.map((p) => ({ ...p })),
    telegram: createTelegramState(),
    refreshTokens: new Map(),
  }
}

const globalKey = Symbol.for("ilmai.mocks.db.v1")
type GlobalWithDb = typeof globalThis & { [key: symbol]: DbState | undefined }
const globalScope = globalThis as GlobalWithDb

if (!globalScope[globalKey]) {
  globalScope[globalKey] = createInitialState()
}

export const db: DbState = globalScope[globalKey]!

export function resetDb(): void {
  globalScope[globalKey] = createInitialState()
  Object.assign(db, globalScope[globalKey]!)
}

export function newId(): string {
  return uuid()
}

export function timestamp(): string {
  return now()
}

export function findTopic(topicId: string): Topic | undefined {
  return db.topics.find((t) => t.id === topicId)
}

export function findMaterial(materialId: string): Material | undefined {
  return db.materials.find((m) => m.id === materialId)
}

export function rebuildTopicCounts(topicId: string | undefined): void {
  if (!topicId) return
  const topic = findTopic(topicId)
  if (!topic) return
  const mats = db.materials.filter((m) => m.topicId === topicId)
  topic.materialCount = mats.length
  topic.chunkCount = mats.reduce((acc, m) => acc + (m.chunkCount ?? 0), 0)
  topic.updatedAt = now()
}

export type { DbState }
export { DEMO_USER }
