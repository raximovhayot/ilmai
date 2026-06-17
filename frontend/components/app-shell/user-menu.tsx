"use client"

import * as React from "react"
import Link from "next/link"
import { signOut, useSession } from "next-auth/react"
import { useTheme } from "next-themes"
import { toast } from "sonner"
import { HugeiconsIcon } from "@hugeicons/react"
import {
  ComputerIcon,
  Crown02Icon,
  Logout03Icon,
  Moon02Icon,
  RefreshIcon,
  Settings01Icon,
  Sun03Icon,
} from "@hugeicons/core-free-icons"

import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuRadioGroup,
  DropdownMenuRadioItem,
  DropdownMenuSeparator,
  DropdownMenuSub,
  DropdownMenuSubContent,
  DropdownMenuSubTrigger,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import { apiFetch } from "@/lib/api"
import { LANGUAGES, LANGUAGE_LABELS, type Lang } from "@/lib/i18n/dictionary"
import { useLanguage, useT } from "@/lib/i18n/provider"
import { cn } from "@/lib/utils"

const DEMO_MODE =
  process.env.NEXT_PUBLIC_DEMO_MODE === "1" ||
  process.env.NEXT_PUBLIC_MOCK_API === "1"

export type UserSummary = {
  name?: string | null
  email?: string | null
  image?: string | null
}

export function getInitials(
  name: string | null | undefined,
  email: string | null | undefined
): string {
  const source = (name && name.trim()) || (email && email.trim()) || ""
  if (!source) return "??"
  const parts = source.split(/\s+/).filter(Boolean)
  if (parts.length >= 2) {
    return (parts[0]![0]! + parts[1]![0]!).toUpperCase()
  }
  return (parts[0] ?? "").slice(0, 2).toUpperCase() || "??"
}

export function UserAvatar({
  name,
  email,
  image,
  size = "md",
}: {
  name?: string | null
  email?: string | null
  image?: string | null
  size?: "sm" | "md"
}) {
  const [failed, setFailed] = React.useState(false)
  const showImage = !!image && !failed
  const initials = getInitials(name, email)
  const sizeClass = size === "sm" ? "size-7 text-[10px]" : "size-8 text-xs"

  return (
    <span
      className={cn(
        "flex shrink-0 items-center justify-center overflow-hidden rounded-full bg-primary/10 font-semibold text-primary ring-1 ring-foreground/10",
        sizeClass
      )}
    >
      {showImage ? (
        // eslint-disable-next-line @next/next/no-img-element
        <img
          src={image!}
          alt={name ?? email ?? ""}
          referrerPolicy="no-referrer"
          onError={() => setFailed(true)}
          className="h-full w-full object-cover"
        />
      ) : (
        initials
      )}
    </span>
  )
}

export function UserMenu({
  user,
  trigger,
  side = "bottom",
  align = "end",
  onNavigate,
}: {
  user: UserSummary
  trigger: React.ReactElement
  side?: "top" | "bottom" | "left" | "right"
  align?: "start" | "center" | "end"
  onNavigate?: () => void
}) {
  const t = useT()
  const { status } = useSession()
  const { lang, setLang } = useLanguage()
  const { theme, setTheme } = useTheme()
  const mounted = React.useSyncExternalStore(
    () => () => {},
    () => true,
    () => false
  )
  const [resetting, setResetting] = React.useState(false)

  const onReset = React.useCallback(async () => {
    if (status !== "authenticated") return
    setResetting(true)
    try {
      await apiFetch<{ ok: boolean }>("/demo/reset", {
        method: "POST",
      })
      toast.success(t.nav.resetDemo)
      window.location.reload()
    } catch {
      toast.error(t.errors.generic)
    } finally {
      setResetting(false)
    }
  }, [status, t])

  const activeTheme = mounted ? (theme ?? "system") : "system"
  const currentThemeIcon =
    activeTheme === "light"
      ? Sun03Icon
      : activeTheme === "dark"
        ? Moon02Icon
        : ComputerIcon

  return (
    <DropdownMenu>
      <DropdownMenuTrigger render={trigger} />
      <DropdownMenuContent side={side} align={align} className="min-w-64">
        <div className="px-2 py-2 text-xs">
          <p className="truncate font-medium text-foreground">
            {user.name ?? t.settings.account.unknownName}
          </p>
          {user.email && (
            <p className="truncate text-muted-foreground">{user.email}</p>
          )}
        </div>
        <DropdownMenuSeparator />
        <DropdownMenuItem
          render={
            <Link href="/premium" onClick={onNavigate} className="gap-2">
              <HugeiconsIcon
                icon={Crown02Icon}
                strokeWidth={2}
                className="size-4 text-amber-500"
              />
              {t.nav.upgrade}
            </Link>
          }
        />
        <DropdownMenuItem
          render={
            <Link href="/settings" onClick={onNavigate} className="gap-2">
              <HugeiconsIcon
                icon={Settings01Icon}
                strokeWidth={2}
                className="size-4"
              />
              {t.nav.settings}
            </Link>
          }
        />
        {DEMO_MODE && (
          <DropdownMenuItem onClick={onReset} disabled={resetting}>
            <HugeiconsIcon
              icon={RefreshIcon}
              strokeWidth={2}
              className="size-4"
            />
            {t.nav.resetDemo}
          </DropdownMenuItem>
        )}
        <DropdownMenuSeparator />
        <DropdownMenuSub>
          <DropdownMenuSubTrigger>
            <HugeiconsIcon
              icon={currentThemeIcon}
              strokeWidth={2}
              className="size-4"
            />
            {t.common.theme}
          </DropdownMenuSubTrigger>
          <DropdownMenuSubContent className="min-w-44">
            <DropdownMenuRadioGroup
              value={activeTheme}
              onValueChange={(value) =>
                setTheme(value as "light" | "dark" | "system")
              }
            >
              <DropdownMenuRadioItem value="light">
                <HugeiconsIcon
                  icon={Sun03Icon}
                  strokeWidth={2}
                  className="size-4"
                />
                {t.common.themeLight}
              </DropdownMenuRadioItem>
              <DropdownMenuRadioItem value="dark">
                <HugeiconsIcon
                  icon={Moon02Icon}
                  strokeWidth={2}
                  className="size-4"
                />
                {t.common.themeDark}
              </DropdownMenuRadioItem>
              <DropdownMenuRadioItem value="system">
                <HugeiconsIcon
                  icon={ComputerIcon}
                  strokeWidth={2}
                  className="size-4"
                />
                {t.common.themeSystem}
              </DropdownMenuRadioItem>
            </DropdownMenuRadioGroup>
          </DropdownMenuSubContent>
        </DropdownMenuSub>
        <DropdownMenuSub>
          <DropdownMenuSubTrigger>
            <LanguageFlag code={lang} />
            {t.common.language}
          </DropdownMenuSubTrigger>
          <DropdownMenuSubContent className="min-w-44">
            <DropdownMenuRadioGroup
              value={lang}
              onValueChange={(value) => setLang(value as Lang)}
            >
              {LANGUAGES.map((code) => (
                <DropdownMenuRadioItem key={code} value={code}>
                  <LanguageFlag code={code} />
                  <span>{LANGUAGE_LABELS[code]}</span>
                </DropdownMenuRadioItem>
              ))}
            </DropdownMenuRadioGroup>
          </DropdownMenuSubContent>
        </DropdownMenuSub>
        <DropdownMenuSeparator />
        <DropdownMenuItem
          onClick={() => signOut({ callbackUrl: "/login" })}
          className="text-destructive focus:text-destructive"
        >
          <HugeiconsIcon
            icon={Logout03Icon}
            strokeWidth={2}
            className="size-4"
          />
          {t.nav.signOut}
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}

function LanguageFlag({ code }: { code: Lang }) {
  return (
    <span
      aria-hidden
      className="inline-flex size-4 shrink-0 items-center justify-center overflow-hidden rounded-[2px] bg-foreground/5 ring-1 ring-foreground/10"
    >
      {code === "en" ? (
        <UkFlag />
      ) : code === "ru" ? (
        <RussiaFlag />
      ) : (
        <UzbekistanFlag />
      )}
    </span>
  )
}

function UkFlag() {
  return (
    <svg
      viewBox="0 0 60 30"
      preserveAspectRatio="xMidYMid slice"
      className="block h-full w-full"
    >
      <clipPath id="uk-clip">
        <rect width="60" height="30" />
      </clipPath>
      <g clipPath="url(#uk-clip)">
        <rect width="60" height="30" fill="#012169" />
        <path d="M0,0 L60,30 M60,0 L0,30" stroke="#FFFFFF" strokeWidth="6" />
        <path d="M0,0 L60,30 M60,0 L0,30" stroke="#C8102E" strokeWidth="2.5" />
        <path d="M30,0 V30 M0,15 H60" stroke="#FFFFFF" strokeWidth="10" />
        <path d="M30,0 V30 M0,15 H60" stroke="#C8102E" strokeWidth="6" />
      </g>
    </svg>
  )
}

function RussiaFlag() {
  return (
    <svg
      viewBox="0 0 9 6"
      preserveAspectRatio="xMidYMid slice"
      className="block h-full w-full"
    >
      <rect width="9" height="2" fill="#FFFFFF" />
      <rect width="9" height="2" y="2" fill="#0039A6" />
      <rect width="9" height="2" y="4" fill="#D52B1E" />
    </svg>
  )
}

function UzbekistanFlag() {
  return (
    <svg
      viewBox="0 0 60 30"
      preserveAspectRatio="xMidYMid slice"
      className="block h-full w-full"
    >
      <rect width="60" height="9.5" fill="#0099B5" />
      <rect width="60" height="0.7" y="9.5" fill="#CE1126" />
      <rect width="60" height="9.6" y="10.2" fill="#FFFFFF" />
      <rect width="60" height="0.7" y="19.8" fill="#CE1126" />
      <rect width="60" height="9.5" y="20.5" fill="#1EB53A" />
      <circle cx="10" cy="5" r="3" fill="#FFFFFF" />
      <circle cx="11" cy="5" r="2.5" fill="#0099B5" />
    </svg>
  )
}
