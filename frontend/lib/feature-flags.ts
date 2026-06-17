export const FEATURES = [
  "topics",
  "companion",
  "quiz",
  "gaps",
  "home",
  "profile",
  "plan",
  "onboarding",
  "premium",
  "telegram",
  "settings",
] as const

export type Feature = (typeof FEATURES)[number]

const MOCK_ENABLED =
  process.env.NEXT_PUBLIC_MOCK_API === "1" ||
  process.env.NEXT_PUBLIC_DEMO_MODE === "1"

function parseFeatureList(raw: string | undefined): Set<string> {
  return new Set(
    (raw ?? "")
      .split(",")
      .map((value) => value.trim().toLowerCase())
      .filter(Boolean)
  )
}

const LIVE_FEATURES = parseFeatureList(process.env.NEXT_PUBLIC_LIVE_FEATURES)

export function isMockEnabled(): boolean {
  return MOCK_ENABLED
}

export function isFeatureLive(feature: Feature): boolean {
  if (!MOCK_ENABLED) return true
  return LIVE_FEATURES.has("all") || LIVE_FEATURES.has(feature)
}

export function liveFeatures(): readonly Feature[] {
  if (!MOCK_ENABLED || LIVE_FEATURES.has("all")) return FEATURES
  return FEATURES.filter((feature) => LIVE_FEATURES.has(feature))
}
