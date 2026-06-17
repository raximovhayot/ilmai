"use client"

import { HugeiconsIcon } from "@hugeicons/react"
import { GlobalIcon } from "@hugeicons/core-free-icons"

import { Button } from "@/components/ui/button"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import {
  LANGUAGES,
  LANGUAGE_LABELS,
  LANGUAGE_SHORT_LABELS,
  type Lang,
} from "@/lib/i18n/dictionary"
import { useLanguage } from "@/lib/i18n/provider"

function LanguageSwitcher() {
  const { lang, setLang, t } = useLanguage()
  return (
    <DropdownMenu>
      <DropdownMenuTrigger
        render={
          <Button variant="ghost" size="sm" aria-label={t.common.language}>
            <HugeiconsIcon
              icon={GlobalIcon}
              strokeWidth={2}
              data-icon="inline-start"
            />
            {LANGUAGE_SHORT_LABELS[lang]}
          </Button>
        }
      />
      <DropdownMenuContent align="end" className="min-w-44">
        {LANGUAGES.map((code: Lang) => (
          <DropdownMenuItem
            key={code}
            onClick={() => setLang(code)}
            data-state={lang === code ? "checked" : undefined}
            className="justify-between"
          >
            <span>{LANGUAGE_LABELS[code]}</span>
            <span className="text-xs text-muted-foreground">
              {LANGUAGE_SHORT_LABELS[code]}
            </span>
          </DropdownMenuItem>
        ))}
      </DropdownMenuContent>
    </DropdownMenu>
  )
}

export { LanguageSwitcher }
