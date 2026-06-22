import { apiFetch } from "@/lib/api"

export type PlanActivity = "READ" | "QUIZ" | "REVIEW"

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
  scheduledDate: string | null
  title: string
  activity: PlanActivity
  materials: PlanMaterial[]
  note: string | null
  done: boolean
  completedAt: string | null
  hasLesson: boolean
  lessonGeneratedAt: string | null
}

export type LessonCitation = {
  materialId: string | null
  materialName: string | null
  chunkIndex: number | null
  snippet: string | null
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
