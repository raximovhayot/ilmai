"use client"

import * as React from "react"
import { toast } from "sonner"
import { useSession } from "next-auth/react"
import { HugeiconsIcon } from "@hugeicons/react"
import { CheckmarkCircle02Icon, Crown02Icon } from "@hugeicons/core-free-icons"

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
import {
  Item,
  ItemActions,
  ItemContent,
  ItemDescription,
  ItemGroup,
  ItemTitle,
} from "@/components/ui/item"
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group"
import { Skeleton } from "@/components/ui/skeleton"
import { Spinner } from "@/components/ui/spinner"
import {
  cancelSubscription,
  getActiveSubscription,
  getPayments,
  getSubscriptions,
  isActiveSubscription,
  startCheckout,
  type Payment,
  type PaymentProviderCode,
  type PaymentStatusCode,
  type PremiumPlanCode,
  type Subscription,
} from "@/lib/billing"
import { useLanguage, useT } from "@/lib/i18n/provider"
import { cn } from "@/lib/utils"

const PROVIDERS: PaymentProviderCode[] = ["PAYME", "CLICK", "STRIPE", "TEST"]
const PLANS: PremiumPlanCode[] = ["PREMIUM_MONTHLY", "PREMIUM_YEARLY"]
const PROVIDER_LABEL: Record<PaymentProviderCode, string> = {
  PAYME: "Payme",
  CLICK: "Click",
  STRIPE: "Stripe",
  TEST: "Test",
}

export function PremiumView() {
  const t = useT()
  const { lang } = useLanguage()
  const { status } = useSession()

  const [active, setActive] = React.useState<Subscription | null>(null)
  const [subscriptions, setSubscriptions] = React.useState<Subscription[]>([])
  const [payments, setPayments] = React.useState<Payment[]>([])
  const [loading, setLoading] = React.useState(true)
  const [busy, setBusy] = React.useState(false)
  const [provider, setProvider] = React.useState<PaymentProviderCode>("PAYME")

  const load = React.useCallback(async () => {
    if (status !== "authenticated") return
    const [a, subs, pays] = await Promise.all([
      getActiveSubscription(),
      getSubscriptions(),
      getPayments(),
    ])
    setActive(a)
    setSubscriptions(subs)
    setPayments(pays)
  }, [status])

  React.useEffect(() => {
    if (status !== "authenticated") return
    let cancelled = false
    void (async () => {
      try {
        await load()
      } catch {
        // ignore
      } finally {
        if (!cancelled) setLoading(false)
      }
    })()
    return () => {
      cancelled = true
    }
  }, [status, load])

  React.useEffect(() => {
    if (typeof window === "undefined") return
    const params = new URLSearchParams(window.location.search)
    const status = params.get("checkout")
    if (status !== "success" && status !== "cancel") return
    if (status === "success") {
      toast.success(t.premium.checkoutSuccess)
    } else {
      toast.info(t.premium.checkoutCanceled)
    }
    params.delete("checkout")
    const query = params.toString()
    window.history.replaceState(
      null,
      "",
      window.location.pathname + (query ? `?${query}` : "")
    )
  }, [t])

  const isPremium = isActiveSubscription(active)

  const formatDate = (iso: string | null) =>
    iso ? new Date(iso).toLocaleDateString(lang) : "—"

  const formatMoney = (minor: number, currency: string) => {
    try {
      return new Intl.NumberFormat(lang, {
        style: "currency",
        currency,
        maximumFractionDigits: currency === "UZS" ? 0 : 2,
      }).format(minor / 100)
    } catch {
      return `${(minor / 100).toLocaleString(lang)} ${currency}`
    }
  }

  const planLabel = (plan: Subscription["plan"]) =>
    plan === "PREMIUM_YEARLY"
      ? t.premium.planYearly
      : plan === "PREMIUM_MONTHLY"
        ? t.premium.planMonthly
        : t.premium.tierFree

  const subStatusLabel = (status: Subscription["status"]) =>
    status === "ACTIVE"
      ? t.premium.statusActive
      : status === "PENDING"
        ? t.premium.statusPending
        : status === "CANCELED"
          ? t.premium.statusCanceled
          : t.premium.statusExpired

  const payStatusLabel = (status: PaymentStatusCode) =>
    status === "SUCCEEDED"
      ? t.premium.paySucceeded
      : status === "PENDING"
        ? t.premium.payPending
        : status === "FAILED"
          ? t.premium.payFailed
          : t.premium.payRefunded

  const payStatusVariant = (status: PaymentStatusCode) =>
    status === "SUCCEEDED"
      ? "secondary"
      : status === "FAILED"
        ? "destructive"
        : "outline"

  const onCheckout = async (plan: PremiumPlanCode) => {
    if (status !== "authenticated") return
    setBusy(true)
    try {
      const result = await startCheckout({ plan, provider })
      if (result?.redirectUrl) {
        window.location.assign(result.redirectUrl)
        return
      }
      toast.success(t.premium.checkoutSuccess)
      await load()
    } catch {
      toast.error(t.errors.generic)
    } finally {
      setBusy(false)
    }
  }

  const onCancel = async () => {
    if (status !== "authenticated") return
    setBusy(true)
    try {
      await cancelSubscription()
      toast.success(t.premium.canceledToast)
      await load()
    } catch {
      toast.error(t.errors.generic)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <header className="flex flex-col gap-1">
        <h1 className="flex items-center gap-2 font-heading text-2xl font-semibold tracking-tight md:text-3xl">
          <HugeiconsIcon
            icon={Crown02Icon}
            strokeWidth={2}
            className="size-7 text-amber-500"
          />
          {t.premium.title}
        </h1>
        <p className="text-sm text-muted-foreground">{t.premium.subtitle}</p>
      </header>

      {loading ? (
        <Skeleton className="h-64 w-full rounded-2xl" />
      ) : isPremium && active ? (
        <ActiveSubscriptionCard
          subscription={active}
          busy={busy}
          features={t.premium.premiumFeatures}
          planLabel={planLabel}
          statusLabel={subStatusLabel}
          formatDate={formatDate}
          onCancel={onCancel}
        />
      ) : (
        <ChoosePlanCard
          provider={provider}
          onProviderChange={setProvider}
          busy={busy}
          planLabel={planLabel}
          onCheckout={onCheckout}
        />
      )}

      {!loading && payments.length > 0 && (
        <Card>
          <CardHeader>
            <CardTitle className="text-base">
              {t.premium.paymentsTitle}
            </CardTitle>
          </CardHeader>
          <CardContent>
            <ItemGroup>
              {payments.map((p) => (
                <Item key={p.id} variant="outline">
                  <ItemContent>
                    <ItemTitle className="tabular-nums">
                      {formatMoney(p.amountMinor, p.currency)}
                    </ItemTitle>
                    <ItemDescription>
                      {p.provider === "TEST"
                        ? t.premium.providerTest
                        : PROVIDER_LABEL[p.provider]}{" "}
                      · {formatDate(p.occurredAt)}
                    </ItemDescription>
                  </ItemContent>
                  <ItemActions>
                    <Badge variant={payStatusVariant(p.status)}>
                      {payStatusLabel(p.status)}
                    </Badge>
                  </ItemActions>
                </Item>
              ))}
            </ItemGroup>
          </CardContent>
        </Card>
      )}

      {!loading && subscriptions.length > 0 && (
        <Card>
          <CardHeader>
            <CardTitle className="text-base">
              {t.premium.subscriptionsTitle}
            </CardTitle>
          </CardHeader>
          <CardContent>
            <ItemGroup>
              {subscriptions.map((s) => (
                <Item key={s.id} variant="outline">
                  <ItemContent>
                    <ItemTitle>{planLabel(s.plan)}</ItemTitle>
                    <ItemDescription>
                      {s.provider === "TEST"
                        ? t.premium.providerTest
                        : PROVIDER_LABEL[s.provider]}{" "}
                      · {formatDate(s.currentPeriodStart)} –{" "}
                      {formatDate(s.currentPeriodEnd)}
                    </ItemDescription>
                  </ItemContent>
                  <ItemActions>
                    <Badge variant="outline">{subStatusLabel(s.status)}</Badge>
                  </ItemActions>
                </Item>
              ))}
            </ItemGroup>
          </CardContent>
        </Card>
      )}
    </div>
  )
}

type ActiveProps = {
  subscription: Subscription
  busy: boolean
  features: string[]
  planLabel: (plan: Subscription["plan"]) => string
  statusLabel: (status: Subscription["status"]) => string
  formatDate: (iso: string | null) => string
  onCancel: () => void
}

function ActiveSubscriptionCard({
  subscription,
  busy,
  features,
  planLabel,
  statusLabel,
  formatDate,
  onCancel,
}: ActiveProps) {
  const t = useT()
  return (
    <Card className="border-amber-400/50 bg-amber-400/5">
      <CardHeader className="flex flex-row items-start justify-between gap-2">
        <div className="flex flex-col gap-1">
          <CardTitle className="flex items-center gap-2 text-base">
            <HugeiconsIcon
              icon={Crown02Icon}
              strokeWidth={2}
              className="size-5 text-amber-500"
            />
            {t.premium.currentTitle}
          </CardTitle>
          <CardDescription>{planLabel(subscription.plan)}</CardDescription>
        </div>
        <Badge>{statusLabel(subscription.status)}</Badge>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        <Alert>
          <AlertDescription>
            {subscription.cancelAtPeriodEnd
              ? `${t.premium.willNotRenew} ${t.premium.endsOn} ${formatDate(subscription.currentPeriodEnd)}`
              : `${t.premium.renewsOn} ${formatDate(subscription.currentPeriodEnd)}`}
          </AlertDescription>
        </Alert>
        <ul className="grid grid-cols-1 gap-2 md:grid-cols-2">
          {features.map((label, i) => (
            <li
              key={i}
              className="flex items-start gap-2 rounded-lg border border-border bg-card p-3 text-sm"
            >
              <HugeiconsIcon
                icon={CheckmarkCircle02Icon}
                strokeWidth={2}
                className="mt-0.5 size-4 text-emerald-600 dark:text-emerald-400"
              />
              <span>{label}</span>
            </li>
          ))}
        </ul>
      </CardContent>
      {!subscription.cancelAtPeriodEnd && (
        <CardFooter className="justify-end">
          <Dialog>
            <DialogTrigger
              render={<Button variant="outline" size="sm" disabled={busy} />}
            >
              {t.premium.cancel}
            </DialogTrigger>
            <DialogContent>
              <DialogHeader>
                <DialogTitle>{t.premium.cancelConfirmTitle}</DialogTitle>
                <DialogDescription>
                  {t.premium.cancelConfirmDescription}
                </DialogDescription>
              </DialogHeader>
              <DialogFooter>
                <DialogClose render={<Button variant="outline" />}>
                  {t.premium.cancelConfirmDismiss}
                </DialogClose>
                <DialogClose
                  render={<Button variant="destructive" />}
                  onClick={onCancel}
                >
                  {t.premium.cancelConfirmAction}
                </DialogClose>
              </DialogFooter>
            </DialogContent>
          </Dialog>
        </CardFooter>
      )}
    </Card>
  )
}

type ChooseProps = {
  provider: PaymentProviderCode
  onProviderChange: (provider: PaymentProviderCode) => void
  busy: boolean
  planLabel: (plan: Subscription["plan"]) => string
  onCheckout: (plan: PremiumPlanCode) => void
}

function ChoosePlanCard({
  provider,
  onProviderChange,
  busy,
  planLabel,
  onCheckout,
}: ChooseProps) {
  const t = useT()
  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-base">
          <span className="text-xl motion-safe:animate-bounce" aria-hidden>
            ✨
          </span>
          {t.premium.choosePlanTitle}
        </CardTitle>
        <CardDescription>{t.premium.choosePlanSubtitle}</CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-5">
        <div className="flex flex-col gap-2">
          <span className="text-sm font-medium">{t.premium.comparePlans}</span>
          <div className="grid gap-3 sm:grid-cols-2">
            <div className="flex flex-col gap-3 rounded-2xl border border-border bg-muted/30 p-4">
              <div className="flex items-center justify-between gap-2">
                <span className="flex items-center gap-2 font-heading text-lg font-medium">
                  <span className="text-xl" aria-hidden>
                    📘
                  </span>
                  {t.premium.tierFree}
                </span>
                <Badge variant="outline">{t.premium.tierCurrent}</Badge>
              </div>
              <span className="text-sm text-muted-foreground">
                {t.premium.tierFreePrice}
              </span>
              <ul className="flex flex-col gap-2">
                {t.premium.freeFeatures.map((label, i) => (
                  <li key={i} className="flex items-start gap-2 text-sm">
                    <HugeiconsIcon
                      icon={CheckmarkCircle02Icon}
                      strokeWidth={2}
                      className="mt-0.5 size-4 text-muted-foreground"
                    />
                    <span>{label}</span>
                  </li>
                ))}
              </ul>
            </div>
            <div className="flex flex-col gap-3 rounded-2xl border border-amber-400/50 bg-amber-400/5 p-4">
              <span className="flex items-center gap-2 font-heading text-lg font-medium">
                <span className="text-xl motion-safe:animate-pulse" aria-hidden>
                  👑
                </span>
                {t.premium.tierPremium}
              </span>
              <ul className="flex flex-col gap-2">
                {t.premium.premiumFeatures.map((label, i) => (
                  <li key={i} className="flex items-start gap-2 text-sm">
                    <HugeiconsIcon
                      icon={CheckmarkCircle02Icon}
                      strokeWidth={2}
                      className="mt-0.5 size-4 text-emerald-600 dark:text-emerald-400"
                    />
                    <span>{label}</span>
                  </li>
                ))}
              </ul>
            </div>
          </div>
        </div>

        <div className="flex flex-col gap-2">
          <span className="text-sm font-medium">{t.premium.providerLabel}</span>
          <RadioGroup
            value={provider}
            onValueChange={(value) =>
              onProviderChange(value as PaymentProviderCode)
            }
            className="grid-cols-1 sm:grid-cols-3"
          >
            {PROVIDERS.map((p) => (
              <label
                key={p}
                className={cn(
                  "flex cursor-pointer items-center gap-2 rounded-xl border px-3 py-2.5 text-sm transition-colors",
                  provider === p
                    ? "border-primary bg-primary/5"
                    : "border-border hover:bg-accent/40"
                )}
              >
                <RadioGroupItem value={p} />
                <span className="font-medium">
                  {p === "TEST" ? t.premium.providerTest : PROVIDER_LABEL[p]}
                </span>
              </label>
            ))}
          </RadioGroup>
        </div>

        <div className="grid gap-3 sm:grid-cols-2">
          {PLANS.map((plan) => (
            <div
              key={plan}
              className="flex flex-col gap-3 rounded-2xl border border-border p-4"
            >
              <div className="flex flex-col gap-0.5">
                <span className="font-heading text-lg font-medium">
                  {planLabel(plan)}
                </span>
                <span className="text-sm text-muted-foreground">
                  {plan === "PREMIUM_YEARLY"
                    ? t.premium.billedYearly
                    : t.premium.billedMonthly}
                </span>
              </div>
              <Button
                className="mt-auto"
                disabled={busy}
                onClick={() => onCheckout(plan)}
              >
                {busy ? (
                  <Spinner data-icon="inline-start" />
                ) : (
                  <HugeiconsIcon
                    icon={Crown02Icon}
                    strokeWidth={2}
                    data-icon="inline-start"
                  />
                )}
                {busy ? t.premium.redirecting : t.premium.subscribe}
              </Button>
            </div>
          ))}
        </div>
      </CardContent>
    </Card>
  )
}
