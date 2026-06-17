import { redirect } from "next/navigation"

import { auth } from "@/auth"
import { SettingsSubscriptionView } from "@/components/settings/settings-subscription-view"

export default async function SettingsSubscriptionPage() {
  const session = await auth()

  if (!session?.user) {
    redirect("/login")
  }

  return <SettingsSubscriptionView />
}
