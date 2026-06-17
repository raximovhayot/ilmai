"use client"

import * as React from "react"
import { usePathname } from "next/navigation"
import { useSession } from "next-auth/react"

import { AppShell } from "@/components/app-shell/app-shell"
import { listTopics, type TopicResponse } from "@/lib/topics"
import { getPremium, type PremiumStatus } from "@/lib/premium"
import { listSessions, type ChatSession } from "@/lib/agent"

type Props = {
  user: {
    name?: string | null
    email?: string | null
    image?: string | null
  }
  children: React.ReactNode
}

export function AppShellClient({ user, children }: Props) {
  const { status } = useSession()
  const pathname = usePathname()
  const [topics, setTopics] = React.useState<TopicResponse[]>([])
  const [premium, setPremium] = React.useState<PremiumStatus | null>(null)
  const [sessions, setSessions] = React.useState<ChatSession[]>([])

  React.useEffect(() => {
    if (status !== "authenticated") return
    let cancelled = false
    void (async () => {
      try {
        const [t, p] = await Promise.all([listTopics(), getPremium()])
        if (!cancelled) {
          setTopics(t)
          setPremium(p)
        }
      } catch {
        // ignore — empty state OK
      }
    })()
    return () => {
      cancelled = true
    }
  }, [status])

  React.useEffect(() => {
    if (status !== "authenticated") return
    let cancelled = false
    void (async () => {
      try {
        const list = await listSessions()
        if (!cancelled) setSessions(list)
      } catch {
        // ignore — empty state OK
      }
    })()
    return () => {
      cancelled = true
    }
  }, [status, pathname])

  return (
    <AppShell topics={topics} sessions={sessions} premium={premium} user={user}>
      {children}
    </AppShell>
  )
}
