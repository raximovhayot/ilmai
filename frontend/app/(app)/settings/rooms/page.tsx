import { redirect } from "next/navigation"

import { auth } from "@/auth"
import { SettingsRoomsView } from "@/components/settings/settings-rooms-view"

export default async function SettingsRoomsPage() {
  const session = await auth()

  if (!session?.user) {
    redirect("/login")
  }

  return <SettingsRoomsView />
}
