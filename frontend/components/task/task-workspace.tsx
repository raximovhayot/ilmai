"use client"

import * as React from "react"
import Link from "next/link"
import { useSession } from "next-auth/react"
import { toast } from "sonner"
import { HugeiconsIcon } from "@hugeicons/react"
import {
  ArrowLeft01Icon,
  BookOpen01Icon,
  CheckmarkCircle02Icon,
  File01Icon,
  Flag03Icon,
  PuzzleIcon,
  RefreshIcon,
  SparklesIcon,
} from "@hugeicons/core-free-icons"

import { Response } from "@/components/ai-elements/response"
import { TaskChatPanel } from "@/components/task/task-chat-panel"
import { TaskQuiz } from "@/components/task/task-quiz"
import { Badge } from "@/components/ui/badge"
import { Button, buttonVariants } from "@/components/ui/button"
import { Spinner } from "@/components/ui/spinner"
import { Textarea } from "@/components/ui/textarea"
import {
  completePlanTask,
  formatAudioTimestamp,
  generateTaskLesson,
  getPlans,
  isAudioCitation,
  isPdfCitation,
  rawMaterialUrl,
  type LearningPlan,
  type LessonCitation,
  type PlanActivity,
  type PlanStep,
  type StepLesson,
} from "@/lib/plan"
import { useT } from "@/lib/i18n/provider"
import { cn } from "@/lib/utils"

type QuizMode = "practice" | "exam"

function citationKey(citation: LessonCitation, index: number): string {
  return `${citation.materialId ?? "c"}-${citation.chunkIndex ?? index}-${index}`
}

function activityIcon(activity: PlanActivity) {
  if (activity === "QUIZ") return PuzzleIcon
  if (activity === "INDEPENDENT") return Flag03Icon
  return BookOpen01Icon
}

export function TaskWorkspace({
  planId,
  dayIndex,
  orderInDay,
}: {
  planId: string
  dayIndex: number
  orderInDay: number
}) {
  const dict = useT()
  const t = dict.plan
  const { status } = useSession()

  const [plan, setPlan] = React.useState<LearningPlan | null>(null)
  const [loaded, setLoaded] = React.useState(false)
  const [lesson, setLesson] = React.useState<StepLesson | null>(null)
  const [lessonLoading, setLessonLoading] = React.useState(false)
  const [completing, setCompleting] = React.useState(false)
  const [reflection, setReflection] = React.useState("")
  const [quizMode, setQuizMode] = React.useState<QuizMode>("practice")
  const [chatOpen, setChatOpen] = React.useState(false)
  const [sourceOpen, setSourceOpen] = React.useState(false)
  const [seededKey, setSeededKey] = React.useState<string | null>(null)

  const step = React.useMemo<PlanStep | null>(() => {
    if (!plan) return null
    return (
      plan.steps.find(
        (s) => s.dayIndex === dayIndex && s.orderInDay === orderInDay
      ) ?? null
    )
  }, [plan, dayIndex, orderInDay])

  const isQuiz = step?.activity === "QUIZ"
  const isIndependent = step?.activity === "INDEPENDENT"
  const quizTopicId = React.useMemo(
    () => step?.materials?.find((m) => m.topicId)?.topicId ?? null,
    [step]
  )
  const isLessonActivity =
    step?.activity === "READ" || step?.activity === "REVIEW"
  const examLocked = isQuiz && quizMode === "exam"
  const chatVisible = chatOpen && !examLocked
  const sourceVisible = sourceOpen && !examLocked

  React.useEffect(() => {
    if (status !== "authenticated") return
    let cancelled = false
    void (async () => {
      try {
        const plans = await getPlans()
        if (cancelled) return
        const found = plans.find((p) => p.id === planId) ?? null
        setPlan(found)
        setLoaded(true)
        const target = found?.steps.find(
          (s) => s.dayIndex === dayIndex && s.orderInDay === orderInDay
        )
        if (
          target &&
          (target.activity === "READ" || target.activity === "REVIEW")
        ) {
          setLessonLoading(true)
          try {
            const next = await generateTaskLesson(planId, dayIndex, orderInDay)
            if (!cancelled && next) setLesson(next)
          } catch {
            if (!cancelled) toast.error(dict.errors.generic)
          } finally {
            if (!cancelled) setLessonLoading(false)
          }
        }
      } catch {
        if (!cancelled) setLoaded(true)
      }
    })()
    return () => {
      cancelled = true
    }
  }, [status, planId, dayIndex, orderInDay, dict.errors.generic])

  const stepKey = step ? `${step.dayIndex}:${step.orderInDay}` : null
  if (step && stepKey !== seededKey) {
    setSeededKey(stepKey)
    setReflection(step.reflectionNote ?? "")
  }

  const loadLesson = React.useCallback(
    async (regenerate: boolean) => {
      if (status !== "authenticated") return
      setLessonLoading(true)
      try {
        const next = await generateTaskLesson(
          planId,
          dayIndex,
          orderInDay,
          regenerate
        )
        if (next) setLesson(next)
      } catch {
        toast.error(dict.errors.generic)
      } finally {
        setLessonLoading(false)
      }
    },
    [status, planId, dayIndex, orderInDay, dict.errors.generic]
  )

  const lessonReady = !!step?.hasLesson || !!lesson
  const reflectionValid = reflection.trim().length > 0
  const canComplete = isIndependent
    ? reflectionValid
    : isLessonActivity
      ? lessonReady
      : true

  const handleComplete = React.useCallback(async () => {
    if (status !== "authenticated" || !step || completing) return
    setCompleting(true)
    try {
      const fresh = await completePlanTask(
        planId,
        dayIndex,
        orderInDay,
        isIndependent ? { reflectionNote: reflection.trim() } : undefined
      )
      if (fresh) setPlan(fresh)
    } catch {
      toast.error(dict.errors.generic)
    } finally {
      setCompleting(false)
    }
  }, [
    status,
    step,
    completing,
    planId,
    dayIndex,
    orderInDay,
    isIndependent,
    reflection,
    dict.errors.generic,
  ])

  const activityLabel = step
    ? step.activity === "QUIZ"
      ? t.actionQuiz
      : step.activity === "REVIEW"
        ? t.actionReview
        : step.activity === "INDEPENDENT"
          ? t.actionIndependent
          : t.actionRead
    : ""

  return (
    <div className="flex h-dvh flex-col bg-background">
      <header className="flex h-14 shrink-0 items-center gap-3 border-b border-border px-3 sm:px-4">
        <Link
          href="/plan"
          className={buttonVariants({ variant: "ghost", size: "sm" })}
        >
          <HugeiconsIcon
            icon={ArrowLeft01Icon}
            strokeWidth={2}
            data-icon="inline-start"
            className="rtl:rotate-180"
          />
          {t.wsBackToPlan}
        </Link>

        <div className="flex min-w-0 flex-1 items-center gap-2">
          {step ? (
            <>
              <HugeiconsIcon
                icon={activityIcon(step.activity)}
                strokeWidth={2}
                className="size-4 shrink-0 text-muted-foreground"
              />
              <span
                className="truncate text-sm font-semibold"
                title={step.title}
              >
                {step.title}
              </span>
              <Badge
                variant="secondary"
                className="hidden shrink-0 sm:inline-flex"
              >
                {activityLabel}
              </Badge>
            </>
          ) : null}
        </div>

        {isQuiz ? (
          <div className="flex shrink-0 items-center gap-1 rounded-lg border border-border p-0.5">
            {(["practice", "exam"] as QuizMode[]).map((mode) => (
              <button
                key={mode}
                type="button"
                onClick={() => setQuizMode(mode)}
                aria-pressed={quizMode === mode}
                className={cn(
                  "rounded-md px-2.5 py-1 text-xs font-medium transition-colors",
                  quizMode === mode
                    ? "bg-primary text-primary-foreground"
                    : "text-muted-foreground hover:text-foreground"
                )}
              >
                {mode === "practice" ? t.wsModePractice : t.wsModeExam}
              </button>
            ))}
          </div>
        ) : null}

        <Button
          variant={chatVisible ? "secondary" : "ghost"}
          size="sm"
          onClick={() => setChatOpen((v) => !v)}
          disabled={examLocked}
          aria-label={chatVisible ? t.wsHideChat : t.wsShowChat}
        >
          <HugeiconsIcon
            icon={SparklesIcon}
            strokeWidth={2}
            data-icon="inline-start"
          />
          <span className="hidden sm:inline">{t.wsChatTitle}</span>
        </Button>
        <Button
          variant={sourceVisible ? "secondary" : "ghost"}
          size="sm"
          onClick={() => setSourceOpen((v) => !v)}
          disabled={examLocked}
          aria-label={t.wsSourceTitle}
        >
          <HugeiconsIcon
            icon={File01Icon}
            strokeWidth={2}
            data-icon="inline-start"
          />
          <span className="hidden sm:inline">{t.wsSourceTitle}</span>
        </Button>
      </header>

      <div className="relative flex min-h-0 flex-1">
        {chatVisible && step ? (
          <>
            <button
              type="button"
              aria-label={t.wsHideChat}
              onClick={() => setChatOpen(false)}
              className="absolute inset-0 z-10 bg-foreground/40 md:hidden"
            />
            <aside className="absolute inset-y-0 start-0 z-20 flex w-full max-w-sm flex-col border-e border-border bg-background md:static md:z-auto md:w-80 md:max-w-none md:shrink-0">
              <TaskChatPanel taskTitle={step.title} />
            </aside>
          </>
        ) : null}

        <main className="min-w-0 flex-1 overflow-y-auto">
          <div className="mx-auto flex max-w-3xl flex-col gap-4 p-4 sm:p-6">
            {!loaded ? (
              <div className="flex items-center justify-center py-20 text-muted-foreground">
                <Spinner />
              </div>
            ) : !step ? (
              <p className="py-20 text-center text-sm text-muted-foreground">
                {t.wsContentEmpty}
              </p>
            ) : isQuiz ? (
              <TaskQuiz
                planId={planId}
                step={step}
                topicId={quizTopicId}
                onCompleted={setPlan}
              />
            ) : (
              <>
                <TaskContent
                  step={step}
                  lesson={lesson}
                  lessonLoading={lessonLoading}
                  isLessonActivity={isLessonActivity}
                  isIndependent={!!isIndependent}
                  isQuiz={!!isQuiz}
                  examLocked={!!examLocked}
                  reflection={reflection}
                  onReflectionChange={setReflection}
                  onRegenerate={() => void loadLesson(true)}
                />

                <div className="flex items-center justify-end gap-3 border-t border-border pt-4">
                  {step.done ? (
                    <span className="inline-flex items-center gap-1.5 text-sm font-medium text-emerald-600 dark:text-emerald-400">
                      <HugeiconsIcon
                        icon={CheckmarkCircle02Icon}
                        strokeWidth={2}
                        className="size-4"
                      />
                      {t.completed}
                    </span>
                  ) : (
                    <Button
                      onClick={() => void handleComplete()}
                      disabled={completing || !canComplete}
                    >
                      {completing ? (
                        <Spinner data-icon="inline-start" />
                      ) : (
                        <HugeiconsIcon
                          icon={CheckmarkCircle02Icon}
                          strokeWidth={2}
                          data-icon="inline-start"
                        />
                      )}
                      {t.markDone}
                    </Button>
                  )}
                </div>
              </>
            )}
          </div>
        </main>

        {sourceVisible && step ? (
          <>
            <button
              type="button"
              aria-label={t.wsSourceTitle}
              onClick={() => setSourceOpen(false)}
              className="absolute inset-0 z-10 bg-foreground/40 lg:hidden"
            />
            <aside className="absolute inset-y-0 end-0 z-20 flex w-full max-w-sm flex-col border-s border-border bg-background lg:static lg:z-auto lg:w-80 lg:max-w-none lg:shrink-0">
              <SourcePanel lesson={lesson} step={step} />
            </aside>
          </>
        ) : null}
      </div>
    </div>
  )
}

function TaskContent({
  step,
  lesson,
  lessonLoading,
  isLessonActivity,
  isIndependent,
  isQuiz,
  examLocked,
  reflection,
  onReflectionChange,
  onRegenerate,
}: {
  step: PlanStep
  lesson: StepLesson | null
  lessonLoading: boolean
  isLessonActivity: boolean
  isIndependent: boolean
  isQuiz: boolean
  examLocked: boolean
  reflection: string
  onReflectionChange: (value: string) => void
  onRegenerate: () => void
}) {
  const dict = useT()
  const t = dict.plan

  if (isLessonActivity) {
    if (lessonLoading && !lesson) {
      return (
        <div className="flex items-center gap-2 py-16 text-sm text-muted-foreground">
          <Spinner />
          {t.lessonGenerating}
        </div>
      )
    }
    if (!lesson) {
      return (
        <p className="py-16 text-center text-sm text-muted-foreground">
          {t.wsContentEmpty}
        </p>
      )
    }
    return (
      <article className="flex flex-col gap-3">
        <div className="flex items-center justify-between gap-2">
          <h1 className="text-xl font-semibold tracking-tight">
            {lesson.title}
          </h1>
          <Button
            variant="ghost"
            size="sm"
            onClick={onRegenerate}
            disabled={lessonLoading}
          >
            {lessonLoading ? (
              <Spinner data-icon="inline-start" />
            ) : (
              <HugeiconsIcon
                icon={RefreshIcon}
                strokeWidth={2}
                data-icon="inline-start"
              />
            )}
            {t.regenerateLesson}
          </Button>
        </div>
        <Response>{lesson.content}</Response>
      </article>
    )
  }

  if (isIndependent) {
    return (
      <div className="flex flex-col gap-4">
        <article className="flex flex-col gap-2">
          <h1 className="text-xl font-semibold tracking-tight">{step.title}</h1>
          {step.note ? (
            <p className="text-sm text-muted-foreground">{step.note}</p>
          ) : null}
        </article>
        <div className="flex flex-col gap-1.5">
          <label className="text-sm font-medium">{t.reflectionLabel}</label>
          <Textarea
            value={reflection}
            onChange={(event) => onReflectionChange(event.target.value)}
            placeholder={t.reflectionPlaceholder}
            rows={5}
          />
          {!step.done && reflection.trim().length === 0 ? (
            <p className="text-xs text-muted-foreground">
              {t.lockIndependentHint}
            </p>
          ) : null}
        </div>
      </div>
    )
  }

  if (isQuiz) {
    return (
      <div className="flex flex-col gap-3">
        <h1 className="text-xl font-semibold tracking-tight">{step.title}</h1>
        {step.note ? (
          <p className="text-sm text-muted-foreground">{step.note}</p>
        ) : null}
        <div
          className={cn(
            "rounded-xl border p-4 text-sm",
            examLocked
              ? "border-amber-500/30 bg-amber-500/5 text-amber-700 dark:text-amber-400"
              : "border-border bg-muted/30 text-muted-foreground"
          )}
        >
          <p className="flex items-center gap-2 font-medium">
            <HugeiconsIcon
              icon={PuzzleIcon}
              strokeWidth={2}
              className="size-4 shrink-0"
            />
            {examLocked ? t.wsQuizExamHint : t.wsQuizPracticeHint}
          </p>
        </div>
      </div>
    )
  }

  return null
}

function SourcePanel({
  lesson,
  step,
}: {
  lesson: StepLesson | null
  step: PlanStep
}) {
  const dict = useT()
  const t = dict.plan
  const citations = React.useMemo(() => lesson?.citations ?? [], [lesson])
  const pdfCitations = React.useMemo(
    () => citations.filter(isPdfCitation),
    [citations]
  )
  const audioCitations = React.useMemo(
    () => citations.filter((c) => !isPdfCitation(c) && isAudioCitation(c)),
    [citations]
  )
  const otherCitations = React.useMemo(
    () => citations.filter((c) => !isPdfCitation(c) && !isAudioCitation(c)),
    [citations]
  )

  const [activeKey, setActiveKey] = React.useState<string | null>(null)
  const active = React.useMemo(() => {
    if (pdfCitations.length === 0) return null
    return (
      pdfCitations.find((c, i) => citationKey(c, i) === activeKey) ??
      pdfCitations[0]
    )
  }, [pdfCitations, activeKey])

  const pageLabel = React.useCallback(
    (citation: LessonCitation) => {
      const start = citation.pageStart
      if (typeof start !== "number") {
        return citation.materialName ?? t.wsSourceTitle
      }
      const end = citation.pageEnd ?? start
      if (end !== start) {
        return t.wsSourcePages
          .replace("{start}", String(start))
          .replace("{end}", String(end))
      }
      return t.wsSourcePage.replace("{page}", String(start))
    },
    [t]
  )

  if (pdfCitations.length > 0 && active && active.materialId) {
    const iframeSrc = `${rawMaterialUrl(active.materialId)}#page=${
      active.pageStart ?? 1
    }`
    return (
      <div className="flex min-h-0 flex-1 flex-col">
        <header className="flex h-11 shrink-0 items-center gap-2 border-b border-border px-4 text-sm font-semibold">
          <HugeiconsIcon icon={File01Icon} strokeWidth={2} className="size-4" />
          <span className="truncate">
            {active.materialName ?? t.wsSourceTitle}
          </span>
        </header>
        {pdfCitations.length > 1 ? (
          <div className="flex shrink-0 flex-wrap gap-1.5 border-b border-border px-3 py-2">
            {pdfCitations.map((citation, index) => {
              const key = citationKey(citation, index)
              return (
                <button
                  key={key}
                  type="button"
                  onClick={() => setActiveKey(key)}
                  className={cn(
                    "rounded-full border px-2.5 py-1 text-xs transition-colors",
                    key === citationKey(active, pdfCitations.indexOf(active))
                      ? "border-primary bg-primary/10 text-primary"
                      : "border-border text-muted-foreground hover:bg-muted"
                  )}
                >
                  {pageLabel(citation)}
                </button>
              )
            })}
          </div>
        ) : null}
        <div className="min-h-0 flex-1">
          <iframe
            key={iframeSrc}
            src={iframeSrc}
            title={active.materialName ?? t.wsSourceTitle}
            className="h-full w-full border-0"
          />
        </div>
        {otherCitations.length > 0 ? (
          <div className="max-h-40 shrink-0 overflow-y-auto border-t border-border p-3">
            <p className="mb-2 text-xs font-semibold text-muted-foreground">
              {t.wsSourceCited}
            </p>
            <ul className="flex flex-col gap-2">
              {otherCitations.map((citation, index) => (
                <li
                  key={`${citation.materialId ?? "c"}-${index}`}
                  className="rounded-lg border border-border bg-muted/20 p-3"
                >
                  {citation.materialName ? (
                    <p className="mb-1 truncate text-xs font-semibold">
                      {citation.materialName}
                    </p>
                  ) : null}
                  {citation.snippet ? (
                    <p className="text-xs leading-relaxed text-muted-foreground">
                      {citation.snippet}
                    </p>
                  ) : null}
                </li>
              ))}
            </ul>
          </div>
        ) : null}
      </div>
    )
  }

  if (audioCitations.length > 0) {
    return (
      <AudioSourceView
        citations={audioCitations}
        otherCitations={otherCitations}
      />
    )
  }

  return (
    <div className="flex min-h-0 flex-1 flex-col">
      <header className="flex h-11 shrink-0 items-center gap-2 border-b border-border px-4 text-sm font-semibold">
        <HugeiconsIcon icon={File01Icon} strokeWidth={2} className="size-4" />
        {t.wsSourceTitle}
      </header>
      <div className="min-h-0 flex-1 overflow-y-auto p-3">
        {citations.length > 0 ? (
          <ul className="flex flex-col gap-2">
            {citations.map((citation, index) => (
              <li
                key={`${citation.materialId ?? "c"}-${index}`}
                className="rounded-lg border border-border bg-muted/20 p-3"
              >
                {citation.materialName ? (
                  <p className="mb-1 truncate text-xs font-semibold">
                    {citation.materialName}
                  </p>
                ) : null}
                {citation.snippet ? (
                  <p className="text-xs leading-relaxed text-muted-foreground">
                    {citation.snippet}
                  </p>
                ) : null}
              </li>
            ))}
          </ul>
        ) : step.materials.length > 0 ? (
          <ul className="flex flex-col gap-2">
            {step.materials.map((material) => (
              <li
                key={material.id}
                className="flex items-center gap-2 rounded-lg border border-border bg-muted/20 p-3 text-xs"
              >
                <HugeiconsIcon
                  icon={BookOpen01Icon}
                  strokeWidth={2}
                  className="size-3.5 shrink-0 text-muted-foreground"
                />
                <span className="truncate">
                  {material.title ?? t.lessonSources}
                </span>
              </li>
            ))}
          </ul>
        ) : (
          <p className="px-1 py-6 text-center text-xs text-muted-foreground">
            {t.wsSourceEmpty}
          </p>
        )}
      </div>
    </div>
  )
}

function AudioSourceView({
  citations,
  otherCitations,
}: {
  citations: LessonCitation[]
  otherCitations: LessonCitation[]
}) {
  const dict = useT()
  const t = dict.plan

  const groups = React.useMemo(() => {
    const map = new Map<
      string,
      { materialName: string | null; items: LessonCitation[] }
    >()
    for (const citation of citations) {
      if (!citation.materialId) continue
      const group = map.get(citation.materialId) ?? {
        materialName: citation.materialName,
        items: [],
      }
      group.items.push(citation)
      map.set(citation.materialId, group)
    }
    return Array.from(map.entries())
  }, [citations])

  return (
    <div className="flex min-h-0 flex-1 flex-col">
      <header className="flex h-11 shrink-0 items-center gap-2 border-b border-border px-4 text-sm font-semibold">
        <HugeiconsIcon icon={File01Icon} strokeWidth={2} className="size-4" />
        {t.wsSourceTitle}
      </header>
      <div className="min-h-0 flex-1 overflow-y-auto p-3">
        <div className="flex flex-col gap-4">
          {groups.map(([materialId, group]) => (
            <AudioMaterialBlock
              key={materialId}
              materialId={materialId}
              materialName={group.materialName}
              segments={group.items}
            />
          ))}
        </div>
        {otherCitations.length > 0 ? (
          <div className="mt-4 border-t border-border pt-3">
            <p className="mb-2 text-xs font-semibold text-muted-foreground">
              {t.wsSourceCited}
            </p>
            <ul className="flex flex-col gap-2">
              {otherCitations.map((citation, index) => (
                <li
                  key={`${citation.materialId ?? "c"}-${index}`}
                  className="rounded-lg border border-border bg-muted/20 p-3"
                >
                  {citation.materialName ? (
                    <p className="mb-1 truncate text-xs font-semibold">
                      {citation.materialName}
                    </p>
                  ) : null}
                  {citation.snippet ? (
                    <p className="text-xs leading-relaxed text-muted-foreground">
                      {citation.snippet}
                    </p>
                  ) : null}
                </li>
              ))}
            </ul>
          </div>
        ) : null}
      </div>
    </div>
  )
}

function AudioMaterialBlock({
  materialId,
  materialName,
  segments,
}: {
  materialId: string
  materialName: string | null
  segments: LessonCitation[]
}) {
  const dict = useT()
  const t = dict.plan
  const audioRef = React.useRef<HTMLAudioElement>(null)

  const seek = React.useCallback((startMs: number | null | undefined) => {
    const el = audioRef.current
    if (!el || typeof startMs !== "number") return
    el.currentTime = Math.max(0, startMs / 1000)
    void el.play().catch(() => {})
  }, [])

  return (
    <div className="rounded-lg border border-border bg-muted/20 p-3">
      {materialName ? (
        <p className="mb-2 truncate text-xs font-semibold">{materialName}</p>
      ) : null}
      <audio
        ref={audioRef}
        src={rawMaterialUrl(materialId)}
        controls
        preload="metadata"
        className="w-full"
      />
      <div className="mt-2 flex flex-wrap gap-1.5">
        {segments.map((segment, index) => {
          if (typeof segment.audioStartMs !== "number") return null
          const start = formatAudioTimestamp(segment.audioStartMs)
          const end = formatAudioTimestamp(
            segment.audioEndMs ?? segment.audioStartMs
          )
          const label = t.wsSourceSegment
            .replace("{start}", start)
            .replace("{end}", end)
          return (
            <button
              key={`${segment.chunkIndex ?? index}-${index}`}
              type="button"
              title={t.wsSourcePlay}
              onClick={() => seek(segment.audioStartMs)}
              className="rounded-full border border-border px-2.5 py-1 text-xs text-muted-foreground transition-colors hover:bg-muted"
            >
              {label}
            </button>
          )
        })}
      </div>
    </div>
  )
}
