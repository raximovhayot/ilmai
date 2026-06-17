import { redirect } from "next/navigation"

import { auth } from "@/auth"
import { PlanViewClient } from "@/components/plan/plan-view-client"

export default async function PlanPage() {
  const session = await auth()
  if (!session?.user) redirect("/login")
  return <PlanViewClient />
}
