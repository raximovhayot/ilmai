"use client"

import * as React from "react"
import { useSession } from "next-auth/react"

import { HomeDashboard } from "@/components/home/home-dashboard"
import { completePlanStep, getPlans, type LearningPlan } from "@/lib/plan"
import { getStats, type Stats } from "@/lib/stats"
import { listTopics, type TopicResponse } from "@/lib/topics"

type Props = {
  greetingName: string | null
}

export function HomeDashboardClient({ greetingName }: Props) {
  const { status } = useSession()
  const [stats, setStats] = React.useState<Stats | null>(null)
  const [plans, setPlans] = React.useState<LearningPlan[]>([])
  const [topics, setTopics] = React.useState<TopicResponse[]>([])
  const [loading, setLoading] = React.useState(true)

  React.useEffect(() => {
    if (status !== "authenticated") return
    let cancelled = false
    void (async () => {
      try {
        const [s, p, ts] = await Promise.all([
          getStats(),
          getPlans().catch(() => []),
          listTopics().catch(() => []),
        ])
        if (!cancelled) {
          setStats(s)
          setPlans(p)
          setTopics(ts)
        }
      } catch {
        // ignore
      } finally {
        if (!cancelled) setLoading(false)
      }
    })()
    return () => {
      cancelled = true
    }
  }, [status])

  const onCompleteStep = React.useCallback(
    async (planId: string, dayIndex: number) => {
      if (status !== "authenticated") return
      try {
        const fresh = await completePlanStep(planId, dayIndex)
        if (fresh) {
          setPlans((prev) => prev.map((p) => (p.id === fresh.id ? fresh : p)))
        }
      } catch {
        // ignore
      }
    },
    [status]
  )

  return (
    <HomeDashboard
      greetingName={greetingName}
      stats={stats}
      plans={plans}
      topics={topics}
      loading={loading}
      onCompleteStep={onCompleteStep}
    />
  )
}
