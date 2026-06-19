import { redirect } from "next/navigation"

import { auth } from "@/auth"
import { AppShellClient } from "@/components/app-shell/app-shell-client"
import { apiFetch } from "@/lib/api"
import type { OnboardingState } from "@/lib/onboarding"
import { getServerAccessToken } from "@/lib/server-auth"

export default async function AppLayout({
  children,
}: {
  children: React.ReactNode
}) {
  const session = await auth()

  if (!session?.user) {
    redirect("/login")
  }

  let needsOnboarding = false
  const accessToken = await getServerAccessToken()
  if (accessToken) {
    try {
      const onboarding = await apiFetch<OnboardingState | null>("/onboarding", {
        accessToken,
        cache: "no-store",
      })
      needsOnboarding = !!onboarding && onboarding.onboardingPassed == null
    } catch {
      needsOnboarding = false
    }
  }
  if (needsOnboarding) {
    redirect("/onboarding")
  }

  return (
    <AppShellClient
      user={{
        name: session.user?.name ?? null,
        email: session.user?.email ?? null,
        image: session.user?.image ?? null,
      }}
    >
      {children}
    </AppShellClient>
  )
}
