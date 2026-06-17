import { redirect } from "next/navigation"

import { auth } from "@/auth"
import { SettingsTelegramView } from "@/components/settings/settings-telegram-view"

export default async function SettingsTelegramPage() {
  const session = await auth()

  if (!session?.user) {
    redirect("/login")
  }

  return <SettingsTelegramView />
}
