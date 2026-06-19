"use client"

import { HugeiconsIcon } from "@hugeicons/react"
import { SparklesIcon } from "@hugeicons/core-free-icons"

import { Button } from "@/components/ui/button"
import { LANGUAGES, LANGUAGE_LABELS, type Lang } from "@/lib/i18n/dictionary"
import { useLanguage } from "@/lib/i18n/provider"
import { cn } from "@/lib/utils"

type Props = {
  onNext: () => void
}

export function WelcomeStep({ onNext }: Props) {
  const { lang, setLang, t } = useLanguage()
  const c = t.onboarding.welcome

  return (
    <div className="flex flex-col items-center gap-8 text-center">
      <span className="flex size-16 items-center justify-center rounded-3xl bg-primary/10 text-primary">
        <HugeiconsIcon icon={SparklesIcon} strokeWidth={2} className="size-7" />
      </span>
      <div className="space-y-3">
        <h1 className="text-2xl font-semibold tracking-tight">{c.title}</h1>
        <p className="mx-auto max-w-md text-balance text-muted-foreground">
          {c.subtitle}
        </p>
      </div>

      <div className="w-full space-y-2">
        <p className="text-sm font-medium text-muted-foreground">
          {c.languageLabel}
        </p>
        <div className="flex flex-wrap justify-center gap-2">
          {LANGUAGES.map((code: Lang) => (
            <Button
              key={code}
              type="button"
              variant={code === lang ? "default" : "outline"}
              onClick={() => setLang(code)}
              aria-pressed={code === lang}
            >
              {LANGUAGE_LABELS[code]}
            </Button>
          ))}
        </div>
      </div>

      <Button
        type="button"
        size="lg"
        className={cn("w-full sm:w-auto")}
        onClick={onNext}
      >
        {c.cta}
      </Button>
    </div>
  )
}
