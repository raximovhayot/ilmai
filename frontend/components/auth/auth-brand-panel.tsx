"use client"

import { HugeiconsIcon } from "@hugeicons/react"
import {
  BookOpen02Icon,
  Chatting01Icon,
  Target02Icon,
  SparklesIcon,
} from "@hugeicons/core-free-icons"

import { useT } from "@/lib/i18n/provider"

const BULLET_ICONS = [BookOpen02Icon, Chatting01Icon, Target02Icon]

function AuthBrandPanel() {
  const t = useT()

  return (
    <div className="relative hidden h-full overflow-hidden bg-primary text-primary-foreground lg:flex lg:flex-col">
      <div className="absolute -top-32 -left-24 size-96 rounded-full bg-chart-3/40 blur-3xl" />
      <div className="absolute right-[-6rem] bottom-[-6rem] size-[28rem] rounded-full bg-chart-1/30 blur-3xl" />

      <div className="relative z-10 flex h-full flex-col gap-12 p-10 xl:p-14">
        <div className="flex items-center gap-2 text-base font-medium">
          <span className="inline-flex size-9 items-center justify-center rounded-2xl bg-primary-foreground/15 ring-1 ring-primary-foreground/10 backdrop-blur">
            <HugeiconsIcon
              icon={SparklesIcon}
              strokeWidth={2}
              className="size-5"
            />
          </span>
          <span className="text-lg font-semibold tracking-tight">
            {t.brand.name}
          </span>
        </div>

        <div className="mt-auto flex flex-col gap-8">
          <h2 className="font-heading text-3xl leading-tight font-semibold tracking-tight xl:text-4xl">
            {t.brand.tagline}
          </h2>

          <ul className="flex flex-col gap-4">
            {t.brand.bullets.map((bullet, i) => {
              const Icon = BULLET_ICONS[i % BULLET_ICONS.length]
              return (
                <li key={i} className="flex items-start gap-3">
                  <span className="mt-0.5 inline-flex size-8 shrink-0 items-center justify-center rounded-xl bg-primary-foreground/10 ring-1 ring-primary-foreground/10">
                    <HugeiconsIcon
                      icon={Icon}
                      strokeWidth={2}
                      className="size-4"
                    />
                  </span>
                  <span className="text-sm leading-relaxed text-primary-foreground/90 xl:text-base">
                    {bullet}
                  </span>
                </li>
              )
            })}
          </ul>

          <p className="text-xs text-primary-foreground/60">{t.brand.footer}</p>
        </div>
      </div>
    </div>
  )
}

export { AuthBrandPanel }
