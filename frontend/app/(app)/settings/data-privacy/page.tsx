import { redirect } from "next/navigation"

import { auth } from "@/auth"
import { SettingsDataPrivacyView } from "@/components/settings/settings-data-privacy-view"

export default async function SettingsDataPrivacyPage() {
  const session = await auth()

  if (!session?.user) {
    redirect("/login")
  }

  return <SettingsDataPrivacyView />
}
