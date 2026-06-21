import { PROXY_PREFIX, apiFetch } from "@/lib/api"

export type ChatChannel = "WEB" | "TELEGRAM"

export type ChatSession = {
  id: string
  channel: ChatChannel
  title: string | null
  createdAt: string
  updatedAt: string
}

export type TextConfidence = "HIGH" | "LOW"

export type TextPart = {
  type: "text"
  text: string
  confidence?: TextConfidence
}

export type CitationPart = {
  type: "citation"
  id: string
  materialId: string
  materialName: string
  locator: string
  snippet: string
  score: number
}

export type ToolCallPart = {
  type: "tool_call"
  tool: string
  status: string
  durationMs?: number | null
  error?: string | null
}

export type ActionPart = {
  type: "action"
  action: string
  label: string
  payload?: Record<string, unknown> | null
}

export type QuizCardPart = {
  type: "quiz_card"
  sessionId: string
  questionId: string
  position: number
  concept?: string | null
  prompt: string
  options?: string[] | null
  materialId?: string | null
  materialName?: string | null
  chunkIndex?: number | null
}

export type ErrorPart = {
  type: "error"
  code: string
  message: string
  retryable: boolean
}

export type MessagePart =
  | TextPart
  | CitationPart
  | ToolCallPart
  | ActionPart
  | QuizCardPart
  | ErrorPart

export async function listSessions(): Promise<ChatSession[]> {
  const data = await apiFetch<ChatSession[]>("/agent/sessions", {
    cache: "no-store",
  })
  return data ?? []
}

export async function createSession(
  title?: string,
  channel: ChatChannel = "WEB"
): Promise<ChatSession | null> {
  return await apiFetch<ChatSession>("/agent/sessions", {
    method: "POST",
    body: { title: title ?? null, channel },
  })
}

export type ChatMessageRole = "USER" | "ASSISTANT"

export type ChatMessageCitation = {
  id: string
  materialId: string
  materialName: string
  locator: string | null
  snippet: string
  score: number
}

export type ChatMessage = {
  id: string
  role: ChatMessageRole
  content: string
  citations: ChatMessageCitation[] | null
  lowConfidence: boolean
  createdAt: string
}

export async function getSessionMessages(
  sessionId: string
): Promise<ChatMessage[]> {
  const data = await apiFetch<ChatMessage[]>(
    `/agent/sessions/${sessionId}/messages`,
    { cache: "no-store" }
  )
  return data ?? []
}

export type QuizAnswerResult = {
  id: string
  position: number
  userAnswer?: string | null
  isCorrect?: boolean | null
  correctAnswer?: string | null
  explanation?: string | null
  feedback?: string | null
}

export async function answerQuizCard(
  sessionId: string,
  questionId: string,
  answer: string
): Promise<QuizAnswerResult | null> {
  return await apiFetch<QuizAnswerResult>(
    `/quiz/sessions/${sessionId}/questions/${questionId}/answer`,
    { method: "POST", body: { answer } }
  )
}

import { DefaultChatTransport, type UIMessage } from "ai"

export type CoachDataParts = {
  citation: {
    id: string
    materialId: string
    materialName: string
    locator: string
    snippet: string
    score: number
  }
  quiz: {
    sessionId: string
    mode?: string | null
    timeLimitSeconds?: number | null
    difficulty?: string | null
    questions: Array<{
      questionId: string
      position: number
      type?: string | null
      concept?: string | null
      prompt: string
      options?: string[] | null
      materialId?: string | null
      materialName?: string | null
      chunkIndex?: number | null
    }>
  }
  action: {
    action: string
    label: string
    payload?: Record<string, unknown> | null
  }
  confidence: {
    level: "low" | "high"
  }
}

export type CoachUIMessage = UIMessage<never, CoachDataParts>

export function messageText(message: CoachUIMessage): string {
  return message.parts
    .filter((part) => part.type === "text")
    .map((part) => part.text)
    .join("")
}

export function historyToCoachMessages(
  history: ChatMessage[]
): CoachUIMessage[] {
  return history.map((message) => {
    const parts: CoachUIMessage["parts"] = []
    if (message.content) {
      parts.push({ type: "text", text: message.content })
    }
    for (const citation of message.citations ?? []) {
      parts.push({
        type: "data-citation",
        data: {
          id: citation.id,
          materialId: citation.materialId,
          materialName: citation.materialName,
          locator: citation.locator ?? "",
          snippet: citation.snippet,
          score: citation.score,
        },
      })
    }
    if (message.role === "ASSISTANT" && message.lowConfidence) {
      parts.push({ type: "data-confidence", data: { level: "low" } })
    }
    return {
      id: message.id,
      role: message.role === "USER" ? "user" : "assistant",
      parts,
    }
  })
}

export function createCoachTransport(sessionId: string) {
  return new DefaultChatTransport<CoachUIMessage>({
    api: `${PROXY_PREFIX}/agent/chat/${sessionId}`,
    prepareSendMessagesRequest: ({ messages }) => {
      const lastUserMessage = [...messages]
        .reverse()
        .find((m) => m.role === "user")
      const prompt = lastUserMessage ? messageText(lastUserMessage) : ""
      return {
        body: {
          prompt,
          channel: "WEB",
        },
      }
    },
  })
}
