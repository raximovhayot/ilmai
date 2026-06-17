import { getProfile } from "@/lib/profile"

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

export async function listGoals(): Promise<Goal[]> {
  const profile = await getProfile()
  if (!profile?.goal) return []
  const now = new Date().toISOString()
  return [
    {
      id: "profile-goal",
      title: profile.goal,
      description: null,
      targetDate: profile.targetDate ?? null,
      status: "ACTIVE",
      progress: 0,
      daysTotal: 0,
      daysCompleted: 0,
      topicIds: [],
      nextAction: null,
      createdAt: now,
      updatedAt: now,
    },
  ]
}
