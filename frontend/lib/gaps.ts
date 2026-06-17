import { apiFetch } from "@/lib/api"

export type GapTrend = "IMPROVING" | "WORSENING" | "STABLE" | "NEW"

export type GapItem = {
  id: string
  concept: string
  missCount: number
  hitCount: number
  accuracy: number
  lastSeenAt?: string | null
  suggestedMaterialId?: string | null
  suggestedMaterialName?: string | null
  trend?: GapTrend
}

export type GapsReport = {
  generatedAt?: string | null
  totalQuestionsAnswered: number
  correctCount: number
  overallAccuracy: number
  summary?: string | null
  gaps: GapItem[]
  strengths: GapItem[]
  recommendedNext?: string | null
}

export async function getGaps(): Promise<GapsReport | null> {
  return await apiFetch<GapsReport>("/gaps", {
    cache: "no-store",
  })
}

export async function refreshGaps(): Promise<GapsReport | null> {
  return await apiFetch<GapsReport>("/gaps/refresh", {
    method: "POST",
  })
}
