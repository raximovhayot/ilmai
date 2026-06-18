import { http, HttpResponse, delay } from "msw"

import {
  DEMO_USER,
  db,
  findMaterial,
  findTopic,
  newId,
  rebuildTopicCounts,
  resetDb,
  timestamp,
} from "./db"
import type {
  ChatMessage,
  Citation,
  Material,
  QuizDifficulty,
  QuizQuestion,
  QuizQuestionType,
  QuizSession,
  Subscription,
  Topic,
} from "./types"

const BASE = process.env.NEXT_PUBLIC_API_BASE_URL || "http://localhost:8080"

const chatSessions: {
  id: string
  channel: string
  title: string | null
  createdAt: string
  updatedAt: string
}[] = []

const quizCardAnswers: Record<string, string> = {}

function sseFrame(part: unknown): Uint8Array {
  return new TextEncoder().encode(`data:${JSON.stringify(part)}\n\n`)
}

function buildGapsReport() {
  const ready = db.materials.filter((m) => m.status === "READY")
  const m0 = ready[0]
  const m1 = ready[1] ?? ready[0]
  const now = timestamp()
  return {
    generatedAt: now,
    totalQuestionsAnswered: 16,
    correctCount: 9,
    overallAccuracy: 9 / 16,
    summary:
      "You're solid on the fundamentals, but reliability and cost topics still need review.",
    gaps: [
      {
        id: newId(),
        concept: "Reliability patterns",
        missCount: 4,
        hitCount: 1,
        accuracy: 0.2,
        lastSeenAt: now,
        suggestedMaterialId: m0?.id ?? null,
        suggestedMaterialName: m0?.title ?? null,
        trend: "WORSENING",
      },
      {
        id: newId(),
        concept: "Cost optimization",
        missCount: 3,
        hitCount: 2,
        accuracy: 0.4,
        lastSeenAt: now,
        suggestedMaterialId: m1?.id ?? null,
        suggestedMaterialName: m1?.title ?? null,
        trend: "STABLE",
      },
    ],
    strengths: [
      {
        id: newId(),
        concept: "Security basics",
        missCount: 0,
        hitCount: 6,
        accuracy: 1,
        lastSeenAt: now,
        suggestedMaterialId: null,
        suggestedMaterialName: null,
        trend: "IMPROVING",
      },
    ],
    recommendedNext:
      "Review reliability patterns, then take a short quiz to lock it in.",
  }
}

function buildProfile() {
  return {
    userId: DEMO_USER.id,
    locale: "uz",
    timezone: "Asia/Tashkent",
    goal: db.plan.goal ?? null,
    targetDate: db.plan.targetDate ?? null,
    dailyReminder: "20:00:00",
    dailyStudyMinutes: 30,
    sessionsCount: db.stats.sessionsCompleted,
    quizCount: db.quizSessions.length,
    streakDays: db.stats.streakDays,
    lastActiveAt: timestamp(),
  }
}

const TELEGRAM_BOT_USERNAME = "IlmAiTutorBot"

function telegramResponse() {
  const tg = db.telegram
  return {
    id: tg.id,
    telegramUsername: tg.telegramUsername,
    chatId: tg.chatId,
    linkedAt: tg.linkedAt,
    linkCode: tg.linkCode,
    linkCodeExpiresAt: tg.linkCodeExpiresAt,
    botUsername: TELEGRAM_BOT_USERNAME,
  }
}

function maybeAutoLinkTelegram() {
  const tg = db.telegram
  if (tg.linkedAt || !tg.linkCode || !tg.autoLinkAt) return
  if (Date.now() < new Date(tg.autoLinkAt).getTime()) return
  tg.linkedAt = timestamp()
  tg.id = newId()
  tg.chatId = 100_000_000 + Math.floor(Math.random() * 900_000_000)
  tg.telegramUsername = "demo_learner"
  tg.linkCode = null
  tg.linkCodeExpiresAt = null
  tg.autoLinkAt = null
}

const FREE_DAILY_QUIZZES = 3

function currentTier(): "FREE" | "PREMIUM" {
  return db.subscriptions.some(
    (s) => s.status === "ACTIVE" && s.plan !== "FREE"
  )
    ? "PREMIUM"
    : "FREE"
}

function quizzesStartedToday(): number {
  const today = new Date().toISOString().slice(0, 10)
  return db.quizSessions.filter((s) => s.createdAt.slice(0, 10) === today)
    .length
}

function asQuizQuestionResponse(q: QuizQuestion) {
  const reveal = q.userAnswer != null
  return {
    id: q.id,
    position: q.index + 1,
    type: q.type,
    concept: q.citation?.materialName ?? null,
    prompt: q.prompt,
    options: q.choices ?? null,
    materialId: q.citation?.materialId ?? null,
    materialName: q.citation?.materialName ?? null,
    chunkIndex: null,
    userAnswer: q.userAnswer ?? null,
    isCorrect: q.isCorrect ?? null,
    correctAnswer: reveal ? (q.correctAnswer ?? null) : null,
    explanation: reveal ? (q.feedback ?? null) : null,
    feedback: reveal ? (q.feedback ?? null) : null,
  }
}

function asQuizSessionResponse(s: QuizSession, withQuestions: boolean) {
  const base = {
    id: s.id,
    topicId: s.topicId,
    difficulty: s.difficulty,
    locale: null,
    status: s.status,
    score: s.totalQuestions > 0 ? s.correctCount / s.totalQuestions : 0,
    correctCount: s.correctCount,
    totalCount: s.totalQuestions,
    startedAt: s.createdAt,
    completedAt: s.completedAt ?? null,
  }
  if (!withQuestions) return base
  return {
    ...base,
    questions: db.quizQuestions
      .filter((q) => q.sessionId === s.id)
      .sort((a, b) => a.index - b.index)
      .map(asQuizQuestionResponse),
  }
}

function envelope<T>(data: T, status = 200) {
  return HttpResponse.json({ data, errors: [] }, { status })
}

function errorResponse(code: string, message: string, status: number) {
  return HttpResponse.json(
    {
      data: null,
      errors: [
        {
          code,
          field: null,
          message,
          messageKey: code.toLowerCase(),
        },
      ],
    },
    { status }
  )
}

function bearerToken(request: Request): string | null {
  const header =
    request.headers.get("authorization") || request.headers.get("Authorization")
  if (!header) return null
  const match = /^Bearer\s+(.+)$/i.exec(header)
  return match ? match[1]!.trim() : null
}

function requireAuth(request: Request) {
  const token = bearerToken(request)
  if (!token) {
    return errorResponse("AUTH_UNAUTHENTICATED", "Sign in to continue.", 401)
  }
  return { token }
}

function asTopicResponse(topic: Topic) {
  return {
    id: topic.id,
    spaceId: topic.spaceId,
    name: topic.name,
    createdAt: topic.createdAt,
    updatedAt: topic.updatedAt,
  }
}

function enrichPlan(plan: typeof db.plan): typeof db.plan {
  return {
    ...plan,
    steps: plan.steps.map((s) => ({
      ...s,
      materials: s.materials.map((m) => ({
        ...m,
        title: m.title ?? findMaterial(m.id)?.title ?? null,
      })),
    })),
  }
}

function asMaterialResponse(material: Material) {
  return {
    id: material.id,
    topicId: material.topicId,
    title: material.title,
    contentType: material.contentType ?? null,
    sizeBytes: material.sizeBytes ?? null,
    status: material.status,
    retryCount: 0,
    createdAt: material.createdAt,
    updatedAt: material.updatedAt,
  }
}

function tokenizeQuery(query: string): string[] {
  return query
    .toLowerCase()
    .replace(/[^a-zа-я0-9ʻʼ' ]/giu, " ")
    .split(/\s+/)
    .filter((w) => w.length > 2)
}

function similarityForMaterial(
  material: Material,
  queryTokens: string[]
): number {
  const body = material.body.toLowerCase() + " " + material.title.toLowerCase()
  let score = 0
  for (const token of queryTokens) {
    if (body.includes(token)) score += 1
  }
  return queryTokens.length === 0 ? 0 : score / queryTokens.length
}

function pickCitations(topicId: string, query: string, limit = 3): Citation[] {
  const tokens = tokenizeQuery(query)
  const candidates = db.materials.filter(
    (m) => m.topicId === topicId && m.status === "READY"
  )
  const scored = candidates
    .map((m) => ({ material: m, score: similarityForMaterial(m, tokens) }))
    .filter((s) => s.score > 0)
    .sort((a, b) => b.score - a.score)
    .slice(0, limit)

  return scored.map((s, idx) => {
    const sentences = s.material.body
      .split(/(?<=[.!?])\s+/)
      .filter((sentence) => {
        const lower = sentence.toLowerCase()
        return tokens.some((token) => lower.includes(token))
      })
    const preview = (sentences[0] ?? s.material.body).slice(0, 220)
    return {
      materialId: s.material.id,
      materialName: s.material.title,
      chunkIndex: idx,
      preview,
      score: Math.min(0.95, 0.6 + s.score * 0.35),
    }
  })
}

function detectLanguage(message: string): "uz" | "ru" | "en" {
  if (/[\u0400-\u04FF]/.test(message)) return "ru"
  if (/[ʻʼ]/.test(message)) return "uz"
  if (/\b(salom|qanday|nima|kerak|materiallarim|oʻzbek|qiyin)\b/i.test(message))
    return "uz"
  return "en"
}

const NO_GROUNDED = {
  en: "I couldn't find that in your materials yet. Try uploading a source that covers it, or rephrase your question.",
  ru: "Я не нашёл этого в ваших материалах. Попробуйте загрузить источник по этой теме или переформулировать вопрос.",
  uz: "Bunga oid maʼlumot materiallaringizda topilmadi. Mavzuga oid manba yuklang yoki savolni qaytadan yozing.",
}

function craftAnswer(
  topic: Topic,
  _question: string,
  citations: Citation[],
  lang: "uz" | "ru" | "en"
): string {
  if (citations.length === 0) return NO_GROUNDED[lang]

  const opener: Record<typeof lang, string> = {
    en: "Good question.",
    ru: "Хороший вопрос.",
    uz: "Yaxshi savol.",
  }

  const intro: Record<typeof lang, string> = {
    en: `Here's what your "${topic.name}" notes say:`,
    ru: `Вот что говорят ваши материалы из «${topic.name}»:`,
    uz: `"${topic.name}" mavzusidagi materiallaringiz shuni aytadi:`,
  }

  const evidence = citations.map((c, i) => `${i + 1}. ${c.preview}`).join("\n")

  const followUps: Record<typeof lang, string[]> = {
    en: [
      "What's your own guess at why that's true?",
      "Want me to quiz you on this in 3 questions?",
      "Which part of that is still unclear?",
    ],
    ru: [
      "А как вы сами думаете, почему так?",
      "Хотите, я задам 3 вопроса по этому?",
      "Что из этого пока непонятно?",
    ],
    uz: [
      "O‘zingizcha, nima sababdan shunday deb o‘ylaysiz?",
      "Shu bo‘yicha 3 ta savol berishimni xohlaysizmi?",
      "Bu yerda qaysi qism hali tushunarsiz?",
    ],
  }
  const follow =
    followUps[lang][citations.length % followUps[lang].length] ?? ""

  return `${opener[lang]} ${intro[lang]}\n\n${evidence}\n\n${follow}`
}

function craftQuestion(
  material: Material,
  index: number,
  difficulty: QuizDifficulty
): QuizQuestion {
  const sentences = material.body
    .split(/(?<=[.!?])\s+/)
    .map((s) => s.trim())
    .filter((s) => s.length > 30)
  const fact = sentences[index % Math.max(sentences.length, 1)] ?? material.body
  const noun =
    fact
      .split(/\s+/)
      .find((w) => w.length > 6 && /^[A-Za-zА-Яа-яʻʼ]+$/.test(w)) || "concept"

  const types: QuizQuestionType[] =
    difficulty === "EASY"
      ? ["MCQ"]
      : difficulty === "MEDIUM"
        ? ["MCQ", "SHORT_ANSWER"]
        : ["MCQ", "SHORT_ANSWER", "OPEN"]
  const type = types[index % types.length]!

  const sessionPlaceholder = "session-placeholder"
  const citation: Citation = {
    materialId: material.id,
    materialName: material.title,
    chunkIndex: index,
    preview: fact.slice(0, 220),
    score: 0.84,
  }

  if (type === "SHORT_ANSWER") {
    return {
      id: newId(),
      sessionId: sessionPlaceholder,
      index,
      type,
      prompt: `In one sentence, what does the source say about "${noun}"?`,
      correctAnswer: fact,
      hint: `Look at the section that mentions ${noun}.`,
      citation,
    }
  }

  if (type === "OPEN") {
    return {
      id: newId(),
      sessionId: sessionPlaceholder,
      index,
      type,
      prompt: `In your own words, explain what the source says about "${noun}" in the context of ${material.title}.`,
      correctAnswer: fact,
      hint: "Look at the section that mentions " + noun + ".",
      citation,
    }
  }

  const correct = fact.length > 120 ? fact.slice(0, 110) + "…" : fact
  const choices = shuffle([
    correct,
    "It is unrelated to the topic.",
    "It applies only outside of production.",
    "It was deprecated in favour of a newer approach.",
  ])
  return {
    id: newId(),
    sessionId: sessionPlaceholder,
    index,
    type: "MCQ",
    prompt: `Which statement best matches what your source says about "${noun}"?`,
    choices,
    correctAnswer: correct,
    citation,
  }
}

function shuffle<T>(arr: T[]): T[] {
  const a = [...arr]
  for (let i = a.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1))
    ;[a[i], a[j]] = [a[j]!, a[i]!]
  }
  return a
}

export const handlers = [
  http.post(`${BASE}/auth/google`, async ({ request }) => {
    await delay(150)
    const body = (await request.json().catch(() => ({}))) as {
      idToken?: string
    }
    if (!body?.idToken) {
      return errorResponse("AUTH_BAD_REQUEST", "Missing idToken.", 400)
    }
    const refreshToken = newId()
    db.refreshTokens.set(refreshToken, {
      userId: DEMO_USER.id,
      expiresAt: new Date(Date.now() + 30 * 86_400_000).toISOString(),
    })
    return envelope({
      accessToken: "demo-access-token",
      refreshToken,
      tokenType: "Bearer",
      accessExpiresAt: new Date(Date.now() + 3600_000).toISOString(),
      refreshExpiresAt: new Date(Date.now() + 30 * 86_400_000).toISOString(),
    })
  }),

  http.post(`${BASE}/auth/refresh`, async ({ request }) => {
    await delay(80)
    const body = (await request.json().catch(() => ({}))) as {
      refreshToken?: string
    }
    if (!body?.refreshToken) {
      return errorResponse(
        "AUTH_REFRESH_INVALID",
        "Refresh token required.",
        400
      )
    }
    db.refreshTokens.delete(body.refreshToken)
    const next = newId()
    db.refreshTokens.set(next, {
      userId: DEMO_USER.id,
      expiresAt: new Date(Date.now() + 30 * 86_400_000).toISOString(),
    })
    return envelope({
      accessToken: "demo-access-token",
      refreshToken: next,
      tokenType: "Bearer",
      accessExpiresAt: new Date(Date.now() + 3600_000).toISOString(),
      refreshExpiresAt: new Date(Date.now() + 30 * 86_400_000).toISOString(),
    })
  }),

  http.get(`${BASE}/auth/me`, async ({ request }) => {
    const guard = requireAuth(request)
    if (guard instanceof HttpResponse) return guard
    return envelope({
      id: db.user.id,
      username: db.user.username,
      status: db.user.status,
      createdAt: db.user.createdAt,
    })
  }),

  http.get(`${BASE}/topics`, async ({ request }) => {
    const guard = requireAuth(request)
    if (guard instanceof HttpResponse) return guard
    await delay(50)
    return envelope(
      db.topics
        .slice()
        .sort((a, b) => b.updatedAt.localeCompare(a.updatedAt))
        .map(asTopicResponse)
    )
  }),

  http.post(`${BASE}/topics`, async ({ request }) => {
    const guard = requireAuth(request)
    if (guard instanceof HttpResponse) return guard
    const body = (await request.json().catch(() => ({}))) as {
      name?: string
      description?: string
    }
    const name = (body?.name || "").trim()
    if (!name)
      return errorResponse("TOPIC_NAME_BLANK", "Topic name is required.", 400)
    const taken = db.topics.find(
      (t) => t.name.toLowerCase() === name.toLowerCase()
    )
    if (taken)
      return errorResponse(
        "TOPIC_NAME_TAKEN",
        "You already have a topic with that name.",
        409
      )
    const topic: Topic = {
      id: newId(),
      spaceId: "space-1",
      name,
      description: body?.description?.trim() || undefined,
      createdAt: timestamp(),
      updatedAt: timestamp(),
      materialCount: 0,
      chunkCount: 0,
    }
    db.topics.push(topic)
    return envelope(asTopicResponse(topic))
  }),

  http.patch(`${BASE}/topics/:id`, async ({ request, params }) => {
    const guard = requireAuth(request)
    if (guard instanceof HttpResponse) return guard
    const topic = findTopic(params.id as string)
    if (!topic) return errorResponse("TOPIC_NOT_FOUND", "Topic not found.", 404)
    const body = (await request.json().catch(() => ({}))) as {
      name?: string
      description?: string
    }
    if (body.name !== undefined) {
      const name = body.name.trim()
      if (!name)
        return errorResponse("TOPIC_NAME_BLANK", "Topic name is required.", 400)
      if (
        db.topics.some(
          (t) =>
            t.id !== topic.id && t.name.toLowerCase() === name.toLowerCase()
        )
      ) {
        return errorResponse(
          "TOPIC_NAME_TAKEN",
          "You already have a topic with that name.",
          409
        )
      }
      topic.name = name
    }
    if (body.description !== undefined) {
      topic.description = body.description.trim() || undefined
    }
    topic.updatedAt = timestamp()
    return envelope(asTopicResponse(topic))
  }),

  http.delete(`${BASE}/topics/:id`, async ({ request, params }) => {
    const guard = requireAuth(request)
    if (guard instanceof HttpResponse) return guard
    const idx = db.topics.findIndex((t) => t.id === params.id)
    if (idx === -1)
      return errorResponse("TOPIC_NOT_FOUND", "Topic not found.", 404)
    const hasMaterials = db.materials.some((m) => m.topicId === params.id)
    if (hasMaterials)
      return errorResponse(
        "TOPIC_NOT_EMPTY",
        "Delete the materials inside this topic first.",
        409
      )
    db.topics.splice(idx, 1)
    return envelope({ ok: true })
  }),

  http.get(`${BASE}/spaces`, async ({ request }) => {
    const guard = requireAuth(request)
    if (guard instanceof HttpResponse) return guard
    return envelope([{ id: "space-1", name: "My space" }])
  }),

  http.get(`${BASE}/materials`, async ({ request }) => {
    const guard = requireAuth(request)
    if (guard instanceof HttpResponse) return guard
    const url = new URL(request.url)
    const topicId = url.searchParams.get("topicId")
    if (topicId) {
      const topic = findTopic(topicId)
      if (!topic)
        return errorResponse(
          "MATERIAL_TOPIC_NOT_FOUND",
          "Topic not found.",
          404
        )
    }
    const mats = db.materials
      .filter((m) => (topicId ? m.topicId === topicId : true))
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
      .map(asMaterialResponse)
    return envelope(mats)
  }),

  http.get(`${BASE}/materials/contents`, async ({ request }) => {
    const guard = requireAuth(request)
    if (guard instanceof HttpResponse) return guard
    const url = new URL(request.url)
    const page = Math.max(0, Number(url.searchParams.get("page") ?? "0") || 0)
    const size = Math.min(
      100,
      Math.max(1, Number(url.searchParams.get("size") ?? "24") || 24)
    )
    const rootItems = db.materials
      .filter((m) => !m.topicId)
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
    const start = page * size
    const slice = rootItems.slice(start, start + size)
    const topics =
      page === 0
        ? db.topics
            .slice()
            .sort((a, b) => a.createdAt.localeCompare(b.createdAt))
            .map(asTopicResponse)
        : []
    return envelope({
      topics,
      items: slice.map(asMaterialResponse),
      page,
      size,
      hasMore: start + size < rootItems.length,
    })
  }),

  http.get(`${BASE}/materials/:id`, async ({ request, params }) => {
    const guard = requireAuth(request)
    if (guard instanceof HttpResponse) return guard
    const mat = findMaterial(params.id as string)
    if (!mat)
      return errorResponse("MATERIAL_NOT_FOUND", "Material not found.", 404)
    return envelope(asMaterialResponse(mat))
  }),

  http.patch(`${BASE}/materials/:id`, async ({ request, params }) => {
    const guard = requireAuth(request)
    if (guard instanceof HttpResponse) return guard
    const mat = findMaterial(params.id as string)
    if (!mat)
      return errorResponse("MATERIAL_NOT_FOUND", "Material not found.", 404)
    const body = (await request.json().catch(() => ({}))) as {
      topicId?: string | null
    }
    const previousTopicId = mat.topicId
    const target = body.topicId ?? null
    if (target) {
      if (!findTopic(target))
        return errorResponse(
          "MATERIAL_TOPIC_NOT_FOUND",
          "Topic not found.",
          404
        )
      mat.topicId = target
    } else {
      mat.topicId = undefined
    }
    mat.updatedAt = timestamp()
    rebuildTopicCounts(previousTopicId)
    rebuildTopicCounts(mat.topicId)
    return envelope(asMaterialResponse(mat))
  }),

  http.post(`${BASE}/materials`, async ({ request }) => {
    const guard = requireAuth(request)
    if (guard instanceof HttpResponse) return guard
    const form = await request.formData()
    const topicId = String(form.get("topicId") || "")
    const titleRaw = form.get("title")
    const title = typeof titleRaw === "string" ? titleRaw.trim() : ""
    const pasted = form.get("pastedText")
    const file = form.get("file")
    if (topicId && !findTopic(topicId))
      return errorResponse("MATERIAL_TOPIC_NOT_FOUND", "Topic not found.", 404)

    let body = ""
    let originalFilename: string | undefined
    let contentType = "text/plain"
    let sizeBytes = 0
    let derivedTitle = title

    if (file instanceof File && file.size > 0) {
      const acceptable = [
        "text/plain",
        "text/markdown",
        "application/pdf",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
      ]
      if (!acceptable.includes(file.type)) {
        return errorResponse(
          "MATERIAL_UNSUPPORTED_TYPE",
          "Only PDF, Word and plain text files are supported in the demo.",
          400
        )
      }
      if (file.size > 20_000_000) {
        return errorResponse(
          "MATERIAL_TOO_LARGE",
          "Files larger than 20 MB are not supported.",
          413
        )
      }
      originalFilename = file.name
      contentType = file.type
      sizeBytes = file.size
      if (file.type.startsWith("text")) {
        body = await file.text()
      } else {
        body =
          `[Binary file ingested in mock mode — ${file.name} (${file.size} bytes)]\n` +
          `In the real backend, Apache Tika would extract text from this ${file.type} document and split it into chunks for vector search.`
      }
      if (!derivedTitle) derivedTitle = file.name.replace(/\.[^.]+$/, "")
    } else if (typeof pasted === "string" && pasted.trim().length > 0) {
      body = pasted.trim()
      sizeBytes = body.length
      if (!derivedTitle)
        derivedTitle =
          body.split(/\s+/).slice(0, 6).join(" ").slice(0, 60) + "…"
    } else {
      return errorResponse(
        "MATERIAL_CONTENT_REQUIRED",
        "Upload a file or paste some text.",
        400
      )
    }

    if (!derivedTitle) {
      return errorResponse("MATERIAL_TITLE_BLANK", "A title is required.", 400)
    }

    const id = newId()
    const material: Material = {
      id,
      topicId: topicId || undefined,
      title: derivedTitle,
      originalFilename,
      contentType,
      sizeBytes,
      status: "PENDING",
      chunkCount: 0,
      createdAt: timestamp(),
      updatedAt: timestamp(),
      body,
    }
    db.materials.push(material)
    rebuildTopicCounts(topicId)

    setTimeout(() => {
      const m = findMaterial(id)
      if (!m) return
      m.status = "PROCESSING"
      m.updatedAt = timestamp()
    }, 700)
    setTimeout(() => {
      const m = findMaterial(id)
      if (!m) return
      m.status = "READY"
      m.chunkCount = Math.max(1, Math.ceil((m.body?.length ?? 200) / 350))
      m.updatedAt = timestamp()
      rebuildTopicCounts(topicId)
    }, 1800)

    return envelope(asMaterialResponse(material), 202)
  }),

  http.delete(`${BASE}/materials/:id`, async ({ request, params }) => {
    const guard = requireAuth(request)
    if (guard instanceof HttpResponse) return guard
    const idx = db.materials.findIndex((m) => m.id === params.id)
    if (idx === -1)
      return errorResponse("MATERIAL_NOT_FOUND", "Material not found.", 404)
    const topicId = db.materials[idx]!.topicId
    db.materials.splice(idx, 1)
    rebuildTopicCounts(topicId)
    return envelope({ ok: true })
  }),

  http.get(`${BASE}/agent/sessions`, async ({ request }) => {
    const guard = requireAuth(request)
    if (guard instanceof HttpResponse) return guard
    const sorted = [...chatSessions].sort((a, b) =>
      b.updatedAt.localeCompare(a.updatedAt)
    )
    return envelope(sorted)
  }),

  http.post(`${BASE}/agent/sessions`, async ({ request }) => {
    const guard = requireAuth(request)
    if (guard instanceof HttpResponse) return guard
    const body = (await request.json().catch(() => ({}))) as {
      title?: string | null
      channel?: string
    }
    const now = timestamp()
    const session = {
      id: newId(),
      channel: body.channel ?? "WEB",
      title: body.title ?? null,
      createdAt: now,
      updatedAt: now,
    }
    chatSessions.unshift(session)
    return envelope(session, 201)
  }),

  http.post(`${BASE}/agent/chat/:sessionId`, async ({ request, params }) => {
    const guard = requireAuth(request)
    if (guard instanceof HttpResponse) return guard
    const sessionId = params.sessionId as string
    const body = (await request.json().catch(() => ({}))) as { prompt?: string }
    const prompt = (body.prompt ?? "").trim()

    const session = chatSessions.find((s) => s.id === sessionId)
    if (session) session.updatedAt = timestamp()

    const material =
      db.materials.find((m) => m.status === "READY") ?? db.materials[0]
    const grounded = Boolean(material && material.status === "READY")
    const quizSessionId = newId()
    const quizQuestions = [
      {
        questionId: newId(),
        position: 1,
        type: "MCQ",
        concept: "Study technique",
        prompt: "Which technique best improves long-term retention?",
        options: [
          "Spaced repetition",
          "Cramming",
          "Highlighting",
          "Re-reading",
        ] as string[] | null,
        materialId: material?.id ?? null,
        materialName: material?.title ?? null,
        chunkIndex: 0,
        answer: "Spaced repetition",
      },
      {
        questionId: newId(),
        position: 2,
        type: "MCQ",
        concept: "Active recall",
        prompt: "What does active recall require you to do?",
        options: [
          "Retrieve answers from memory",
          "Re-read your notes",
          "Listen passively",
          "Copy the text",
        ] as string[] | null,
        materialId: material?.id ?? null,
        materialName: material?.title ?? null,
        chunkIndex: 1,
        answer: "Retrieve answers from memory",
      },
      {
        questionId: newId(),
        position: 3,
        type: "SHORT_ANSWER",
        concept: "Spacing effect",
        prompt: "In one phrase, why does spacing your practice help?",
        options: null as string[] | null,
        materialId: material?.id ?? null,
        materialName: material?.title ?? null,
        chunkIndex: 2,
        answer: "It strengthens memory over time",
      },
    ]
    for (const q of quizQuestions) quizCardAnswers[q.questionId] = q.answer

    const messageId = newId()
    const textId = newId()
    const frames: unknown[] = [
      { type: "start", messageId },
      { type: "text-start", id: textId },
    ]
    if (grounded && material) {
      frames.push(
        { type: "text-delta", id: textId, delta: "Based on your materials, " },
        {
          type: "text-delta",
          id: textId,
          delta: `here's what I found on “${prompt || "your question"}”. `,
        },
        {
          type: "text-delta",
          id: textId,
          delta:
            "Review actively and space your practice over several days for the best recall. ",
        },
        { type: "text-end", id: textId },
        {
          type: "data-citation",
          data: {
            id: newId(),
            materialId: material.id,
            materialName: material.title,
            locator: "p.1",
            snippet: (material.body ?? material.title).slice(0, 140),
            score: 0.82,
          },
        },
        {
          type: "data-quiz",
          data: {
            sessionId: quizSessionId,
            mode: "SINGLE",
            timeLimitSeconds: 0,
            difficulty: "MEDIUM",
            questions: quizQuestions.map((q) => ({
              questionId: q.questionId,
              position: q.position,
              type: q.type,
              concept: q.concept,
              prompt: q.prompt,
              options: q.options,
              materialId: q.materialId,
              materialName: q.materialName,
              chunkIndex: q.chunkIndex,
            })),
          },
        },
        {
          type: "data-action",
          data: {
            action: "open_quiz",
            label: "Quiz me on this",
            payload: {},
          },
        }
      )
    } else {
      frames.push(
        {
          type: "text-delta",
          id: textId,
          delta:
            "I couldn't find this in your materials, so here's general guidance. ",
        },
        {
          type: "text-delta",
          id: textId,
          delta: "Upload notes on this topic to get grounded, cited answers.",
        },
        { type: "text-end", id: textId },
        {
          type: "data-confidence",
          data: {
            level: "low",
          },
        }
      )
    }
    frames.push({ type: "finish" })

    const stream = new ReadableStream({
      async start(controller) {
        for (const frame of frames) {
          controller.enqueue(sseFrame(frame))
          await delay(160)
        }
        controller.enqueue(new TextEncoder().encode("data:[DONE]\n\n"))
        controller.close()
      },
    })

    return new HttpResponse(stream, {
      status: 200,
      headers: {
        "Content-Type": "text/event-stream",
        "Cache-Control": "no-cache",
        "x-vercel-ai-ui-message-stream": "v1",
      },
    })
  }),

  http.post(
    `${BASE}/quiz/sessions/:sessionId/questions/:questionId/answer`,
    async ({ request, params }) => {
      const guard = requireAuth(request)
      if (guard instanceof HttpResponse) return guard
      const sessionId = params.sessionId as string
      const questionId = params.questionId as string
      const body = (await request.json().catch(() => ({}))) as {
        answer?: string
      }
      const answer = (body.answer ?? "").trim()

      const q = db.quizQuestions.find(
        (qq) => qq.id === questionId && qq.sessionId === sessionId
      )
      if (q) {
        const session = db.quizSessions.find((s) => s.id === sessionId)
        let correct = false
        if (q.type === "MCQ") {
          correct = answer === q.correctAnswer
        } else {
          const tokensA = new Set(tokenizeQuery(answer))
          const tokensB = tokenizeQuery(q.correctAnswer)
          let overlap = 0
          for (const tk of tokensB) if (tokensA.has(tk)) overlap++
          const threshold = q.type === "SHORT_ANSWER" ? 0.5 : 0.35
          correct = tokensB.length > 0 && overlap / tokensB.length >= threshold
        }
        const already = q.userAnswer != null
        q.userAnswer = answer
        q.isCorrect = correct
        q.feedback = correct
          ? "Correct — exactly what the source says."
          : `Not quite. The source says: “${q.correctAnswer}”`
        if (session && correct && !already) session.correctCount++
        if (session) {
          const answered = db.quizQuestions.filter(
            (qq) => qq.sessionId === session.id && qq.userAnswer != null
          )
          if (answered.length === session.totalQuestions) {
            session.status = "COMPLETED"
            session.completedAt = timestamp()
            session.score = Math.round(
              (session.correctCount / session.totalQuestions) * 100
            )
          }
        }
        await delay(250)
        return envelope(asQuizQuestionResponse(q))
      }

      const correctAnswer = quizCardAnswers[questionId] ?? "Spaced repetition"
      const isCorrect = answer.toLowerCase() === correctAnswer.toLowerCase()
      return envelope({
        id: questionId,
        position: 1,
        userAnswer: answer,
        isCorrect,
        correctAnswer,
        explanation:
          "Spaced repetition strengthens recall by reviewing material at increasing intervals.",
        feedback: isCorrect
          ? "Correct — well done!"
          : "Review the spacing effect.",
      })
    }
  ),

  http.get(`${BASE}/chat/conversations`, async ({ request }) => {
    const guard = requireAuth(request)
    if (guard instanceof HttpResponse) return guard
    const url = new URL(request.url)
    const topicId = url.searchParams.get("topicId")
    const list = db.conversations
      .filter((c) => (topicId ? c.topicId === topicId : true))
      .sort((a, b) => b.updatedAt.localeCompare(a.updatedAt))
    return envelope(list)
  }),

  http.get(
    `${BASE}/chat/conversations/:id/messages`,
    async ({ request, params }) => {
      const guard = requireAuth(request)
      if (guard instanceof HttpResponse) return guard
      const conv = db.conversations.find((c) => c.id === params.id)
      if (!conv)
        return errorResponse(
          "CHAT_CONVERSATION_NOT_FOUND",
          "Conversation not found.",
          404
        )
      const messages = db.messages
        .filter((m) => m.conversationId === conv.id)
        .sort((a, b) => a.createdAt.localeCompare(b.createdAt))
      return envelope({ conversation: conv, messages })
    }
  ),

  http.post(`${BASE}/chat`, async ({ request }) => {
    const guard = requireAuth(request)
    if (guard instanceof HttpResponse) return guard
    const body = (await request.json().catch(() => ({}))) as {
      topicId?: string
      conversationId?: string
      message?: string
    }
    if (!body.topicId || !body.message?.trim()) {
      return errorResponse(
        "CHAT_BAD_REQUEST",
        "topicId and message are required.",
        400
      )
    }
    const topic = findTopic(body.topicId)
    if (!topic)
      return errorResponse("CHAT_TOPIC_NOT_FOUND", "Topic not found.", 404)

    let conv = body.conversationId
      ? db.conversations.find((c) => c.id === body.conversationId)
      : undefined
    if (!conv) {
      conv = {
        id: newId(),
        topicId: topic.id,
        title: body.message!.trim().slice(0, 60),
        createdAt: timestamp(),
        updatedAt: timestamp(),
      }
      db.conversations.push(conv)
    }

    const userMessage: ChatMessage = {
      id: newId(),
      conversationId: conv.id,
      role: "USER",
      content: body.message!.trim(),
      citations: [],
      createdAt: timestamp(),
    }
    db.messages.push(userMessage)

    await delay(700)

    const citations = pickCitations(topic.id, body.message!)
    const lang = detectLanguage(body.message!)
    const answer = craftAnswer(topic, body.message!, citations, lang)

    const assistantMessage: ChatMessage = {
      id: newId(),
      conversationId: conv.id,
      role: "ASSISTANT",
      content: answer,
      citations,
      createdAt: timestamp(),
    }
    db.messages.push(assistantMessage)
    conv.updatedAt = timestamp()
    conv.lastPreview = answer.slice(0, 120)

    return envelope({
      conversationId: conv.id,
      answer,
      citations,
    })
  }),

  http.post(`${BASE}/quiz/sessions`, async ({ request }) => {
    const guard = requireAuth(request)
    if (guard instanceof HttpResponse) return guard
    const body = (await request.json().catch(() => ({}))) as {
      topicId?: string
      difficulty?: QuizDifficulty
      questionCount?: number
      materialIds?: string[]
    }
    if (!body.topicId)
      return errorResponse("QUIZ_BAD_REQUEST", "topicId is required.", 400)
    const topic = findTopic(body.topicId)
    if (!topic)
      return errorResponse("QUIZ_TOPIC_NOT_FOUND", "Topic not found.", 404)
    const focusSet =
      body.materialIds && body.materialIds.length > 0
        ? new Set(body.materialIds)
        : null
    const materials = db.materials.filter(
      (m) =>
        m.topicId === topic.id &&
        m.status === "READY" &&
        (!focusSet || focusSet.has(m.id))
    )
    if (materials.length === 0) {
      return errorResponse(
        "QUIZ_NO_MATERIALS",
        "Upload a material to this topic before starting a quiz.",
        400
      )
    }
    const difficulty: QuizDifficulty = body.difficulty || "MEDIUM"
    const total = Math.min(Math.max(body.questionCount ?? 5, 3), 10)

    if (
      currentTier() === "FREE" &&
      quizzesStartedToday() >= FREE_DAILY_QUIZZES
    ) {
      return errorResponse(
        "QUIZ_LIMIT_REACHED",
        "Daily free quiz limit reached. Upgrade for unlimited quizzes.",
        402
      )
    }

    const sessionId = newId()
    const session: QuizSession = {
      id: sessionId,
      topicId: topic.id,
      difficulty,
      status: "IN_PROGRESS",
      score: 0,
      totalQuestions: total,
      correctCount: 0,
      createdAt: timestamp(),
    }
    db.quizSessions.push(session)

    for (let i = 0; i < total; i++) {
      const material = materials[i % materials.length]!
      const q = craftQuestion(material, i, difficulty)
      q.sessionId = sessionId
      db.quizQuestions.push(q)
    }

    return envelope(asQuizSessionResponse(session, true))
  }),

  http.get(`${BASE}/quiz/sessions`, async ({ request }) => {
    const guard = requireAuth(request)
    if (guard instanceof HttpResponse) return guard
    const list = db.quizSessions
      .slice()
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
      .map((s) => asQuizSessionResponse(s, false))
    return envelope(list)
  }),

  http.get(`${BASE}/quiz/sessions/:id`, async ({ request, params }) => {
    const guard = requireAuth(request)
    if (guard instanceof HttpResponse) return guard
    const session = db.quizSessions.find((s) => s.id === params.id)
    if (!session)
      return errorResponse("QUIZ_NOT_FOUND", "Quiz session not found.", 404)
    return envelope(asQuizSessionResponse(session, true))
  }),

  http.post(
    `${BASE}/quiz/sessions/:id/abandon`,
    async ({ request, params }) => {
      const guard = requireAuth(request)
      if (guard instanceof HttpResponse) return guard
      const session = db.quizSessions.find((s) => s.id === params.id)
      if (!session)
        return errorResponse("QUIZ_NOT_FOUND", "Quiz session not found.", 404)
      return envelope({
        ...asQuizSessionResponse(session, true),
        status: "ABANDONED",
      })
    }
  ),

  http.get(`${BASE}/gaps`, async ({ request }) => {
    const guard = requireAuth(request)
    if (guard instanceof HttpResponse) return guard
    await delay(300)
    return envelope(buildGapsReport())
  }),

  http.post(`${BASE}/gaps/refresh`, async ({ request }) => {
    const guard = requireAuth(request)
    if (guard instanceof HttpResponse) return guard
    await delay(700)
    return envelope(buildGapsReport())
  }),

  http.get(`${BASE}/plan`, async ({ request }) => {
    const guard = requireAuth(request)
    if (guard instanceof HttpResponse) return guard
    return envelope(enrichPlan(db.plan))
  }),

  http.get(`${BASE}/goals`, async ({ request }) => {
    const guard = requireAuth(request)
    if (guard instanceof HttpResponse) return guard
    return envelope(db.goals)
  }),

  http.get(`${BASE}/onboarding`, async ({ request }) => {
    const guard = requireAuth(request)
    if (guard instanceof HttpResponse) return guard
    return envelope({
      goal: db.plan.goal,
      targetDate: db.plan.targetDate,
      dailyStudyMinutes: null,
      dailyReminder: null,
    })
  }),

  http.put(`${BASE}/onboarding`, async ({ request }) => {
    const guard = requireAuth(request)
    if (guard instanceof HttpResponse) return guard
    const body = (await request.json().catch(() => ({}))) as {
      goal?: string | null
      targetDate?: string | null
      dailyStudyMinutes?: number | null
      dailyReminder?: string | null
    }
    await delay(500)
    if (body.goal !== undefined) db.plan.goal = body.goal
    if (body.targetDate !== undefined) db.plan.targetDate = body.targetDate
    return envelope({
      goal: db.plan.goal,
      targetDate: db.plan.targetDate,
      dailyStudyMinutes: body.dailyStudyMinutes ?? null,
      dailyReminder: body.dailyReminder ?? null,
    })
  }),

  http.post(
    `${BASE}/plan/steps/:dayIndex/complete`,
    async ({ request, params }) => {
      const guard = requireAuth(request)
      if (guard instanceof HttpResponse) return guard
      const dayIndex = Number(params.dayIndex)
      const step = db.plan.steps.find((s) => s.dayIndex === dayIndex)
      if (step && !step.done) {
        step.done = true
        step.completedAt = timestamp()
      }
      db.plan.daysCompleted = db.plan.steps.filter((s) => s.done).length
      return envelope(enrichPlan(db.plan))
    }
  ),

  http.get(`${BASE}/profile`, async ({ request }) => {
    const guard = requireAuth(request)
    if (guard instanceof HttpResponse) return guard
    return envelope(buildProfile())
  }),

  http.put(`${BASE}/profile`, async ({ request }) => {
    const guard = requireAuth(request)
    if (guard instanceof HttpResponse) return guard
    const body = (await request.json().catch(() => ({}))) as {
      goal?: string | null
      targetDate?: string | null
    }
    if (body.goal !== undefined) db.plan.goal = body.goal
    if (body.targetDate !== undefined) db.plan.targetDate = body.targetDate
    return envelope(buildProfile())
  }),

  http.post(`${BASE}/billing/checkout`, async ({ request }) => {
    const guard = requireAuth(request)
    if (guard instanceof HttpResponse) return guard
    const body = (await request.json().catch(() => ({}))) as {
      plan?: string
      provider?: string
    }
    const plan = body.plan
    const provider = body.provider
    if (plan !== "PREMIUM_MONTHLY" && plan !== "PREMIUM_YEARLY") {
      return errorResponse("BILLING_INVALID_PLAN", "Unknown plan.", 400)
    }
    if (
      provider !== "STRIPE" &&
      provider !== "PAYME" &&
      provider !== "CLICK" &&
      provider !== "TEST"
    ) {
      return errorResponse("BILLING_INVALID_PROVIDER", "Unknown provider.", 400)
    }
    await delay(700)
    const start = timestamp()
    const periodDays = plan === "PREMIUM_YEARLY" ? 365 : 30
    const end = new Date(Date.now() + periodDays * 86_400_000).toISOString()
    for (const s of db.subscriptions) {
      if (s.status === "ACTIVE" || s.status === "PENDING") s.status = "EXPIRED"
    }
    const subscription: Subscription = {
      id: newId(),
      plan,
      status: "ACTIVE",
      provider,
      currentPeriodStart: start,
      currentPeriodEnd: end,
      cancelAtPeriodEnd: false,
    }
    db.subscriptions.unshift(subscription)
    const usd = provider === "STRIPE" || provider === "TEST"
    const amountMinor = usd
      ? plan === "PREMIUM_YEARLY"
        ? 4990
        : 499
      : plan === "PREMIUM_YEARLY"
        ? 39_000_000
        : 3_900_000
    db.payments.unshift({
      id: newId(),
      provider,
      externalId: "pi_mock_" + newId().slice(0, 8),
      amountMinor,
      currency: usd ? "USD" : "UZS",
      status: "SUCCEEDED",
      occurredAt: start,
    })
    return envelope({
      provider,
      externalId: "cs_mock_" + newId().slice(0, 8),
      redirectUrl: null,
    })
  }),

  http.get(`${BASE}/billing/subscription`, async ({ request }) => {
    const guard = requireAuth(request)
    if (guard instanceof HttpResponse) return guard
    const active = db.subscriptions.find((s) => s.status === "ACTIVE") ?? null
    return envelope(active)
  }),

  http.get(`${BASE}/billing/subscriptions`, async ({ request }) => {
    const guard = requireAuth(request)
    if (guard instanceof HttpResponse) return guard
    return envelope(db.subscriptions)
  }),

  http.get(`${BASE}/billing/payments`, async ({ request }) => {
    const guard = requireAuth(request)
    if (guard instanceof HttpResponse) return guard
    return envelope(db.payments)
  }),

  http.delete(`${BASE}/billing/subscription`, async ({ request }) => {
    const guard = requireAuth(request)
    if (guard instanceof HttpResponse) return guard
    const active = db.subscriptions.find((s) => s.status === "ACTIVE")
    if (!active) {
      return errorResponse(
        "BILLING_NO_ACTIVE_SUBSCRIPTION",
        "No active subscription to cancel.",
        404
      )
    }
    active.cancelAtPeriodEnd = true
    return envelope(active)
  }),

  http.post(`${BASE}/telegram/link-code`, async ({ request }) => {
    const guard = requireAuth(request)
    if (guard instanceof HttpResponse) return guard
    if (db.telegram.linkedAt) {
      return errorResponse(
        "TELEGRAM_ALREADY_LINKED",
        "Telegram is already linked.",
        400
      )
    }
    await delay(400)
    const alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    let code = ""
    for (let i = 0; i < 8; i++) {
      code += alphabet[Math.floor(Math.random() * alphabet.length)]
    }
    db.telegram.linkCode = code
    db.telegram.linkCodeExpiresAt = new Date(
      Date.now() + 30 * 60 * 1000
    ).toISOString()
    db.telegram.autoLinkAt = new Date(Date.now() + 6000).toISOString()
    return envelope(telegramResponse())
  }),

  http.get(`${BASE}/telegram`, async ({ request }) => {
    const guard = requireAuth(request)
    if (guard instanceof HttpResponse) return guard
    maybeAutoLinkTelegram()
    if (!db.telegram.linkedAt && !db.telegram.linkCode) {
      return errorResponse(
        "TELEGRAM_NOT_LINKED",
        "Telegram is not linked.",
        404
      )
    }
    return envelope(telegramResponse())
  }),

  http.delete(`${BASE}/telegram`, async ({ request }) => {
    const guard = requireAuth(request)
    if (guard instanceof HttpResponse) return guard
    db.telegram.id = null
    db.telegram.telegramUsername = null
    db.telegram.chatId = null
    db.telegram.linkedAt = null
    db.telegram.linkCode = null
    db.telegram.linkCodeExpiresAt = null
    db.telegram.autoLinkAt = null
    return new HttpResponse(null, { status: 204 })
  }),

  http.post(`${BASE}/demo/reset`, async () => {
    resetDb()
    return envelope({ ok: true })
  }),
]
