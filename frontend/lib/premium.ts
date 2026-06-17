import {
  deriveTier,
  getActiveSubscription,
  type Subscription,
  type Tier,
} from "@/lib/billing"

export type { Tier }

export type PremiumStatus = {
  tier: Tier
  subscription: Subscription | null
}

export async function getPremium(): Promise<PremiumStatus> {
  const subscription = await getActiveSubscription()
  return { tier: deriveTier(subscription), subscription }
}
