"use client"

import * as React from "react"
import { useSession } from "next-auth/react"
import { HugeiconsIcon } from "@hugeicons/react"
import { RoadIcon } from "@hugeicons/core-free-icons"

import { AddGoalDialog } from "@/components/home/add-goal-dialog"
import { PlanRoadmap } from "@/components/plan/plan-view"
import { Card, CardContent } from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs"
import {
  getPlans,
  type LearningPlan,
  type PlanStatus,
} from "@/lib/plan"
import { useT } from "@/lib/i18n/provider"
import { listTopics, type TopicResponse } from "@/lib/topics"
import { cn } from "@/lib/utils"

type StatusFilter = "ACTIVE" | "COMPLETED" | "PAUSED"

export function PlanViewClient() {
  const t = useT()
  const { status } = useSession()
  const [plans, setPlans] = React.useState<LearningPlan[]>([])
  const [topics, setTopics] = React.useState<TopicResponse[]>([])
  const [loaded, setLoaded] = React.useState(false)
  const [statusFilter, setStatusFilter] = React.useState<StatusFilter>("ACTIVE")
  const [selectedId, setSelectedId] = React.useState<string | null>(null)

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

  const today = React.useMemo(() => new Date().toISOString().slice(0, 10), [])

  const filteredPlans = React.useMemo(
    () => plans.filter((p) => p.status === (statusFilter as PlanStatus)),
    [plans, statusFilter]
  )

  const selectedPlan = React.useMemo(
    () =>
      filteredPlans.find((p) => p.id === selectedId) ??
      filteredPlans[0] ??
      null,
    [filteredPlans, selectedId]
  )

  if (!loaded) {
    return (
      <div className="flex flex-col gap-6">
        <PlanHeader />
        <Skeleton className="h-32 w-full rounded-2xl" />
        <div className="flex flex-col gap-3">
          <Skeleton className="h-20 w-full rounded-xl" />
          <Skeleton className="h-20 w-full rounded-xl" />
          <Skeleton className="h-20 w-full rounded-xl" />
        </div>
      </div>
    )
  }

  if (plans.length === 0) {
    return (
      <div className="flex flex-col gap-6">
        <PlanHeader />
        <Card>
          <CardContent className="flex flex-col items-center gap-3 py-10 text-center">
            <span className="flex size-12 items-center justify-center rounded-full bg-muted text-muted-foreground">
              <HugeiconsIcon
                icon={RoadIcon}
                strokeWidth={2}
                className="size-6"
              />
            </span>
            <p className="max-w-sm text-sm text-muted-foreground">
              {t.plan.empty}
            </p>
            <AddGoalDialog variant="default" size="default" />
          </CardContent>
        </Card>
      </div>
    )
  }

  return (
    <div className="flex flex-col gap-6">
      <PlanHeader />

      <div className="flex flex-col gap-3">
        <Tabs
          value={statusFilter}
          onValueChange={(value) => {
            setStatusFilter(value as StatusFilter)
            setSelectedId(null)
          }}
        >
          <TabsList>
            <TabsTrigger value="ACTIVE">{t.plan.statusActive}</TabsTrigger>
            <TabsTrigger value="COMPLETED">
              {t.plan.statusCompleted}
            </TabsTrigger>
            <TabsTrigger value="PAUSED">{t.plan.statusPaused}</TabsTrigger>
          </TabsList>
        </Tabs>

        {filteredPlans.length === 0 ? (
          <p className="text-sm text-muted-foreground">
            {t.plan.noGoalsInStatus}
          </p>
        ) : filteredPlans.length > 1 ? (
          <div className="flex flex-wrap gap-2">
            {filteredPlans.map((plan) => (
              <GoalChip
                key={plan.id}
                plan={plan}
                selected={selectedPlan?.id === plan.id}
                onSelect={() => setSelectedId(plan.id)}
              />
            ))}
          </div>
        ) : null}
      </div>

      {selectedPlan ? (
        <PlanRoadmap plan={selectedPlan} topics={topics} today={today} />
      ) : null}
    </div>
  )
}

function PlanHeader() {
  const t = useT()
  return (
    <header className="flex flex-wrap items-start justify-between gap-3">
      <div className="flex flex-col gap-1">
        <h1 className="font-heading text-2xl font-semibold tracking-tight md:text-3xl">
          {t.plan.title}
        </h1>
        <p className="text-sm text-muted-foreground">{t.plan.subtitle}</p>
      </div>
      <AddGoalDialog />
    </header>
  )
}

function GoalChip({
  plan,
  selected,
  onSelect,
}: {
  plan: LearningPlan
  selected: boolean
  onSelect: () => void
}) {
  const t = useT()
  return (
    <button
      type="button"
      onClick={onSelect}
      aria-pressed={selected}
      className={cn(
        "inline-flex items-center gap-2 rounded-full border px-3.5 py-1.5 text-sm font-medium transition-colors",
        selected
          ? "border-primary bg-primary text-primary-foreground"
          : "border-border hover:bg-accent/40"
      )}
    >
      <span className="max-w-[12rem] truncate">{plan.goal ?? t.plan.goal}</span>
      {plan.replanNeeded ? (
        <span
          className={cn(
            "size-2 shrink-0 rounded-full bg-amber-500",
            selected && "bg-amber-300"
          )}
        />
      ) : null}
    </button>
  )
}
