import { apiFetch } from "@/lib/api"

export type QuizDifficulty = "EASY" | "MEDIUM" | "HARD"
export type QuizStatus = "IN_PROGRESS" | "COMPLETED" | "ABANDONED"

export type QuizQuestion = {
  id: string
  position: number
  type: string
  concept?: string | null
  prompt: string
  options?: string[] | null
  materialId?: string | null
  materialName?: string | null
  chunkIndex?: number | null
  userAnswer?: string | null
  isCorrect?: boolean | null
  correctAnswer?: string | null
  explanation?: string | null
  feedback?: string | null
}

export type QuizSession = {
  id: string
  topicId?: string | null
  difficulty?: string | null
  locale?: string | null
  status: QuizStatus | string
  score?: number | null
  correctCount: number
  totalCount: number
  startedAt?: string | null
  completedAt?: string | null
  questions?: QuizQuestion[]
}

export type StartQuizBody = {
  topicId: string
  difficulty?: string
  locale?: string
  questionCount?: number
}

export async function startQuizSession(
  body: StartQuizBody
): Promise<QuizSession | null> {
  return await apiFetch<QuizSession>("/quiz/sessions", {
    method: "POST",
    body,
  })
}

export async function listQuizSessions(): Promise<QuizSession[]> {
  const data = await apiFetch<QuizSession[]>("/quiz/sessions", {
    cache: "no-store",
  })
  return data ?? []
}

export async function getQuizSession(
  sessionId: string
): Promise<QuizSession | null> {
  return await apiFetch<QuizSession>(`/quiz/sessions/${sessionId}`, {
    cache: "no-store",
  })
}

export async function answerQuizQuestion(
  sessionId: string,
  questionId: string,
  answer: string
): Promise<QuizQuestion | null> {
  return await apiFetch<QuizQuestion>(
    `/quiz/sessions/${sessionId}/questions/${questionId}/answer`,
    { method: "POST", body: { answer } }
  )
}

export async function abandonQuizSession(
  sessionId: string
): Promise<QuizSession | null> {
  return await apiFetch<QuizSession>(`/quiz/sessions/${sessionId}/abandon`, {
    method: "POST",
  })
}
