"use client"

import * as React from "react"
import { useSession } from "next-auth/react"

import { PlanView } from "@/components/plan/plan-view"
import { getPlan, type LearningPlan } from "@/lib/plan"
import { listTopics, type TopicResponse } from "@/lib/topics"

export function PlanViewClient() {
  const { status } = useSession()
  const [plan, setPlan] = React.useState<LearningPlan | null>(null)
  const [topics, setTopics] = React.useState<TopicResponse[]>([])

  React.useEffect(() => {
    if (status !== "authenticated") return
    let cancelled = false
    void (async () => {
      try {
        const [p, t] = await Promise.all([getPlan(), listTopics()])
        if (!cancelled) {
          setPlan(p)
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

  return <PlanView initialPlan={plan} topics={topics} />
}
