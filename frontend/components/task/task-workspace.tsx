"use client"

import * as React from "react"
import Link from "next/link"
import { useSession } from "next-auth/react"
import { toast } from "sonner"
import { HugeiconsIcon } from "@hugeicons/react"
import {
  ArrowLeft01Icon,
  ArrowRight01Icon,
  BookOpen01Icon,
  CheckmarkCircle02Icon,
  File01Icon,
  Flag03Icon,
  PuzzleIcon,
  RefreshIcon,
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

const PANEL_MIN_WIDTH = 240
const PANEL_MAX_WIDTH = 560
const PANEL_DEFAULT_WIDTH = 320

function clampPanelWidth(width: number): number {
  return Math.min(PANEL_MAX_WIDTH, Math.max(PANEL_MIN_WIDTH, width))
}

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
  const [sourceOpen, setSourceOpen] = React.useState(false)
  const [chatWidth, setChatWidth] = React.useState(PANEL_DEFAULT_WIDTH)
  const [sourceWidth, setSourceWidth] = React.useState(PANEL_DEFAULT_WIDTH)
  const [seededKey, setSeededKey] = React.useState<string | null>(null)
  const [selection, setSelection] = React.useState<string | null>(null)

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
  const chatVisible = !examLocked
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
    <div
      className="flex flex-col overflow-hidden bg-background"
      style={{ height: "100dvh" }}
    >
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
          <aside
            className="hidden shrink-0 flex-col border-e border-border bg-background md:flex"
            style={{ width: chatWidth }}
          >
            <TaskChatPanel
              taskTitle={step.title}
              lessonContent={lesson?.content}
              selection={selection}
              onClearSelection={() => setSelection(null)}
            />
          </aside>
        ) : null}
        {chatVisible && step ? (
          <ResizeHandle
            ariaLabel={t.wsResize}
            className="hidden md:block"
            onDelta={(delta) =>
              setChatWidth((width) => clampPanelWidth(width + delta))
            }
          />
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
                mode={quizMode}
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
                  onSelectText={setSelection}
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
          <ResizeHandle
            ariaLabel={t.wsResize}
            className="hidden lg:block"
            onDelta={(delta) =>
              setSourceWidth((width) => clampPanelWidth(width - delta))
            }
          />
        ) : null}
        {sourceVisible && step ? (
          <>
            <button
              type="button"
              aria-label={t.wsSourceTitle}
              onClick={() => setSourceOpen(false)}
              className="absolute inset-0 z-10 bg-foreground/40 lg:hidden"
            />
            <aside
              className="absolute inset-y-0 end-0 z-20 flex w-full max-w-sm flex-col border-s border-border bg-background lg:static lg:z-auto lg:w-[var(--src-w)] lg:max-w-none lg:shrink-0"
              style={{ "--src-w": `${sourceWidth}px` } as React.CSSProperties}
            >
              <SourcePanel lesson={lesson} step={step} />
            </aside>
          </>
        ) : null}
      </div>
    </div>
  )
}

function ResizeHandle({
  ariaLabel,
  className,
  onDelta,
}: {
  ariaLabel: string
  className?: string
  onDelta: (inlineDelta: number) => void
}) {
  const cleanupRef = React.useRef<(() => void) | null>(null)

  const start = React.useCallback(
    (event: React.PointerEvent<HTMLDivElement>) => {
      event.preventDefault()
      cleanupRef.current?.()
      let lastX = event.clientX
      const dirSign =
        getComputedStyle(document.documentElement).direction === "rtl" ? -1 : 1

      const move = (moveEvent: PointerEvent) => {
        const delta = (moveEvent.clientX - lastX) * dirSign
        lastX = moveEvent.clientX
        if (delta !== 0) onDelta(delta)
      }
      const stop = () => {
        document.body.style.removeProperty("cursor")
        document.body.style.removeProperty("user-select")
        window.removeEventListener("pointermove", move)
        window.removeEventListener("pointerup", stop)
        cleanupRef.current = null
      }

      cleanupRef.current = stop
      document.body.style.cursor = "col-resize"
      document.body.style.userSelect = "none"
      window.addEventListener("pointermove", move)
      window.addEventListener("pointerup", stop)
    },
    [onDelta]
  )

  React.useEffect(() => () => cleanupRef.current?.(), [])

  return (
    <div
      role="separator"
      aria-orientation="vertical"
      aria-label={ariaLabel}
      onPointerDown={start}
      className={cn(
        "relative z-30 w-1 shrink-0 cursor-col-resize touch-none bg-border transition-colors hover:bg-primary/40",
        className
      )}
    >
      <span className="absolute inset-y-0 -start-1 -end-1" />
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
  onSelectText,
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
  onSelectText: (value: string | null) => void
}) {
  const dict = useT()
  const t = dict.plan

  const captureSelection = React.useCallback(() => {
    const text = window.getSelection?.()?.toString() ?? ""
    const trimmed = text.trim()
    if (trimmed.length > 1) {
      onSelectText(trimmed.slice(0, 2000))
    }
  }, [onSelectText])

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
      <article
        className="flex flex-col gap-3"
        onMouseUp={captureSelection}
        onTouchEnd={captureSelection}
      >
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

type MaterialKind = "pdf" | "audio" | "other"

type MaterialEntry = {
  id: string
  name: string | null
  kind: MaterialKind
  citations: LessonCitation[]
}

function materialKind(citations: LessonCitation[]): MaterialKind {
  if (citations.some(isPdfCitation)) return "pdf"
  if (citations.some(isAudioCitation)) return "audio"
  return "other"
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

  const materials = React.useMemo<MaterialEntry[]>(() => {
    const map = new Map<string, MaterialEntry>()
    for (const material of step.materials) {
      map.set(material.id, {
        id: material.id,
        name: material.title,
        kind: "other",
        citations: [],
      })
    }
    for (const citation of citations) {
      if (!citation.materialId) continue
      const existing = map.get(citation.materialId)
      if (existing) {
        existing.citations.push(citation)
        if (!existing.name) existing.name = citation.materialName
      } else {
        map.set(citation.materialId, {
          id: citation.materialId,
          name: citation.materialName,
          kind: "other",
          citations: [citation],
        })
      }
    }
    const entries = Array.from(map.values())
    for (const entry of entries) {
      entry.kind = materialKind(entry.citations)
    }
    return entries
  }, [citations, step.materials])

  const looseCitations = React.useMemo(
    () => citations.filter((c) => !c.materialId),
    [citations]
  )

  const [selectedId, setSelectedId] = React.useState<string | null>(null)
  const selected = React.useMemo(
    () => materials.find((m) => m.id === selectedId) ?? null,
    [materials, selectedId]
  )

  if (selected) {
    return (
      <div className="flex min-h-0 flex-1 flex-col">
        <header className="flex h-11 shrink-0 items-center gap-2 border-b border-border px-2 text-sm font-semibold">
          <Button
            variant="ghost"
            size="icon"
            className="size-7 shrink-0"
            onClick={() => setSelectedId(null)}
            aria-label={t.wsSourceBack}
          >
            <HugeiconsIcon
              icon={ArrowLeft01Icon}
              strokeWidth={2}
              className="size-4 rtl:rotate-180"
            />
          </Button>
          <span className="truncate">{selected.name ?? t.wsSourceTitle}</span>
        </header>
        <MaterialView material={selected} />
      </div>
    )
  }

  return (
    <div className="flex min-h-0 flex-1 flex-col">
      <header className="flex h-11 shrink-0 items-center gap-2 border-b border-border px-4 text-sm font-semibold">
        <HugeiconsIcon icon={File01Icon} strokeWidth={2} className="size-4" />
        {t.wsSourceMaterials}
      </header>
      <div className="min-h-0 flex-1 overflow-y-auto p-3">
        {materials.length > 0 ? (
          <>
            <p className="mb-2 px-1 text-xs text-muted-foreground">
              {t.wsSourceMaterialsHint}
            </p>
            <ul className="flex flex-col gap-2">
              {materials.map((material) => (
                <li key={material.id}>
                  <button
                    type="button"
                    onClick={() => setSelectedId(material.id)}
                    className="flex w-full items-center gap-2 rounded-lg border border-border bg-muted/20 p-3 text-start text-xs transition-colors hover:bg-muted"
                  >
                    <HugeiconsIcon
                      icon={BookOpen01Icon}
                      strokeWidth={2}
                      className="size-3.5 shrink-0 text-muted-foreground"
                    />
                    <span className="min-w-0 flex-1 truncate font-medium">
                      {material.name ?? t.lessonSources}
                    </span>
                    {material.citations.length > 0 ? (
                      <Badge variant="secondary" className="shrink-0">
                        {t.wsSourceCitationCount.replace(
                          "{count}",
                          String(material.citations.length)
                        )}
                      </Badge>
                    ) : null}
                    <HugeiconsIcon
                      icon={ArrowRight01Icon}
                      strokeWidth={2}
                      className="size-3.5 shrink-0 text-muted-foreground rtl:rotate-180"
                    />
                  </button>
                </li>
              ))}
            </ul>
          </>
        ) : (
          <p className="px-1 py-6 text-center text-xs text-muted-foreground">
            {t.wsSourceEmpty}
          </p>
        )}
        {looseCitations.length > 0 ? (
          <div className="mt-4 border-t border-border pt-3">
            <p className="mb-2 px-1 text-xs font-semibold text-muted-foreground">
              {t.wsSourceCited}
            </p>
            <CitationSnippetList citations={looseCitations} />
          </div>
        ) : null}
      </div>
    </div>
  )
}

function MaterialView({ material }: { material: MaterialEntry }) {
  if (material.kind === "pdf") {
    return <PdfMaterialView material={material} />
  }
  if (material.kind === "audio") {
    return (
      <div className="min-h-0 flex-1 overflow-y-auto p-3">
        <AudioMaterialBlock
          materialId={material.id}
          materialName={null}
          segments={material.citations}
        />
      </div>
    )
  }
  return <GenericMaterialView material={material} />
}

function PdfMaterialView({ material }: { material: MaterialEntry }) {
  const dict = useT()
  const t = dict.plan
  const pdfCitations = React.useMemo(
    () => material.citations.filter(isPdfCitation),
    [material.citations]
  )
  const otherCitations = React.useMemo(
    () => material.citations.filter((c) => !isPdfCitation(c)),
    [material.citations]
  )
  const [activeKey, setActiveKey] = React.useState<string | null>(null)
  const active = React.useMemo(
    () =>
      pdfCitations.find((c, i) => citationKey(c, i) === activeKey) ??
      pdfCitations[0] ??
      null,
    [pdfCitations, activeKey]
  )

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

  const iframeSrc = `${rawMaterialUrl(material.id)}#page=${
    active?.pageStart ?? 1
  }`

  return (
    <>
      {pdfCitations.length > 0 ? (
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
                  active && key === citationKey(active, pdfCitations.indexOf(active))
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
          title={material.name ?? t.wsSourceTitle}
          className="h-full w-full border-0"
        />
      </div>
      {otherCitations.length > 0 ? (
        <div className="max-h-40 shrink-0 overflow-y-auto border-t border-border p-3">
          <p className="mb-2 text-xs font-semibold text-muted-foreground">
            {t.wsSourceCited}
          </p>
          <CitationSnippetList citations={otherCitations} />
        </div>
      ) : null}
    </>
  )
}

function GenericMaterialView({ material }: { material: MaterialEntry }) {
  const dict = useT()
  const t = dict.plan
  const iframeSrc = rawMaterialUrl(material.id)
  return (
    <>
      <div className="min-h-0 flex-1">
        <iframe
          src={iframeSrc}
          title={material.name ?? t.wsSourceTitle}
          className="h-full w-full border-0"
        />
      </div>
      {material.citations.length > 0 ? (
        <div className="max-h-40 shrink-0 overflow-y-auto border-t border-border p-3">
          <p className="mb-2 text-xs font-semibold text-muted-foreground">
            {t.wsSourceCited}
          </p>
          <CitationSnippetList citations={material.citations} />
        </div>
      ) : null}
    </>
  )
}

function CitationSnippetList({ citations }: { citations: LessonCitation[] }) {
  return (
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
