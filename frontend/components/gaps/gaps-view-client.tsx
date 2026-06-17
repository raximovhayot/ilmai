"use client"

import * as React from "react"
import { useSession } from "next-auth/react"

import { GapsView } from "@/components/gaps/gaps-view"
import { getGaps, type GapsReport } from "@/lib/gaps"
import { listTopics, type TopicResponse } from "@/lib/topics"

export function GapsViewClient() {
  const { status } = useSession()
  const [gaps, setGaps] = React.useState<GapsReport | null>(null)
  const [topics, setTopics] = React.useState<TopicResponse[]>([])

  React.useEffect(() => {
    if (status !== "authenticated") return
    let cancelled = false
    void (async () => {
      try {
        const [g, t] = await Promise.all([getGaps(), listTopics()])
        if (!cancelled) {
          setGaps(g)
          setTopics(t)
        }
      } catch {
        // ignore
      }
    })()
    return () => {
      cancelled = true
    }
  }, [status])

  return <GapsView initialGaps={gaps} topics={topics} />
}
