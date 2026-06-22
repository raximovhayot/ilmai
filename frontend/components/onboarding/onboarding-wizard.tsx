"use client"

import * as React from "react"
import { useRouter } from "next/navigation"

import { Button } from "@/components/ui/button"
import { Progress } from "@/components/ui/progress"
import { GoalStep, type GoalDraft } from "@/components/onboarding/goal-step"
import { PlanStep } from "@/components/onboarding/plan-step"
import { PremiumStep } from "@/components/onboarding/premium-step"
import { TelegramStep } from "@/components/onboarding/telegram-step"
import { UploadStep } from "@/components/onboarding/upload-step"
import { WelcomeStep } from "@/components/onboarding/welcome-step"
import { useT } from "@/lib/i18n/provider"
import { saveOnboarding } from "@/lib/onboarding"

const STEP_COUNT = 6
const SKIPPABLE_STEPS = new Set([0, 1])

export function OnboardingWizard() {
  const t = useT()
  const router = useRouter()
  const [step, setStep] = React.useState(0)
  const [saving, setSaving] = React.useState(false)
  const [goal, setGoal] = React.useState<GoalDraft>({
    goal: "",
    targetDate: "",
    dailyStudyMinutes: null,
  })

  function goalPayload() {
    return {
      goal: goal.goal.trim().length > 0 ? goal.goal.trim() : null,
      targetDate: goal.targetDate.length > 0 ? goal.targetDate : null,
      dailyStudyMinutes: goal.dailyStudyMinutes,
    }
  }

  async function submitGoal() {
    await saveOnboarding(goalPayload())
    setStep(2)
  }

  function skipStep() {
    setStep((current) => Math.min(current + 1, STEP_COUNT - 1))
  }

  async function finish(destination: string) {
    if (saving) return
    setSaving(true)
    try {
      await saveOnboarding({ ...goalPayload(), onboardingPassed: true })
    } catch {
      // ignore — still leave onboarding
    }
    router.replace(destination)
  }

  const progress = ((step + 1) / STEP_COUNT) * 100

  return (
    <div className="mx-auto flex min-h-dvh w-full max-w-lg flex-col px-4 py-6">
      <div className="mb-8 flex items-center gap-4">
        <Progress value={progress} className="flex-1" aria-hidden />
        <span className="shrink-0 text-xs text-muted-foreground tabular-nums">
          {t.onboarding.stepOf
            .replace("{current}", String(step + 1))
            .replace("{total}", String(STEP_COUNT))}
        </span>
      </div>

      <div className="flex flex-1 flex-col justify-center">
        {step === 0 && <WelcomeStep onNext={() => setStep(1)} />}
        {step === 1 && (
          <GoalStep
            value={goal}
            onChange={setGoal}
            onBack={() => setStep(0)}
            onNext={submitGoal}
          />
        )}
        {step === 2 && (
          <UploadStep
            onBack={() => setStep(1)}
            onReady={() => setStep(3)}
            onUpgrade={() => void finish("/premium")}
          />
        )}
        {step === 3 && (
          <PlanStep onBack={() => setStep(2)} onNext={() => setStep(4)} />
        )}
        {step === 4 && (
          <TelegramStep onBack={() => setStep(3)} onNext={() => setStep(5)} />
        )}
        {step === 5 && (
          <PremiumStep
            saving={saving}
            onBack={() => setStep(4)}
            onGoPremium={() => void finish("/premium")}
            onMaybeLater={() => void finish("/home")}
          />
        )}
      </div>

      {SKIPPABLE_STEPS.has(step) && (
        <div className="mt-6 flex justify-center">
          <Button
            type="button"
            variant="ghost"
            size="sm"
            className="text-muted-foreground"
            onClick={skipStep}
            disabled={saving}
          >
            {t.onboarding.skip}
          </Button>
        </div>
      )}
    </div>
  )
}
