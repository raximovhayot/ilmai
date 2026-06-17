"use client"

import * as React from "react"
import { useSession } from "next-auth/react"

import { HomeDashboard } from "@/components/home/home-dashboard"
import { listGoals, type Goal } from "@/lib/goals"
import { completePlanStep, getPlan, type LearningPlan } from "@/lib/plan"
import { getStats, type Stats } from "@/lib/stats"
import { listTopics, type TopicResponse } from "@/lib/topics"

type Props = {
  greetingName: string | null
}

export function HomeDashboardClient({ greetingName }: Props) {
  const { status } = useSession()
  const [stats, setStats] = React.useState<Stats | null>(null)
  const [goals, setGoals] = React.useState<Goal[]>([])
  const [plan, setPlan] = React.useState<LearningPlan | null>(null)
  const [topics, setTopics] = React.useState<TopicResponse[]>([])
  const [loading, setLoading] = React.useState(true)

  React.useEffect(() => {
    if (status !== "authenticated") return
    let cancelled = false
    void (async () => {
      try {
        const [s, g, p, ts] = await Promise.all([
          getStats(),
          listGoals(),
          getPlan().catch(() => null),
          listTopics().catch(() => []),
        ])
        if (!cancelled) {
          setStats(s)
          setGoals(g)
          setPlan(p)
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
    async (dayIndex: number) => {
      if (status !== "authenticated") return
      try {
        const fresh = await completePlanStep(dayIndex)
        if (fresh) setPlan(fresh)
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
      goals={goals}
      plan={plan}
      topics={topics}
      loading={loading}
      onCompleteStep={onCompleteStep}
    />
  )
}
