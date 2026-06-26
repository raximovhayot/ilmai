import { apiFetch, PROXY_PREFIX } from "@/lib/api"

export type PlanActivity = "READ" | "QUIZ" | "REVIEW" | "INDEPENDENT"

export type LearningPlanItemAction = PlanActivity

export type PlanStatus = "ACTIVE" | "PAUSED" | "COMPLETED" | "SUPERSEDED"

export type GoalStatusUpdate = "ACTIVE" | "PAUSED" | "COMPLETED"

export type PlanMaterial = {
  id: string
  title: string | null
  topicId: string | null
}

export type PlanStep = {
  dayIndex: number
  orderInDay: number
  scheduledDate: string | null
  title: string
  activity: PlanActivity
  materials: PlanMaterial[]
  note: string | null
  reflectionNote: string | null
  done: boolean
  completedAt: string | null
  hasLesson: boolean
  lessonGeneratedAt: string | null
  quizSessionId: string | null
  quizPassed: boolean | null
  quizScore: number | null
}

export type CompleteTaskPayload = {
  reflectionNote?: string
  quizSessionId?: string
}

export type LessonCitation = {
  materialId: string | null
  materialName: string | null
  chunkIndex: number | null
  snippet: string | null
  sourceKind?: string | null
  pageStart?: number | null
  pageEnd?: number | null
  audioStartMs?: number | null
  audioEndMs?: number | null
}

export function rawMaterialUrl(materialId: string): string {
  return `${PROXY_PREFIX}/materials/${materialId}/raw`
}

export function isPdfCitation(citation: LessonCitation): boolean {
  if (!citation.materialId) return false
  return (
    citation.sourceKind === "pdf_range" ||
    typeof citation.pageStart === "number"
  )
}

export function isAudioCitation(citation: LessonCitation): boolean {
  if (!citation.materialId) return false
  return (
    citation.sourceKind === "audio_segment" ||
    typeof citation.audioStartMs === "number"
  )
}

export function formatAudioTimestamp(ms: number): string {
  const totalSeconds = Math.max(0, Math.floor(ms / 1000))
  const seconds = totalSeconds % 60
  const totalMinutes = Math.floor(totalSeconds / 60)
  const minutes = totalMinutes % 60
  const hours = Math.floor(totalMinutes / 60)
  const mm = String(minutes).padStart(2, "0")
  const ss = String(seconds).padStart(2, "0")
  if (hours > 0) {
    return `${hours}:${mm}:${ss}`
  }
  return `${mm}:${ss}`
}

export type StepLesson = {
  dayIndex: number
  title: string
  content: string
  citations: LessonCitation[]
  generatedAt: string | null
}

export type LearningPlan = {
  id: string
  goalId: string | null
  goal: string | null
  targetDate: string | null
  status: PlanStatus
  replanNeeded: boolean
  createdAt: string | null
  daysTotal: number
  daysCompleted: number
  steps: PlanStep[]
}

export async function generatePlan(): Promise<LearningPlan | null> {
  const result = await apiFetch<LearningPlan | null>("/plan/generate", {
    method: "POST",
  })
  return result && result.id ? result : null
}

export async function getPlan(): Promise<LearningPlan | null> {
  const result = await apiFetch<LearningPlan | null>("/plan", {
    cache: "no-store",
  })
  return result && result.id ? result : null
}

export async function getPlans(): Promise<LearningPlan[]> {
  const result = await apiFetch<LearningPlan[] | null>("/plan/all", {
    cache: "no-store",
  })
  return Array.isArray(result) ? result.filter((p) => p && p.id) : []
}

export async function completePlanStep(
  planId: string,
  dayIndex: number
): Promise<LearningPlan | null> {
  const result = await apiFetch<LearningPlan | null>(
    `/plan/${planId}/steps/${dayIndex}/complete`,
    { method: "POST" }
  )
  return result && result.id ? result : null
}

export async function generateStepLesson(
  planId: string,
  dayIndex: number,
  regenerate = false
): Promise<StepLesson | null> {
  return await apiFetch<StepLesson>(
    `/plan/${planId}/steps/${dayIndex}/lesson?regenerate=${regenerate}`,
    { method: "POST" }
  )
}

export async function completePlanTask(
  planId: string,
  dayIndex: number,
  orderInDay: number,
  payload?: CompleteTaskPayload
): Promise<LearningPlan | null> {
  const result = await apiFetch<LearningPlan | null>(
    `/plan/${planId}/steps/${dayIndex}/${orderInDay}/complete`,
    { method: "POST", body: payload ?? {} }
  )
  return result && result.id ? result : null
}

export async function generateTaskLesson(
  planId: string,
  dayIndex: number,
  orderInDay: number,
  regenerate = false
): Promise<StepLesson | null> {
  return await apiFetch<StepLesson>(
    `/plan/${planId}/steps/${dayIndex}/${orderInDay}/lesson?regenerate=${regenerate}`,
    { method: "POST" }
  )
}

export async function setPlanStatus(
  planId: string,
  status: GoalStatusUpdate
): Promise<LearningPlan | null> {
  const result = await apiFetch<LearningPlan | null>(`/plan/${planId}`, {
    method: "PATCH",
    body: { status },
  })
  return result && result.id ? result : null
}

export async function deletePlan(planId: string): Promise<void> {
  await apiFetch<void>(`/plan/${planId}`, { method: "DELETE" })
}
