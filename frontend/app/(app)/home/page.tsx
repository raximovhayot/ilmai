import { redirect } from "next/navigation"

import { auth } from "@/auth"
import { HomeDashboardClient } from "@/components/home/home-dashboard-client"

export default async function HomePage() {
  const session = await auth()
  if (!session?.user) {
    redirect("/login")
  }

  return <HomeDashboardClient greetingName={session.user?.name ?? null} />
}
