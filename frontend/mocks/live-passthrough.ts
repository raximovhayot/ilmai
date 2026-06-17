import { http, passthrough, type RequestHandler } from "msw"

import { type Feature, liveFeatures } from "@/lib/feature-flags"

const BASE = process.env.NEXT_PUBLIC_API_BASE_URL || "http://localhost:8080"

const FEATURE_PATHS: Partial<Record<Feature, string[]>> = {
  topics: [
    "/topics",
    "/topics/*",
    "/spaces",
    "/spaces/*",
    "/materials",
    "/materials/*",
  ],
  companion: ["/agent", "/agent/*"],
  quiz: ["/quiz", "/quiz/*"],
  gaps: ["/gaps", "/gaps/*"],
  plan: ["/plan", "/plan/*"],
  profile: ["/profile", "/profile/*", "/stats", "/goals"],
  onboarding: ["/onboarding", "/onboarding/*"],
  premium: ["/billing", "/billing/*"],
  telegram: ["/telegram", "/telegram/*"],
}

const ALWAYS_LIVE_PATHS: string[] = ["/auth", "/auth/*"]

export function livePassthroughHandlers(): RequestHandler[] {
  const seen = new Set<string>()
  const handlers: RequestHandler[] = []
  for (const path of ALWAYS_LIVE_PATHS) {
    if (seen.has(path)) continue
    seen.add(path)
    handlers.push(http.all(`${BASE}${path}`, () => passthrough()))
  }
  for (const feature of liveFeatures()) {
    const paths = FEATURE_PATHS[feature]
    if (!paths) continue
    for (const path of paths) {
      if (seen.has(path)) continue
      seen.add(path)
      handlers.push(http.all(`${BASE}${path}`, () => passthrough()))
    }
  }
  return handlers
}
