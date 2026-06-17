import { redirect } from "next/navigation"

import { auth } from "@/auth"
import { SettingsHubView } from "@/components/settings/settings-hub-view"

export default async function SettingsPage() {
  const session = await auth()

  if (!session?.user) {
    redirect("/login")
  }

  return (
    <SettingsHubView
      sessionUser={{
        name: session.user?.name ?? null,
        email: session.user?.email ?? null,
        image: session.user?.image ?? null,
      }}
    />
  )
}
