"use client"

import Link from "next/link"
import { HugeiconsIcon } from "@hugeicons/react"
import { ArrowLeft01Icon } from "@hugeicons/core-free-icons"

import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { MaterialsView } from "@/components/materials/materials-view"
import { useT } from "@/lib/i18n/provider"
import type { MaterialResponse } from "@/lib/materials"
import type { TopicResponse } from "@/lib/topics"

type Props = {
  topic: TopicResponse
  initialMaterials: MaterialResponse[]
  loadError: boolean
}

export function DataDetailView({ topic, initialMaterials, loadError }: Props) {
  const t = useT()

  return (
    <div className="flex flex-col gap-6">
      <MaterialsView
        spaceId={topic.spaceId}
        topicId={topic.id}
        initialMaterials={initialMaterials}
        loadError={loadError}
        header={
          <div className="flex flex-wrap items-start gap-3">
            <Button
              variant="ghost"
              size="icon-sm"
              nativeButton={false}
              render={
                <Link href="/data" aria-label={t.topics.backToTopics}>
                  <HugeiconsIcon icon={ArrowLeft01Icon} strokeWidth={2} />
                </Link>
              }
            />
            <div className="flex min-w-0 flex-col gap-1">
              <div className="flex flex-wrap items-center gap-2">
                <h1 className="truncate font-heading text-2xl font-semibold tracking-tight">
                  {topic.name}
                </h1>
                <Badge variant="outline">
                  {initialMaterials.length} {t.topicCard.materials}
                </Badge>
              </div>
            </div>
          </div>
        }
      />
    </div>
  )
}
