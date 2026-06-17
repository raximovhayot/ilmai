import { redirect } from "next/navigation"

import { auth } from "@/auth"
import { GapsViewClient } from "@/components/gaps/gaps-view-client"

export default async function GapsPage() {
  const session = await auth()
  if (!session?.user) redirect("/login")
  return <GapsViewClient />
}
