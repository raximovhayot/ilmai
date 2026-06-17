import { apiFetch } from "@/lib/api"

export type OnboardingState = {
  goal: string | null
  targetDate: string | null
  dailyStudyMinutes: number | null
  dailyReminder: string | null
}

export type OnboardingInput = {
  goal?: string | null
  targetDate?: string | null
  dailyStudyMinutes?: number | null
  dailyReminder?: string | null
}

export async function getOnboarding(): Promise<OnboardingState | null> {
  return await apiFetch<OnboardingState | null>("/onboarding", {
    cache: "no-store",
  })
}

export async function saveOnboarding(
  body: OnboardingInput
): Promise<OnboardingState | null> {
  return await apiFetch<OnboardingState | null>("/onboarding", {
    method: "PUT",
    body,
  })
}
