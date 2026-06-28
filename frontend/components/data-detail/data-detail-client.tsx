"use client"

import * as React from "react"
import { useSession } from "next-auth/react"

import { DataDetailView } from "@/components/data-detail/data-detail-view"
import { Card, CardContent } from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"
import { listMaterials, type MaterialResponse } from "@/lib/materials"
import { listTopics, type TopicResponse } from "@/lib/topics"
import { useActiveRoom } from "@/lib/active-room"
import { useT } from "@/lib/i18n/provider"

type Props = {
  topicId: string
}

export function DataDetailClient({ topicId }: Props) {
  const { status } = useSession()
  const { activeRoomId } = useActiveRoom()
  const t = useT()

  const [topic, setTopic] = React.useState<TopicResponse | null>(null)
  const [materials, setMaterials] = React.useState<MaterialResponse[]>([])
  const [loading, setLoading] = React.useState(true)
  const [missing, setMissing] = React.useState(false)
  const [loadError, setLoadError] = React.useState(false)

  React.useEffect(() => {
    if (status !== "authenticated") return
    let cancelled = false
    void (async () => {
      try {
        const topics = await listTopics()
        if (cancelled) return
        const found = topics.find((tp) => tp.id === topicId) ?? null
        setTopic(found)
        setMissing(!found)
        if (found) {
          try {
            const ms = await listMaterials(topicId, activeRoomId)
            if (!cancelled) setMaterials(ms)
          } catch {
            if (!cancelled) setLoadError(true)
          }
        }
      } catch {
        if (!cancelled) setLoadError(true)
      } finally {
        if (!cancelled) setLoading(false)
      }
    })()
    return () => {
      cancelled = true
    }
  }, [status, topicId, activeRoomId])

  if (loading) {
    return <DataDetailSkeleton />
  }

  if (missing || !topic) {
    return (
      <div className="rounded-lg border border-dashed border-border p-8 text-center text-sm text-muted-foreground">
        {t.topics.errors.notFound}
      </div>
    )
  }

  return (
    <DataDetailView
      topic={topic}
      initialMaterials={materials}
      loadError={loadError}
    />
  )
}

function DataDetailSkeleton() {
  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-wrap items-center gap-3">
        <Skeleton className="size-8 shrink-0 rounded-md" />
        <Skeleton className="h-7 w-48" />
        <Skeleton className="h-5 w-20 rounded-full" />
      </div>
      <ul className="grid grid-cols-1 gap-3 sm:grid-cols-2">
        {[0, 1, 2, 3].map((i) => (
          <li key={i}>
            <Card>
              <CardContent className="flex items-start gap-3 p-4">
                <Skeleton className="size-9 shrink-0 rounded-md" />
                <div className="flex flex-1 flex-col gap-2">
                  <Skeleton className="h-4 w-2/5" />
                  <Skeleton className="h-3 w-1/3" />
                </div>
              </CardContent>
            </Card>
          </li>
        ))}
      </ul>
    </div>
  )
}
