import { redirect } from "next/navigation"

import { auth } from "@/auth"
import { SettingsAccountView } from "@/components/settings/settings-account-view"
import { apiFetch } from "@/lib/api"
import { getServerAccessToken } from "@/lib/server-auth"

type AuthMe = {
  id: string
  username: string
  status: string
  createdAt: string
}

export default async function SettingsAccountPage() {
  const session = await auth()

  if (!session?.user) {
    redirect("/login")
  }

  let account = null
  try {
    const accessToken = await getServerAccessToken()
    account = await apiFetch<AuthMe>("/auth/me", { accessToken })
  } catch (error) {
    console.error("Failed to fetch account info:", error)
  }

  return (
    <SettingsAccountView
      sessionUser={{
        name: session.user?.name ?? null,
        email: session.user?.email ?? null,
        image: session.user?.image ?? null,
      }}
      account={account}
    />
  )
}
