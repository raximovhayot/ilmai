import { apiFetch } from "@/lib/api"

export type PlanActivity = "READ" | "QUIZ" | "REVIEW"

export type LearningPlanItemAction = PlanActivity

export type PlanStatus = "ACTIVE" | "SUPERSEDED"

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
  status: PlanStatus
  replanNeeded: boolean
  createdAt: string | null
  daysTotal: number
  daysCompleted: number
  steps: PlanStep[]
}

export async function getPlan(): Promise<LearningPlan | null> {
  const result = await apiFetch<LearningPlan | null>("/plan", {
    cache: "no-store",
  })
  return result && result.id ? result : null
}

export async function completePlanStep(
  dayIndex: number
): Promise<LearningPlan | null> {
  const result = await apiFetch<LearningPlan | null>(
    `/plan/steps/${dayIndex}/complete`,
    { method: "POST" }
  )
  return result && result.id ? result : null
}
