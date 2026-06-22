import { redirect } from "next/navigation"

import { auth } from "@/auth"
import { ProfileView } from "@/components/profile/profile-view"

export default async function ProfilePage() {
  const session = await auth()
  if (!session?.user) redirect("/login")
  return <ProfileView />
}
