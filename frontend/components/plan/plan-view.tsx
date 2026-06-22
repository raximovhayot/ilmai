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

import { Response } from "@/components/ai-elements/response"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Progress } from "@/components/ui/progress"
import { Spinner } from "@/components/ui/spinner"
import {
  completePlanStep,
  generateStepLesson,
  getPlans,
  type LearningPlan,
  type PlanActivity,
  type PlanStep,
  type StepLesson,
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
  const [lessons, setLessons] = React.useState<Record<number, StepLesson>>({})
  const [expanded, setExpanded] = React.useState<number | null>(null)
  const [lessonLoading, setLessonLoading] = React.useState<number | null>(null)

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
    if (status !== "authenticated" || !plan) return
    setCompleting(dayIndex)
    try {
      const fresh = await completePlanStep(plan.id, dayIndex)
      if (fresh) setPlan(fresh)
    } catch {
      toast.error(t.errors.generic)
    } finally {
      setCompleting(null)
    }
  }

  const onRefresh = async () => {
    if (status !== "authenticated" || !plan) return
    setRefreshing(true)
    try {
      const all = await getPlans()
      const fresh = all.find((p) => p.id === plan.id)
      if (fresh) setPlan(fresh)
    } catch {
      toast.error(t.errors.generic)
    } finally {
      setRefreshing(false)
    }
  }

  const onToggleLesson = async (dayIndex: number) => {
    if (status !== "authenticated" || !plan) return
    if (expanded === dayIndex) {
      setExpanded(null)
      return
    }
    if (lessons[dayIndex]) {
      setExpanded(dayIndex)
      return
    }
    setLessonLoading(dayIndex)
    try {
      const lesson = await generateStepLesson(plan.id, dayIndex)
      if (lesson) {
        setLessons((prev) => ({ ...prev, [dayIndex]: lesson }))
        setExpanded(dayIndex)
      }
    } catch {
      toast.error(t.errors.generic)
    } finally {
      setLessonLoading(null)
    }
  }

  const onRegenerateLesson = async (dayIndex: number) => {
    if (status !== "authenticated" || !plan) return
    setLessonLoading(dayIndex)
    try {
      const lesson = await generateStepLesson(plan.id, dayIndex, true)
      if (lesson) {
        setLessons((prev) => ({ ...prev, [dayIndex]: lesson }))
        setExpanded(dayIndex)
      }
    } catch {
      toast.error(t.errors.generic)
    } finally {
      setLessonLoading(null)
    }
  }

  const progress =
    plan && plan.daysTotal > 0
      ? Math.min(100, Math.round((plan.daysCompleted / plan.daysTotal) * 100))
      : 0

  if (!plan) return null

  return (
    <div id={`plan-${plan.id}`} className="flex scroll-mt-24 flex-col gap-4">
      {plan.replanNeeded ? (
        <ReplanBanner onRefresh={onRefresh} refreshing={refreshing} />
      ) : null}

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
                    lesson={lessons[step.dayIndex]}
                    lessonOpen={expanded === step.dayIndex}
                    lessonLoading={lessonLoading === step.dayIndex}
                    onToggleLesson={() => onToggleLesson(step.dayIndex)}
                    onRegenerateLesson={() =>
                      onRegenerateLesson(step.dayIndex)
                    }
                  />
                ))
              )}
            </CardContent>
          </Card>
      </>
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
  lesson,
  lessonOpen,
  lessonLoading,
  onToggleLesson,
  onRegenerateLesson,
}: {
  step: PlanStep
  isToday: boolean
  completing: boolean
  topicNameById: Map<string, string>
  onComplete: () => void
  lesson: StepLesson | undefined
  lessonOpen: boolean
  lessonLoading: boolean
  onToggleLesson: () => void
  onRegenerateLesson: () => void
}) {
  const t = useT()
  const activityLabel = labelForAction(step.activity, t)
  const lessonButtonLabel = lessonOpen
    ? t.plan.hideLesson
    : step.hasLesson || lesson
      ? t.plan.openLesson
      : t.plan.startLesson
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

      <div className="mt-3 flex flex-wrap items-center justify-between gap-2">
        {step.activity === "QUIZ" ? (
          <Button
            nativeButton={false}
            variant="outline"
            size="sm"
            render={
              <Link
                href={`/companion?seed=${encodeURIComponent(
                  `${t.plan.startQuiz}: ${step.title}`
                )}`}
              >
                <HugeiconsIcon
                  icon={PuzzleIcon}
                  strokeWidth={2}
                  data-icon="inline-start"
                />
                {t.plan.startQuiz}
              </Link>
            }
          />
        ) : (
          <Button
            variant="outline"
            size="sm"
            onClick={onToggleLesson}
            disabled={lessonLoading}
          >
            {lessonLoading ? (
              <Spinner data-icon="inline-start" />
            ) : (
              <HugeiconsIcon
                icon={BookOpen01Icon}
                strokeWidth={2}
                data-icon="inline-start"
              />
            )}
            {lessonButtonLabel}
          </Button>
        )}

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

      {step.activity !== "QUIZ" && lessonOpen && lesson ? (
        <LessonPanel
          lesson={lesson}
          regenerating={lessonLoading}
          onRegenerate={onRegenerateLesson}
        />
      ) : null}
    </div>
  )
}

function LessonPanel({
  lesson,
  regenerating,
  onRegenerate,
}: {
  lesson: StepLesson
  regenerating: boolean
  onRegenerate: () => void
}) {
  const t = useT()
  return (
    <div className="mt-3 rounded-lg border border-border bg-muted/30 p-4">
      <div className="mb-2 flex items-center justify-between gap-2">
        <span className="flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wide text-muted-foreground">
          <HugeiconsIcon
            icon={BookOpen01Icon}
            strokeWidth={2}
            className="size-3.5"
          />
          {t.plan.lessonHeading}
        </span>
        <Button
          variant="ghost"
          size="sm"
          onClick={onRegenerate}
          disabled={regenerating}
        >
          {regenerating ? (
            <Spinner data-icon="inline-start" />
          ) : (
            <HugeiconsIcon
              icon={RefreshIcon}
              strokeWidth={2}
              data-icon="inline-start"
            />
          )}
          {t.plan.regenerateLesson}
        </Button>
      </div>

      <Response>{lesson.content}</Response>

      {lesson.citations.length > 0 ? (
        <div className="mt-3 border-t border-border pt-2">
          <p className="mb-1 text-xs font-medium text-muted-foreground">
            {t.plan.lessonSources}
          </p>
          <ul className="flex flex-col gap-1">
            {lesson.citations.map((citation, index) => (
              <li
                key={`${citation.materialId ?? "m"}-${index}`}
                className="flex items-start gap-1.5 text-xs text-muted-foreground"
              >
                <span className="font-medium text-foreground/70">
                  [{index + 1}]
                </span>
                <span className="truncate">
                  {citation.materialName ?? "material"}
                  {typeof citation.chunkIndex === "number"
                    ? ` · #${citation.chunkIndex}`
                    : ""}
                </span>
              </li>
            ))}
          </ul>
        </div>
      ) : null}
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
