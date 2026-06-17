import { redirect } from "next/navigation"

import { auth } from "@/auth"
import { PremiumViewClient } from "@/components/premium/premium-view-client"

export default async function PremiumPage() {
  const session = await auth()
  if (!session?.user) redirect("/login")
  return <PremiumViewClient />
}
