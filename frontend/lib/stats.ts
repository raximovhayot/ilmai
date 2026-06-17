import { getProfile } from "@/lib/profile"

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

export async function getStats(): Promise<Stats | null> {
  const profile = await getProfile()
  if (!profile) return null
  return {
    sessionsCompleted: profile.sessionsCount,
    topicsCount: 0,
    materialsCount: 0,
    streakDays: profile.streakDays,
    currentLevel: "",
    knowledgeScore: 0,
    weeklyMinutes: (profile.dailyStudyMinutes ?? 0) * 7,
    weekActivity: Array.from({ length: 7 }, () => false),
    knowledgeHistory: [],
    perTopic: [],
  }
}
