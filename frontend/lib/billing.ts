import { apiFetch } from "@/lib/api"

export type SubscriptionPlanCode = "FREE" | "PREMIUM_MONTHLY" | "PREMIUM_YEARLY"
export type SubscriptionStatusCode =
  | "PENDING"
  | "ACTIVE"
  | "CANCELED"
  | "EXPIRED"
export type PaymentProviderCode = "STRIPE" | "PAYME" | "CLICK" | "TEST"
export type PaymentStatusCode = "PENDING" | "SUCCEEDED" | "FAILED" | "REFUNDED"

export type PremiumPlanCode = Exclude<SubscriptionPlanCode, "FREE">

export type Subscription = {
  id: string
  plan: SubscriptionPlanCode
  status: SubscriptionStatusCode
  provider: PaymentProviderCode
  currentPeriodStart: string | null
  currentPeriodEnd: string | null
  cancelAtPeriodEnd: boolean
}

export type Payment = {
  id: string
  provider: PaymentProviderCode
  externalId: string | null
  amountMinor: number
  currency: string
  status: PaymentStatusCode
  occurredAt: string
}

export type CheckoutSession = {
  provider: PaymentProviderCode
  externalId: string | null
  redirectUrl: string | null
}

export type Tier = "FREE" | "PREMIUM"

export const PREMIUM_PLANS: PremiumPlanCode[] = [
  "PREMIUM_MONTHLY",
  "PREMIUM_YEARLY",
]

export const PAYMENT_PROVIDERS: PaymentProviderCode[] = [
  "PAYME",
  "CLICK",
  "STRIPE",
  "TEST",
]

export function isActiveSubscription(sub: Subscription | null): boolean {
  return !!sub && sub.status === "ACTIVE" && sub.plan !== "FREE"
}

export function deriveTier(sub: Subscription | null): Tier {
  return isActiveSubscription(sub) ? "PREMIUM" : "FREE"
}

export async function startCheckout(input: {
  plan: PremiumPlanCode
  provider: PaymentProviderCode
}): Promise<CheckoutSession> {
  return await apiFetch<CheckoutSession>("/billing/checkout", {
    method: "POST",
    body: input,
  })
}

export async function getActiveSubscription(): Promise<Subscription | null> {
  return await apiFetch<Subscription | null>("/billing/subscription", {
    cache: "no-store",
  })
}

export async function getSubscriptions(): Promise<Subscription[]> {
  return (
    (await apiFetch<Subscription[]>("/billing/subscriptions", {
      cache: "no-store",
    })) ?? []
  )
}

export async function getPayments(): Promise<Payment[]> {
  return (
    (await apiFetch<Payment[]>("/billing/payments", {
      cache: "no-store",
    })) ?? []
  )
}

export async function cancelSubscription(): Promise<Subscription | null> {
  return await apiFetch<Subscription | null>("/billing/subscription", {
    method: "DELETE",
  })
}
