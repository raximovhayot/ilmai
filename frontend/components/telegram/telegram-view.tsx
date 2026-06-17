"use client"

import * as React from "react"
import { useSession } from "next-auth/react"
import { toast } from "sonner"
import { HugeiconsIcon } from "@hugeicons/react"
import {
  CheckmarkCircle02Icon,
  Clock01Icon,
  FireIcon,
  PuzzleIcon,
  TelegramIcon,
  Unlink04Icon,
} from "@hugeicons/core-free-icons"

import { Alert, AlertDescription } from "@/components/ui/alert"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog"
import { Skeleton } from "@/components/ui/skeleton"
import { Spinner } from "@/components/ui/spinner"
import {
  createLinkCode,
  getTelegram,
  isLinked,
  isPending,
  telegramDeepLink,
  unlinkTelegram,
  type TelegramLink,
} from "@/lib/telegram"
import { useLanguage, useT } from "@/lib/i18n/provider"
import { cn } from "@/lib/utils"

export function TelegramView() {
  const t = useT()
  const { lang } = useLanguage()
  const { status } = useSession()

  const [link, setLink] = React.useState<TelegramLink | null>(null)
  const [loading, setLoading] = React.useState(true)
  const [busy, setBusy] = React.useState(false)

  const linked = isLinked(link)
  const pending = isPending(link)
  const deepLink = telegramDeepLink(link)

  React.useEffect(() => {
    if (status !== "authenticated") return
    let cancelled = false
    void (async () => {
      try {
        const current = await getTelegram()
        if (!cancelled) setLink(current)
      } catch {
        // ignore
      } finally {
        if (!cancelled) setLoading(false)
      }
    })()
    return () => {
      cancelled = true
    }
  }, [status])

  React.useEffect(() => {
    if (status !== "authenticated" || !pending) return
    let cancelled = false
    const id = window.setInterval(() => {
      void (async () => {
        try {
          const current = await getTelegram()
          if (cancelled || !current) return
          setLink(current)
          if (current.linkedAt) {
            window.clearInterval(id)
            toast.success(t.telegram.linkedToast)
          }
        } catch {
          // ignore
        }
      })()
    }, 3000)
    return () => {
      cancelled = true
      window.clearInterval(id)
    }
  }, [status, pending, t])

  const onGenerate = async () => {
    if (status !== "authenticated") return
    setBusy(true)
    try {
      const next = await createLinkCode()
      setLink(next)
    } catch {
      toast.error(t.errors.generic)
    } finally {
      setBusy(false)
    }
  }

  const onUnlink = async () => {
    if (status !== "authenticated") return
    setBusy(true)
    try {
      await unlinkTelegram()
      setLink(null)
      toast.success(t.telegram.unlinkedToast)
    } catch {
      toast.error(t.errors.generic)
    } finally {
      setBusy(false)
    }
  }

  const onCopyCode = async () => {
    if (!link?.linkCode) return
    try {
      await navigator.clipboard.writeText(link.linkCode)
      toast.success(t.telegram.codeCopied)
    } catch {
      // ignore
    }
  }

  const expiresLabel = link?.linkCodeExpiresAt
    ? t.telegram.codeExpires.replace(
        "{time}",
        new Date(link.linkCodeExpiresAt).toLocaleTimeString(lang, {
          hour: "2-digit",
          minute: "2-digit",
        })
      )
    : null

  const pendingHint = t.telegram.pendingHint
    .replace("{bot}", link?.botUsername ?? "")
    .replace("{code}", link?.linkCode ?? "")

  return (
    <div className="flex flex-col gap-6">
      <header className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex flex-col gap-1">
          <h1 className="font-heading text-2xl font-semibold tracking-tight md:text-3xl">
            {t.telegram.title}
          </h1>
          <p className="text-sm text-muted-foreground">{t.telegram.subtitle}</p>
        </div>
        {!loading &&
          (linked ? (
            <Badge>{t.telegram.statusLinked}</Badge>
          ) : pending ? (
            <Badge variant="secondary">{t.telegram.statusPending}</Badge>
          ) : (
            <Badge variant="outline">{t.telegram.statusNotLinked}</Badge>
          ))}
      </header>

      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-base">
            <HugeiconsIcon
              icon={TelegramIcon}
              strokeWidth={2}
              className="size-5 text-sky-500"
            />
            {t.telegram.capabilitiesTitle}
          </CardTitle>
        </CardHeader>
        <CardContent className="flex flex-col gap-3">
          <CapabilityRow
            icon={Clock01Icon}
            accent="bg-amber-500/15 text-amber-600 dark:text-amber-400"
            title={t.telegram.capabilityReminderTitle}
            description={t.telegram.capabilityReminderDescription}
          />
          <CapabilityRow
            icon={PuzzleIcon}
            accent="bg-primary/10 text-primary"
            title={t.telegram.capabilityQuizTitle}
            description={t.telegram.capabilityQuizDescription}
          />
          <CapabilityRow
            icon={FireIcon}
            accent="bg-rose-500/15 text-rose-600 dark:text-rose-400"
            title={t.telegram.capabilityStreakTitle}
            description={t.telegram.capabilityStreakDescription}
          />
        </CardContent>
      </Card>

      {loading ? (
        <Skeleton className="h-48 w-full rounded-2xl" />
      ) : linked && link ? (
        <LinkedCard link={link} busy={busy} lang={lang} onUnlink={onUnlink} />
      ) : pending ? (
        <PendingCard
          code={link?.linkCode ?? ""}
          deepLink={deepLink}
          expiresLabel={expiresLabel}
          pendingHint={pendingHint}
          busy={busy}
          onCopyCode={onCopyCode}
          onRegenerate={onGenerate}
        />
      ) : (
        <ConnectCard busy={busy} onGenerate={onGenerate} />
      )}

      <Alert>
        <AlertDescription>{t.telegram.reminderNote}</AlertDescription>
      </Alert>
    </div>
  )
}

function ConnectCard({
  busy,
  onGenerate,
}: {
  busy: boolean
  onGenerate: () => void
}) {
  const t = useT()
  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-base">
          <HugeiconsIcon
            icon={TelegramIcon}
            strokeWidth={2}
            className="size-5 text-sky-500"
          />
          {t.telegram.connectTitle}
        </CardTitle>
        <CardDescription>{t.telegram.connectSubtitle}</CardDescription>
      </CardHeader>
      <CardContent>
        <Button onClick={onGenerate} disabled={busy}>
          {busy ? (
            <Spinner data-icon="inline-start" />
          ) : (
            <HugeiconsIcon
              icon={TelegramIcon}
              strokeWidth={2}
              data-icon="inline-start"
            />
          )}
          {t.telegram.generateCode}
        </Button>
      </CardContent>
    </Card>
  )
}

function PendingCard({
  code,
  deepLink,
  expiresLabel,
  pendingHint,
  busy,
  onCopyCode,
  onRegenerate,
}: {
  code: string
  deepLink: string | null
  expiresLabel: string | null
  pendingHint: string
  busy: boolean
  onCopyCode: () => void
  onRegenerate: () => void
}) {
  const t = useT()
  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">{t.telegram.pendingTitle}</CardTitle>
        <CardDescription>{t.telegram.pendingSubtitle}</CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        <div className="flex flex-col gap-2">
          <span className="text-sm font-medium">{t.telegram.codeLabel}</span>
          <button
            type="button"
            onClick={onCopyCode}
            aria-label={t.telegram.codeLabel}
            className="flex w-fit items-center gap-2 rounded-xl border border-dashed border-border bg-muted/40 px-4 py-3 font-mono text-2xl font-semibold tracking-[0.3em] transition-colors hover:bg-muted"
          >
            {code}
          </button>
          {expiresLabel && (
            <span className="text-xs text-muted-foreground">
              {expiresLabel}
            </span>
          )}
        </div>

        {deepLink && (
          <Button
            nativeButton={false}
            render={
              <a href={deepLink} target="_blank" rel="noopener noreferrer" />
            }
          >
            <HugeiconsIcon
              icon={TelegramIcon}
              strokeWidth={2}
              data-icon="inline-start"
            />
            {t.telegram.openInTelegram}
          </Button>
        )}

        <Alert>
          <AlertDescription>{pendingHint}</AlertDescription>
        </Alert>

        <div className="flex items-center gap-2 text-sm text-muted-foreground">
          <Spinner />
          <span>{t.telegram.waitingForLink}</span>
        </div>
      </CardContent>
      <CardFooter className="justify-end">
        <Button
          variant="ghost"
          size="sm"
          onClick={onRegenerate}
          disabled={busy}
        >
          {t.telegram.regenerateCode}
        </Button>
      </CardFooter>
    </Card>
  )
}

function LinkedCard({
  link,
  busy,
  lang,
  onUnlink,
}: {
  link: TelegramLink
  busy: boolean
  lang: string
  onUnlink: () => void
}) {
  const t = useT()
  const linkedOn = link.linkedAt
    ? new Date(link.linkedAt).toLocaleDateString(lang)
    : null
  const account = link.telegramUsername
    ? `@${link.telegramUsername}`
    : link.chatId != null
      ? String(link.chatId)
      : "—"
  return (
    <Card className="border-emerald-500/30 bg-emerald-500/5">
      <CardHeader className="flex flex-row items-start justify-between gap-2">
        <div className="flex flex-col gap-1">
          <CardTitle className="flex items-center gap-2 text-base">
            <HugeiconsIcon
              icon={CheckmarkCircle02Icon}
              strokeWidth={2}
              className="size-5 text-emerald-600 dark:text-emerald-400"
            />
            {t.telegram.linkedTitle}
          </CardTitle>
          <CardDescription>
            {t.telegram.linkedAs}:{" "}
            <span className="font-medium text-foreground">{account}</span>
          </CardDescription>
        </div>
        <Badge>{t.telegram.statusLinked}</Badge>
      </CardHeader>
      {linkedOn && (
        <CardContent>
          <span className="text-sm text-muted-foreground">
            {t.telegram.linkedOn}: {linkedOn}
          </span>
        </CardContent>
      )}
      <CardFooter className="justify-end">
        <Dialog>
          <DialogTrigger
            render={<Button variant="outline" size="sm" disabled={busy} />}
          >
            <HugeiconsIcon
              icon={Unlink04Icon}
              strokeWidth={2}
              data-icon="inline-start"
            />
            {t.telegram.unlink}
          </DialogTrigger>
          <DialogContent>
            <DialogHeader>
              <DialogTitle>{t.telegram.unlinkConfirmTitle}</DialogTitle>
              <DialogDescription>
                {t.telegram.unlinkConfirmDescription}
              </DialogDescription>
            </DialogHeader>
            <DialogFooter>
              <DialogClose render={<Button variant="outline" />}>
                {t.telegram.unlinkConfirmDismiss}
              </DialogClose>
              <DialogClose
                render={<Button variant="destructive" />}
                onClick={onUnlink}
              >
                {t.telegram.unlinkConfirmAction}
              </DialogClose>
            </DialogFooter>
          </DialogContent>
        </Dialog>
      </CardFooter>
    </Card>
  )
}

function CapabilityRow({
  icon,
  accent,
  title,
  description,
}: {
  icon: typeof TelegramIcon
  accent: string
  title: string
  description: string
}) {
  return (
    <div className="flex items-start gap-3 rounded-lg border border-border bg-card p-3">
      <span
        className={cn(
          "flex size-9 shrink-0 items-center justify-center rounded-md",
          accent
        )}
        aria-hidden
      >
        <HugeiconsIcon icon={icon} strokeWidth={2} className="size-4" />
      </span>
      <div className="flex min-w-0 flex-1 flex-col gap-0.5">
        <span className="text-sm font-medium">{title}</span>
        <span className="text-xs text-muted-foreground">{description}</span>
      </div>
    </div>
  )
}
