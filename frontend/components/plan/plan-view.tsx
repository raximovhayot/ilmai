"use client"

import * as React from "react"
import Link from "next/link"
import { useSession } from "next-auth/react"
import { toast } from "sonner"
import { HugeiconsIcon } from "@hugeicons/react"
import {
  ArrowRight01Icon,
  BookOpen01Icon,
  Calendar03Icon,
  CheckmarkCircle02Icon,
  Flag03Icon,
  PuzzleIcon,
  RefreshIcon,
  RoadIcon,
} from "@hugeicons/core-free-icons"

import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Progress } from "@/components/ui/progress"
import { Spinner } from "@/components/ui/spinner"
import {
  completePlanStep,
  getPlan,
  type LearningPlan,
  type PlanActivity,
  type PlanStep,
} from "@/lib/plan"
import { useT } from "@/lib/i18n/provider"
import type { TopicResponse } from "@/lib/topics"
import { cn } from "@/lib/utils"

type Props = {
  initialPlan: LearningPlan | null
  topics: TopicResponse[]
}

export function PlanView({ initialPlan, topics }: Props) {
  const t = useT()
  const { status } = useSession()
  const [plan, setPlan] = React.useState<LearningPlan | null>(initialPlan)
  const [completing, setCompleting] = React.useState<number | null>(null)
  const [refreshing, setRefreshing] = React.useState(false)
  const [syncedInitial, setSyncedInitial] = React.useState(initialPlan)

  if (initialPlan !== syncedInitial) {
    setSyncedInitial(initialPlan)
    setPlan(initialPlan)
  }

  const topicNameById = React.useMemo(
    () => new Map(topics.map((tp) => [tp.id, tp.name])),
    [topics]
  )
  const today = new Date().toISOString().slice(0, 10)

  const onComplete = async (dayIndex: number) => {
    if (status !== "authenticated") return
    setCompleting(dayIndex)
    try {
      const fresh = await completePlanStep(dayIndex)
      if (fresh) setPlan(fresh)
    } catch {
      toast.error(t.errors.generic)
    } finally {
      setCompleting(null)
    }
  }

  const onRefresh = async () => {
    if (status !== "authenticated") return
    setRefreshing(true)
    try {
      setPlan(await getPlan())
    } catch {
      toast.error(t.errors.generic)
    } finally {
      setRefreshing(false)
    }
  }

  const progress =
    plan && plan.daysTotal > 0
      ? Math.min(100, Math.round((plan.daysCompleted / plan.daysTotal) * 100))
      : 0

  return (
    <div className="flex flex-col gap-6">
      <header className="flex flex-col gap-1">
        <h1 className="font-heading text-2xl font-semibold tracking-tight md:text-3xl">
          {t.plan.title}
        </h1>
        <p className="text-sm text-muted-foreground">{t.plan.subtitle}</p>
      </header>

      {plan?.replanNeeded ? (
        <ReplanBanner onRefresh={onRefresh} refreshing={refreshing} />
      ) : null}

      {!plan ? (
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
            <Button
              nativeButton={false}
              render={
                <Link href="/profile">
                  <HugeiconsIcon
                    icon={Flag03Icon}
                    strokeWidth={2}
                    data-icon="inline-start"
                  />
                  {t.plan.setGoal}
                </Link>
              }
            />
          </CardContent>
        </Card>
      ) : (
        <>
          <Card>
            <CardHeader className="flex flex-row flex-wrap items-center justify-between gap-2">
              <CardTitle className="flex items-center gap-2 text-base">
                <HugeiconsIcon
                  icon={Flag03Icon}
                  strokeWidth={2}
                  className="size-5"
                />
                {t.plan.goal}
              </CardTitle>
              <Badge variant="outline" className="tabular-nums">
                {plan.daysCompleted}/{plan.daysTotal}
              </Badge>
            </CardHeader>
            <CardContent className="flex flex-col gap-3">
              <div className="flex flex-col gap-1">
                <span className="text-sm font-medium">
                  {plan.goal ?? t.plan.empty}
                </span>
                {plan.targetDate ? (
                  <span className="text-xs text-muted-foreground">
                    {t.plan.target}: {plan.targetDate}
                  </span>
                ) : null}
              </div>
              <Button
                nativeButton={false}
                variant="outline"
                size="sm"
                className="self-start"
                render={
                  <Link href="/profile">
                    <HugeiconsIcon
                      icon={Flag03Icon}
                      strokeWidth={2}
                      data-icon="inline-start"
                    />
                    {plan.goal ? t.plan.editGoal : t.plan.setGoal}
                  </Link>
                }
              />
              <div>
                <div className="flex items-center justify-between text-xs text-muted-foreground">
                  <span>{t.plan.progress}</span>
                  <span className="tabular-nums">{progress}%</span>
                </div>
                <Progress value={progress} className="mt-1" />
              </div>
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle className="flex items-center gap-2 text-base">
                <HugeiconsIcon
                  icon={RoadIcon}
                  strokeWidth={2}
                  className="size-5"
                />
                {t.plan.upcoming}
              </CardTitle>
            </CardHeader>
            <CardContent className="flex flex-col gap-3">
              {plan.steps.length === 0 ? (
                <p className="text-sm text-muted-foreground">{t.plan.empty}</p>
              ) : (
                plan.steps.map((step) => (
                  <StepCard
                    key={step.dayIndex}
                    step={step}
                    isToday={
                      !!step.scheduledDate && step.scheduledDate === today
                    }
                    completing={completing === step.dayIndex}
                    topicNameById={topicNameById}
                    onComplete={() => onComplete(step.dayIndex)}
                  />
                ))
              )}
            </CardContent>
          </Card>
        </>
      )}
    </div>
  )
}

function ReplanBanner({
  onRefresh,
  refreshing,
}: {
  onRefresh: () => void
  refreshing: boolean
}) {
  const t = useT()
  return (
    <Card className="border-amber-500/30 bg-amber-500/10">
      <CardContent className="flex flex-col gap-3 py-4 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex items-start gap-3">
          <span className="mt-0.5 flex size-8 shrink-0 items-center justify-center rounded-full bg-amber-500/20 text-amber-600 dark:text-amber-400">
            <HugeiconsIcon
              icon={RefreshIcon}
              strokeWidth={2}
              className="size-4"
            />
          </span>
          <div className="flex flex-col gap-0.5">
            <span className="text-sm font-medium">{t.plan.replanTitle}</span>
            <span className="text-xs text-muted-foreground">
              {t.plan.replanDescription}
            </span>
          </div>
        </div>
        <Button
          variant="outline"
          size="sm"
          onClick={onRefresh}
          disabled={refreshing}
          className="shrink-0"
        >
          {refreshing ? (
            <Spinner data-icon="inline-start" />
          ) : (
            <HugeiconsIcon
              icon={RefreshIcon}
              strokeWidth={2}
              data-icon="inline-start"
            />
          )}
          {t.plan.refresh}
        </Button>
      </CardContent>
    </Card>
  )
}

function StepCard({
  step,
  isToday,
  completing,
  topicNameById,
  onComplete,
}: {
  step: PlanStep
  isToday: boolean
  completing: boolean
  topicNameById: Map<string, string>
  onComplete: () => void
}) {
  const t = useT()
  const activityLabel = labelForAction(step.activity, t)
  return (
    <div
      className={cn(
        "rounded-xl border border-border p-4",
        step.done && "opacity-70",
        isToday && "border-primary/40 bg-primary/5"
      )}
    >
      <div className="mb-2 flex flex-wrap items-center justify-between gap-2">
        <div className="flex items-center gap-2">
          <HugeiconsIcon
            icon={Calendar03Icon}
            strokeWidth={2}
            className="size-4 text-muted-foreground"
          />
          <span className="text-sm font-medium">
            {t.plan.dayLabel.replace("{n}", String(step.dayIndex))}
          </span>
          {step.scheduledDate ? (
            <span className="text-xs text-muted-foreground">
              {step.scheduledDate}
            </span>
          ) : null}
          {isToday ? (
            <Badge variant="default">{t.plan.todayBadge}</Badge>
          ) : null}
        </div>
        <Badge
          variant="outline"
          className={cn(
            "gap-1",
            step.activity === "READ"
              ? "text-primary"
              : step.activity === "QUIZ"
                ? "text-amber-600 dark:text-amber-400"
                : "text-emerald-600 dark:text-emerald-400"
          )}
        >
          <HugeiconsIcon
            icon={iconForAction(step.activity)}
            strokeWidth={2}
            className="size-3.5"
          />
          {activityLabel}
        </Badge>
      </div>

      <p className={cn("text-sm font-medium", step.done && "line-through")}>
        {step.title}
      </p>
      {step.note ? (
        <p className="mt-0.5 text-xs text-muted-foreground">{step.note}</p>
      ) : null}

      {step.materials.length > 0 ? (
        <ul className="mt-2 flex flex-col gap-1">
          {step.materials.map((material) => {
            const topicName = material.topicId
              ? topicNameById.get(material.topicId)
              : undefined
            const label = material.title ?? topicName ?? activityLabel
            const inner = (
              <>
                <HugeiconsIcon
                  icon={BookOpen01Icon}
                  strokeWidth={2}
                  className="size-3.5 shrink-0 text-muted-foreground"
                />
                <span className="truncate">{label}</span>
                {material.topicId ? (
                  <HugeiconsIcon
                    icon={ArrowRight01Icon}
                    strokeWidth={2}
                    className="size-3.5 shrink-0 text-muted-foreground rtl:rotate-180"
                  />
                ) : null}
              </>
            )
            return (
              <li key={material.id}>
                {material.topicId ? (
                  <Link
                    href={`/data/${material.topicId}`}
                    className="flex items-center gap-1.5 rounded-md px-1 py-0.5 text-xs text-muted-foreground hover:bg-accent/40 hover:text-foreground"
                  >
                    {inner}
                  </Link>
                ) : (
                  <span className="flex items-center gap-1.5 px-1 py-0.5 text-xs text-muted-foreground">
                    {inner}
                  </span>
                )}
              </li>
            )
          })}
        </ul>
      ) : null}

      <div className="mt-3 flex items-center justify-end">
        {step.done ? (
          <span className="inline-flex items-center gap-1.5 text-xs font-medium text-emerald-600 dark:text-emerald-400">
            <HugeiconsIcon
              icon={CheckmarkCircle02Icon}
              strokeWidth={2}
              className="size-4"
            />
            {t.plan.completed}
          </span>
        ) : (
          <Button size="sm" onClick={onComplete} disabled={completing}>
            {completing ? (
              <Spinner data-icon="inline-start" />
            ) : (
              <HugeiconsIcon
                icon={CheckmarkCircle02Icon}
                strokeWidth={2}
                data-icon="inline-start"
              />
            )}
            {t.plan.markDone}
          </Button>
        )}
      </div>
    </div>
  )
}

function iconForAction(action: PlanActivity) {
  if (action === "READ") return BookOpen01Icon
  if (action === "QUIZ") return PuzzleIcon
  return RoadIcon
}

function labelForAction(action: PlanActivity, t: ReturnType<typeof useT>) {
  if (action === "READ") return t.plan.actionRead
  if (action === "QUIZ") return t.plan.actionQuiz
  return t.plan.actionReview
}
