"use client"

import * as React from "react"
import { HugeiconsIcon } from "@hugeicons/react"
import {
  BookOpen01Icon,
  CheckmarkCircle02Icon,
  PuzzleIcon,
  RoadIcon,
} from "@hugeicons/core-free-icons"

import { Button } from "@/components/ui/button"
import { Spinner } from "@/components/ui/spinner"
import { generatePlan, getPlan, type LearningPlan } from "@/lib/plan"
import { useT } from "@/lib/i18n/provider"

type Props = {
  onBack: () => void
  onNext: () => void
}

type Phase = "loading" | "ready" | "empty" | "failed"

export function PlanStep({ onBack, onNext }: Props) {
  const t = useT()
  const c = t.onboarding.plan
  const [phase, setPhase] = React.useState<Phase>("loading")
  const [plan, setPlan] = React.useState<LearningPlan | null>(null)
  const cancelledRef = React.useRef(false)

  function runLoad() {
    void (async () => {
      try {
        const generated = (await generatePlan()) ?? (await getPlan())
        if (cancelledRef.current) return
        if (generated) {
          setPlan(generated)
          setPhase("ready")
        } else {
          setPhase("empty")
        }
      } catch {
        if (!cancelledRef.current) setPhase("failed")
      }
    })()
  }

  function retry() {
    setPhase("loading")
    runLoad()
  }

  React.useEffect(() => {
    cancelledRef.current = false
    runLoad()
    return () => {
      cancelledRef.current = true
    }
  }, [])

  return (
    <div className="space-y-6">
      <div className="space-y-2 text-center">
        <h1 className="text-2xl font-semibold tracking-tight">{c.title}</h1>
        <p className="text-muted-foreground">{c.subtitle}</p>
      </div>

      {phase === "loading" && (
        <div className="flex flex-col items-center gap-3 rounded-3xl border border-dashed border-input px-6 py-12 text-center">
          <Spinner className="size-6" />
          <p className="text-sm font-medium">{c.generating}</p>
          <p className="text-xs text-muted-foreground">{c.generatingHint}</p>
        </div>
      )}

      {phase === "failed" && (
        <div className="flex flex-col items-center gap-3 rounded-3xl border border-dashed border-input px-6 py-12 text-center">
          <p className="text-sm font-medium">{c.failed}</p>
          <Button type="button" variant="outline" onClick={retry}>
            {c.retry}
          </Button>
        </div>
      )}

      {phase === "empty" && (
        <div className="flex flex-col items-center gap-2 rounded-3xl border border-dashed border-input px-6 py-12 text-center">
          <HugeiconsIcon
            icon={RoadIcon}
            strokeWidth={2}
            className="size-7 text-muted-foreground"
          />
          <p className="text-sm font-medium">{c.emptyTitle}</p>
          <p className="text-xs text-muted-foreground">{c.emptyHint}</p>
        </div>
      )}

      {phase === "ready" && plan && (
        <div className="space-y-4">
          <div className="rounded-2xl border border-border bg-card p-4">
            <div className="flex items-center gap-2">
              <HugeiconsIcon
                icon={RoadIcon}
                strokeWidth={2}
                className="size-5 text-primary"
              />
              <span className="min-w-0 flex-1 truncate font-medium">
                {plan.goal ?? c.title}
              </span>
            </div>
            <p className="mt-1 text-xs text-muted-foreground">
              {c.daysLabel.replace("{days}", String(plan.daysTotal))}
              {" · "}
              {c.stepsLabel.replace("{count}", String(plan.steps.length))}
            </p>
          </div>

          <ul className="flex flex-col gap-1.5">
            {plan.steps.slice(0, 5).map((step) => (
              <li
                key={`${step.dayIndex}-${step.orderInDay}`}
                className="flex items-center gap-3 rounded-lg border border-border bg-card p-2.5 text-sm"
              >
                <span className="flex size-7 shrink-0 items-center justify-center rounded-md bg-muted">
                  <HugeiconsIcon
                    icon={
                      step.activity === "READ"
                        ? BookOpen01Icon
                        : step.activity === "QUIZ"
                          ? PuzzleIcon
                          : CheckmarkCircle02Icon
                    }
                    strokeWidth={2}
                    className="size-4"
                  />
                </span>
                <span className="min-w-0 flex-1 truncate">{step.title}</span>
              </li>
            ))}
          </ul>
        </div>
      )}

      <div className="flex items-center justify-between gap-3 pt-2">
        <Button type="button" variant="ghost" onClick={onBack}>
          {t.onboarding.back}
        </Button>
        <Button type="button" onClick={onNext} disabled={phase === "loading"}>
          {c.continue}
        </Button>
      </div>
    </div>
  )
}
