import { apiFetch } from "@/lib/api"

export type ProfileResponse = {
  userId?: string
  locale?: string | null
  timezone?: string | null
  goal?: string | null
  targetDate?: string | null
  dailyReminder?: string | null
  dailyStudyMinutes?: number | null
  sessionsCount: number
  quizCount: number
  streakDays: number
  lastActiveAt?: string | null
}

export type UpdateProfileInput = {
  locale?: string | null
  timezone?: string | null
  goal?: string | null
  targetDate?: string | null
  dailyReminder?: string | null
  dailyStudyMinutes?: number | null
}

export async function getProfile(): Promise<ProfileResponse | null> {
  return await apiFetch<ProfileResponse>("/profile", {
    cache: "no-store",
  })
}

export async function updateProfile(
  body: UpdateProfileInput
): Promise<ProfileResponse | null> {
  return await apiFetch<ProfileResponse>("/profile", {
    method: "PUT",
    body,
  })
}
