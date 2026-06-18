import { getProfile } from "@/lib/profile"

export type Stats = {
  sessionsCompleted: number
  streakDays: number
  weeklyMinutes: number
}

export async function getStats(): Promise<Stats | null> {
  const profile = await getProfile()
  if (!profile) return null
  return {
    sessionsCompleted: profile.sessionsCount,
    streakDays: profile.streakDays,
    weeklyMinutes: (profile.dailyStudyMinutes ?? 0) * 7,
  }
}
