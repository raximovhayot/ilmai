"use client"

import * as React from "react"
import Link from "next/link"
import { useSession } from "next-auth/react"
import { toast } from "sonner"
import { HugeiconsIcon } from "@hugeicons/react"
import {
  CheckmarkCircle02Icon,
  PuzzleIcon,
  RefreshIcon,
  Cancel01Icon,
  Tick02Icon,
} from "@hugeicons/core-free-icons"

import { QuotaStrip } from "@/components/premium/quota-strip"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Progress } from "@/components/ui/progress"
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group"
import { Label } from "@/components/ui/label"
import { Spinner } from "@/components/ui/spinner"
import { Textarea } from "@/components/ui/textarea"
import { ApiClientError } from "@/lib/api"
import { useT } from "@/lib/i18n/provider"
import {
  abandonQuizSession,
  answerQuizQuestion,
  startQuizSession,
  type QuizDifficulty,
  type QuizQuestion,
  type QuizSession,
} from "@/lib/quiz"
import { cn } from "@/lib/utils"

type Props = {
  topicId: string
  topicName: string
  hasMaterials: boolean
  focusMaterialId?: string | null
}

export function QuizPane({ topicId, hasMaterials, focusMaterialId }: Props) {
  const t = useT()
  const { status } = useSession()

  const [difficulty, setDifficulty] = React.useState<QuizDifficulty>("MEDIUM")
  const [questionCount, setQuestionCount] = React.useState(5)
  const [starting, setStarting] = React.useState(false)
  const [error, setError] = React.useState<string | null>(null)
  const [quotaReached, setQuotaReached] = React.useState(false)

  const [sessionState, setSessionState] = React.useState<QuizSession | null>(
    null
  )
  const [questions, setQuestions] = React.useState<QuizQuestion[]>([])
  const [current, setCurrent] = React.useState(0)
  const [pendingAnswer, setPendingAnswer] = React.useState("")
  const [submitting, setSubmitting] = React.useState(false)
  const [showResults, setShowResults] = React.useState(false)
  const autoStartedRef = React.useRef(false)

  const onStart = React.useCallback(async () => {
    if (status !== "authenticated") return
    setStarting(true)
    setError(null)
    setQuotaReached(false)
    try {
      const res = await startQuizSession({
        topicId,
        difficulty,
        questionCount,
      })
      if (!res) throw new Error("empty")
      setSessionState(res)
      setQuestions(res.questions ?? [])
      setCurrent(0)
      setPendingAnswer("")
      setShowResults(false)
    } catch (e) {
      if (e instanceof ApiClientError && e.status === 402) {
        setQuotaReached(true)
      } else {
        setError(t.errors.generic)
        toast.error(t.errors.generic)
      }
    } finally {
      setStarting(false)
    }
  }, [status, difficulty, questionCount, t.errors.generic, topicId])

  React.useEffect(() => {
    if (
      focusMaterialId &&
      hasMaterials &&
      status === "authenticated" &&
      !sessionState &&
      !autoStartedRef.current
    ) {
      autoStartedRef.current = true
      void onStart()
    }
  }, [status, focusMaterialId, hasMaterials, onStart, sessionState])

  const reset = () => {
    setSessionState(null)
    setQuestions([])
    setCurrent(0)
    setPendingAnswer("")
    setError(null)
    setShowResults(false)
  }

  const onAbandon = async () => {
    if (status === "authenticated" && sessionState) {
      try {
        await abandonQuizSession(sessionState.id)
      } catch {
        // ignore abandon failures
      }
    }
    reset()
  }

  const onSubmit = async () => {
    if (status !== "authenticated" || !sessionState) return
    const q = questions[current]
    if (!q || pendingAnswer.trim().length === 0) return
    setSubmitting(true)
    try {
      const updated = await answerQuizQuestion(
        sessionState.id,
        q.id,
        pendingAnswer.trim()
      )
      if (!updated) throw new Error("empty")
      setQuestions((prev) =>
        prev.map((qq) => (qq.id === q.id ? { ...qq, ...updated } : qq))
      )
    } catch {
      toast.error(t.errors.generic)
    } finally {
      setSubmitting(false)
    }
  }

  const onNext = () => {
    setCurrent((c) => Math.min(questions.length - 1, c + 1))
    setPendingAnswer("")
  }

  if (!sessionState) {
    return (
      <div className="flex flex-col gap-3">
        <QuotaStrip kind="quizzes" />
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-base">
              <HugeiconsIcon
                icon={PuzzleIcon}
                strokeWidth={2}
                className="size-5"
              />
              {t.quiz.title}
            </CardTitle>
            <p className="text-sm text-muted-foreground">{t.quiz.subtitle}</p>
          </CardHeader>
          <CardContent className="flex flex-col gap-5">
            {!hasMaterials && (
              <Alert>
                <AlertDescription>{t.quiz.needMaterials}</AlertDescription>
              </Alert>
            )}
            <div className="flex flex-col gap-2">
              <Label>{t.quiz.difficulty}</Label>
              <div className="grid grid-cols-3 gap-2">
                {(["EASY", "MEDIUM", "HARD"] as const).map((d) => (
                  <button
                    key={d}
                    type="button"
                    onClick={() => setDifficulty(d)}
                    className={cn(
                      "rounded-lg border px-3 py-2 text-sm transition-colors",
                      difficulty === d
                        ? "border-primary bg-primary/5 text-foreground"
                        : "border-border text-muted-foreground hover:border-foreground/30"
                    )}
                  >
                    {labelForDifficulty(d, t)}
                  </button>
                ))}
              </div>
            </div>
            <div className="flex flex-col gap-2">
              <Label>{t.quiz.questionCount}</Label>
              <div className="flex flex-wrap gap-2">
                {[3, 5, 8, 10].map((n) => (
                  <button
                    key={n}
                    type="button"
                    onClick={() => setQuestionCount(n)}
                    className={cn(
                      "rounded-md border px-3 py-1.5 text-sm transition-colors",
                      questionCount === n
                        ? "border-primary bg-primary/5"
                        : "border-border text-muted-foreground hover:border-foreground/30"
                    )}
                  >
                    {n}
                  </button>
                ))}
              </div>
            </div>
            {quotaReached && (
              <Alert variant="destructive">
                <AlertDescription className="flex flex-col items-start gap-2">
                  <span>{t.quiz.quotaReached}</span>
                  <Button
                    size="sm"
                    nativeButton={false}
                    render={<Link href="/premium">{t.nav.upgrade}</Link>}
                  />
                </AlertDescription>
              </Alert>
            )}
            {error && (
              <Alert variant="destructive">
                <AlertDescription>{error}</AlertDescription>
              </Alert>
            )}
            <Button
              onClick={() => void onStart()}
              disabled={starting || !hasMaterials}
              className="w-full"
            >
              {starting ? <Spinner data-icon="inline-start" /> : null}
              {t.quiz.startCta}
            </Button>
          </CardContent>
        </Card>
      </div>
    )
  }

  const answeredCount = questions.filter((qq) => qq.userAnswer != null).length
  const allAnswered = questions.length > 0 && answeredCount === questions.length

  if (showResults || sessionState.status === "COMPLETED") {
    return (
      <QuizResults
        session={sessionState}
        questions={questions}
        onRestart={reset}
      />
    )
  }

  const q = questions[current]
  if (!q) return null
  const answered = q.userAnswer != null
  const options = q.options ?? []
  const progress = Math.round(
    ((current + (answered ? 1 : 0)) / questions.length) * 100
  )

  return (
    <Card>
      <CardHeader className="flex flex-row items-center justify-between gap-2">
        <div className="flex flex-col gap-0.5">
          <span className="text-xs tracking-wider text-muted-foreground uppercase">
            {t.quiz.questionOf
              .replace("{current}", String(current + 1))
              .replace("{total}", String(questions.length))}
          </span>
          <CardTitle className="text-base">{q.prompt}</CardTitle>
        </div>
        <div className="flex items-center gap-1">
          <Badge variant="outline">
            {labelForDifficulty(sessionState.difficulty, t)}
          </Badge>
          <Button
            variant="ghost"
            size="icon-sm"
            aria-label={t.common.back}
            onClick={() => void onAbandon()}
          >
            <HugeiconsIcon icon={Cancel01Icon} strokeWidth={2} />
          </Button>
        </div>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        <Progress value={progress} />

        {options.length > 0 ? (
          <RadioGroup
            value={q.userAnswer ?? pendingAnswer}
            onValueChange={(v: string) => !answered && setPendingAnswer(v)}
            disabled={answered}
            className="flex flex-col gap-2"
          >
            {options.map((choice) => {
              const id = `${q.id}-${choice}`
              const isUserAnswer = q.userAnswer === choice
              const isCorrectChoice = q.correctAnswer === choice
              return (
                <Label
                  key={choice}
                  htmlFor={id}
                  className={cn(
                    "flex cursor-pointer items-start gap-3 rounded-lg border border-border bg-card p-3 text-sm transition-colors hover:bg-accent/40",
                    answered &&
                      isCorrectChoice &&
                      "border-emerald-500/40 bg-emerald-500/10",
                    answered &&
                      isUserAnswer &&
                      !q.isCorrect &&
                      "border-destructive/40 bg-destructive/10"
                  )}
                >
                  <RadioGroupItem id={id} value={choice} />
                  <span className="flex-1">{choice}</span>
                  {answered && isCorrectChoice && (
                    <HugeiconsIcon
                      icon={Tick02Icon}
                      strokeWidth={2}
                      className="text-emerald-600 dark:text-emerald-400"
                    />
                  )}
                  {answered && isUserAnswer && !q.isCorrect && (
                    <HugeiconsIcon
                      icon={Cancel01Icon}
                      strokeWidth={2}
                      className="text-destructive"
                    />
                  )}
                </Label>
              )
            })}
          </RadioGroup>
        ) : (
          <Textarea
            value={q.userAnswer ?? pendingAnswer}
            onChange={(e) => !answered && setPendingAnswer(e.target.value)}
            disabled={answered}
            placeholder={t.quiz.yourAnswer}
            rows={3}
          />
        )}

        {answered && (
          <Alert
            className={cn(
              q.isCorrect
                ? "border-emerald-500/30 bg-emerald-500/5 text-foreground"
                : "border-amber-500/30 bg-amber-500/5 text-foreground"
            )}
          >
            <AlertDescription className="flex flex-col gap-1">
              <span className="font-medium">
                {q.isCorrect ? t.quiz.correct : t.quiz.incorrect}
                {!q.isCorrect && q.correctAnswer ? ` — ${q.correctAnswer}` : ""}
              </span>
              {q.explanation || q.feedback ? (
                <span className="text-muted-foreground">
                  {q.explanation ?? q.feedback}
                </span>
              ) : null}
            </AlertDescription>
          </Alert>
        )}

        <div className="flex flex-col gap-2 rounded-lg border border-border p-3 text-xs">
          <span className="font-medium tracking-wider text-muted-foreground uppercase">
            {t.quiz.citationLabel}
          </span>
          {q.materialName ? (
            <p className="font-medium">{q.materialName}</p>
          ) : (
            <p className="flex items-center gap-1.5 font-medium text-amber-700 dark:text-amber-400">
              <HugeiconsIcon
                icon={PuzzleIcon}
                strokeWidth={2}
                className="size-3.5"
              />
              {t.companion.ungrounded}
            </p>
          )}
        </div>

        <div className="sticky bottom-[calc(4.5rem+env(safe-area-inset-bottom))] z-10 -mx-6 -mb-6 flex flex-wrap justify-end gap-2 border-t border-border bg-background/95 px-6 py-3 backdrop-blur supports-[backdrop-filter]:bg-background/80 lg:static lg:m-0 lg:border-0 lg:bg-transparent lg:p-0 lg:backdrop-blur-none">
          {!answered ? (
            <Button
              onClick={onSubmit}
              disabled={submitting || !pendingAnswer.trim()}
              className="w-full lg:w-auto"
            >
              {submitting ? <Spinner data-icon="inline-start" /> : null}
              {t.quiz.submit}
            </Button>
          ) : current < questions.length - 1 ? (
            <Button onClick={onNext} className="w-full lg:w-auto">
              {t.quiz.next}
            </Button>
          ) : (
            <Button
              onClick={() => setShowResults(true)}
              disabled={!allAnswered}
              className="w-full lg:w-auto"
            >
              {t.quiz.finish}
            </Button>
          )}
        </div>
      </CardContent>
    </Card>
  )
}

function labelForDifficulty(
  d: string | null | undefined,
  t: ReturnType<typeof useT>
) {
  switch (d) {
    case "EASY":
      return t.quiz.difficultyEasy
    case "HARD":
      return t.quiz.difficultyHard
    case "MEDIUM":
    default:
      return t.quiz.difficultyMedium
  }
}

function QuizResults({
  session,
  questions,
  onRestart,
}: {
  session: QuizSession
  questions: QuizQuestion[]
  onRestart: () => void
}) {
  const t = useT()
  const total = questions.length
  const correct = questions.filter((q) => q.isCorrect).length
  const pct = total > 0 ? Math.round((correct / total) * 100) : 0
  return (
    <Card>
      <CardHeader className="flex flex-row items-center justify-between gap-2">
        <CardTitle className="flex items-center gap-2 text-base">
          <HugeiconsIcon
            icon={CheckmarkCircle02Icon}
            strokeWidth={2}
            className="size-5 text-emerald-600 dark:text-emerald-400"
          />
          {t.quiz.completedTitle}
        </CardTitle>
        <Button variant="outline" size="sm" onClick={onRestart}>
          <HugeiconsIcon
            icon={RefreshIcon}
            strokeWidth={2}
            data-icon="inline-start"
          />
          {t.quiz.startCta}
        </Button>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        <p className="text-sm text-muted-foreground">
          {t.quiz.completedSubtitle}
        </p>
        <div className="grid grid-cols-3 gap-3">
          <Stat label={t.quiz.score} value={`${pct}%`} />
          <Stat label={t.quiz.correct} value={`${correct} / ${total}`} />
          <Stat
            label={t.quiz.difficulty}
            value={labelForDifficulty(session.difficulty, t)}
          />
        </div>
        <ul className="flex flex-col gap-2">
          {questions.map((q, i) => (
            <li
              key={q.id}
              className="flex items-start gap-2 rounded-lg border border-border p-3 text-sm"
            >
              <span
                className={cn(
                  "mt-0.5 flex size-6 shrink-0 items-center justify-center rounded-full",
                  q.isCorrect
                    ? "bg-emerald-500/15 text-emerald-600 dark:text-emerald-400"
                    : "bg-destructive/15 text-destructive"
                )}
              >
                <HugeiconsIcon
                  icon={q.isCorrect ? Tick02Icon : Cancel01Icon}
                  strokeWidth={2}
                  className="size-3.5"
                />
              </span>
              <div className="flex min-w-0 flex-1 flex-col gap-1">
                <p className="font-medium">
                  {i + 1}. {q.prompt}
                </p>
                <p className="text-xs text-muted-foreground">
                  {t.quiz.yourAnswer}: {q.userAnswer ?? "—"}
                </p>
                {q.feedback && (
                  <p className="text-xs text-muted-foreground">{q.feedback}</p>
                )}
              </div>
            </li>
          ))}
        </ul>
      </CardContent>
    </Card>
  )
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-lg border border-border p-3">
      <p className="text-xs text-muted-foreground">{label}</p>
      <p className="text-lg font-semibold tabular-nums">{value}</p>
    </div>
  )
}
