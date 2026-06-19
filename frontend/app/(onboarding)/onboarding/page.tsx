import { redirect } from "next/navigation"

import { auth } from "@/auth"
import { OnboardingWizard } from "@/components/onboarding/onboarding-wizard"

export default async function OnboardingPage() {
  const session = await auth()
  if (!session?.user) {
    redirect("/login")
  }

  return <OnboardingWizard />
}
