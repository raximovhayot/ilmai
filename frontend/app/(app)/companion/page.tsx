import { redirect } from "next/navigation"

import { auth } from "@/auth"
import { CompanionClient } from "@/components/companion/companion-client"

export default async function CompanionPage({
  searchParams,
}: {
  searchParams: Promise<{ seed?: string; session?: string }>
}) {
  const session = await auth()
  if (!session?.user) {
    redirect("/login")
  }

  const params = await searchParams
  const seed = typeof params.seed === "string" ? params.seed : undefined
  const activeSessionId =
    typeof params.session === "string" ? params.session : undefined

  return (
    <CompanionClient
      initialSessions={[]}
      seed={seed}
      activeSessionId={activeSessionId}
    />
  )
}
