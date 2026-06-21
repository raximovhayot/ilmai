"use client"

import * as React from "react"
import { useSession } from "next-auth/react"
import { HugeiconsIcon } from "@hugeicons/react"
import { RoadIcon } from "@hugeicons/core-free-icons"

import { AddGoalDialog } from "@/components/home/add-goal-dialog"
import { PlanView } from "@/components/plan/plan-view"
import { Card, CardContent } from "@/components/ui/card"
import { getPlans, type LearningPlan } from "@/lib/plan"
import { useT } from "@/lib/i18n/provider"
import { listTopics, type TopicResponse } from "@/lib/topics"

export function PlanViewClient() {
  const t = useT()
  const { status } = useSession()
  const [plans, setPlans] = React.useState<LearningPlan[]>([])
  const [topics, setTopics] = React.useState<TopicResponse[]>([])
  const [loaded, setLoaded] = React.useState(false)

  React.useEffect(() => {
    if (status !== "authenticated") return
    let cancelled = false
    void (async () => {
      try {
        const [p, ts] = await Promise.all([
          getPlans().catch(() => []),
          listTopics().catch(() => []),
        ])
        if (!cancelled) {
          setPlans(p)
          setTopics(ts)
        }
      } catch {
        // ignore
      } finally {
        if (!cancelled) setLoaded(true)
      }
    })()
    return () => {
      cancelled = true
    }
  }, [status])

  return (
    <div className="flex flex-col gap-6">
      <header className="flex flex-wrap items-start justify-between gap-3">
        <div className="flex flex-col gap-1">
          <h1 className="font-heading text-2xl font-semibold tracking-tight md:text-3xl">
            {t.plan.title}
          </h1>
          <p className="text-sm text-muted-foreground">{t.plan.subtitle}</p>
        </div>
        <AddGoalDialog />
      </header>

      {loaded && plans.length === 0 ? (
        <Card>
          <CardContent className="flex flex-col items-center gap-3 py-10 text-center">
            <span className="flex size-12 items-center justify-center rounded-full bg-muted text-muted-foreground">
              <HugeiconsIcon icon={RoadIcon} strokeWidth={2} className="size-6" />
            </span>
            <p className="max-w-sm text-sm text-muted-foreground">
              {t.plan.empty}
            </p>
            <AddGoalDialog variant="default" size="default" />
          </CardContent>
        </Card>
      ) : (
        <div className="flex flex-col gap-8">
          {plans.map((plan) => (
            <PlanView key={plan.id} initialPlan={plan} topics={topics} />
          ))}
        </div>
      )}
    </div>
  )
}
