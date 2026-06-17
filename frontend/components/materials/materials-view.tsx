"use client"

import * as React from "react"
import { useRouter } from "next/navigation"
import { useSession } from "next-auth/react"
import { toast } from "sonner"
import { HugeiconsIcon } from "@hugeicons/react"
import {
  Delete02Icon,
  DocumentAttachmentIcon,
  Folder01Icon,
  Upload04Icon,
} from "@hugeicons/core-free-icons"

import { Alert, AlertDescription } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import { Card, CardContent } from "@/components/ui/card"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import {
  Empty,
  EmptyDescription,
  EmptyHeader,
  EmptyMedia,
  EmptyTitle,
} from "@/components/ui/empty"
import { Spinner } from "@/components/ui/spinner"
import { MaterialStatusPill } from "@/components/materials/status-pill"
import { ApiClientError } from "@/lib/api"
import { cn } from "@/lib/utils"
import { useT } from "@/lib/i18n/provider"
import {
  deleteMaterial,
  getMaterial,
  type MaterialResponse,
  type MaterialStatus,
  moveMaterial,
  uploadMaterialFile,
} from "@/lib/materials"
import { listTopics, type TopicResponse } from "@/lib/topics"

type MaterialsViewProps = {
  spaceId: string
  topicId?: string | null
  initialMaterials: MaterialResponse[]
  loadError: boolean
  header?: React.ReactNode
}

function materialErrorMessageKey(
  errorCode: string | undefined
): keyof ReturnType<typeof useT>["materials"]["errors"] {
  switch (errorCode) {
    case "MATERIAL_NOT_FOUND":
      return "notFound"
    case "MATERIAL_TOPIC_NOT_FOUND":
      return "topicNotFound"
    case "MATERIAL_CONTENT_REQUIRED":
      return "contentRequired"
    case "MATERIAL_TITLE_BLANK":
      return "titleBlank"
    case "MATERIAL_UNSUPPORTED_TYPE":
      return "unsupportedType"
    case "MATERIAL_TOO_LARGE":
      return "tooLarge"
    case "MATERIAL_STORAGE_FAILED":
      return "storageFailed"
    default:
      return "generic"
  }
}

function isProcessing(status: MaterialStatus): boolean {
  return status === "PENDING" || status === "PROCESSING"
}

function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

export function MaterialsView({
  spaceId,
  topicId,
  initialMaterials,
  loadError,
  header,
}: MaterialsViewProps) {
  const dict = useT()
  const t = dict.materials
  const router = useRouter()
  const { status } = useSession()

  const fileInputRef = React.useRef<HTMLInputElement>(null)

  const [materials, setMaterials] =
    React.useState<MaterialResponse[]>(initialMaterials)
  const [topics, setTopics] = React.useState<TopicResponse[]>([])
  const [uploadError, setUploadError] = React.useState<string | null>(null)
  const [uploading, setUploading] = React.useState(false)
  const [dragActive, setDragActive] = React.useState(false)
  const [moveTarget, setMoveTarget] = React.useState<MaterialResponse | null>(
    null
  )
  const [moving, setMoving] = React.useState(false)

  React.useEffect(() => {
    if (status !== "authenticated") return
    let cancelled = false
    void (async () => {
      try {
        const list = await listTopics()
        if (!cancelled) setTopics(list)
      } catch {
        // ignore
      }
    })()
    return () => {
      cancelled = true
    }
  }, [status])

  React.useEffect(() => {
    if (status !== "authenticated") return
    const inflight = materials.some((m) => isProcessing(m.status))
    if (!inflight) return
    const handle = setInterval(async () => {
      const updates = await Promise.all(
        materials.map(async (m) => {
          if (!isProcessing(m.status)) return m
          try {
            const fresh = await getMaterial(m.id)
            return fresh ?? m
          } catch {
            return m
          }
        })
      )
      setMaterials(updates)
    }, 2000)
    return () => clearInterval(handle)
  }, [status, materials])

  async function handleFileUpload(file: File | null) {
    if (!file) return
    if (status !== "authenticated") {
      router.push("/login")
      return
    }
    setUploading(true)
    setUploadError(null)
    try {
      const created = await uploadMaterialFile(spaceId, file, topicId)
      if (created) {
        setMaterials((prev) => [created, ...prev])
      }
      toast.success(file.name)
      router.refresh()
    } catch (error) {
      if (error instanceof ApiClientError) {
        const code = error.errors[0]?.code
        setUploadError(t.errors[materialErrorMessageKey(code)])
      } else {
        setUploadError(t.errors.generic)
      }
    } finally {
      setUploading(false)
    }
  }

  async function handleDelete(material: MaterialResponse) {
    if (status !== "authenticated") {
      router.push("/login")
      return
    }
    if (typeof window !== "undefined" && !window.confirm(t.confirmDelete)) {
      return
    }
    try {
      await deleteMaterial(material.id)
      setMaterials((prev) => prev.filter((m) => m.id !== material.id))
      router.refresh()
    } catch (error) {
      if (error instanceof ApiClientError) {
        const code = error.errors[0]?.code
        toast.error(t.errors[materialErrorMessageKey(code)])
      } else {
        toast.error(t.errors.generic)
      }
    }
  }

  async function handleMove(
    material: MaterialResponse,
    targetTopicId: string | null
  ) {
    if (status !== "authenticated") {
      router.push("/login")
      return
    }
    setMoving(true)
    try {
      await moveMaterial(material.id, targetTopicId)
      const current = topicId ?? null
      if (targetTopicId !== current) {
        setMaterials((prev) => prev.filter((m) => m.id !== material.id))
      }
      setMoveTarget(null)
      toast.success(t.move.success)
      router.refresh()
    } catch (error) {
      if (error instanceof ApiClientError) {
        const code = error.errors[0]?.code
        toast.error(t.errors[materialErrorMessageKey(code)])
      } else {
        toast.error(t.errors.generic)
      }
    } finally {
      setMoving(false)
    }
  }

  const currentTopicId = topicId ?? null

  return (
    <div
      className={cn(
        "relative flex flex-col gap-6 rounded-lg transition-colors",
        dragActive &&
          "outline-2 outline-offset-4 outline-primary outline-dashed"
      )}
      onDragOver={(event) => {
        event.preventDefault()
        if (!uploading) setDragActive(true)
      }}
      onDragLeave={(event) => {
        if (event.currentTarget.contains(event.relatedTarget as Node)) return
        setDragActive(false)
      }}
      onDrop={(event) => {
        event.preventDefault()
        setDragActive(false)
        if (uploading) return
        void handleFileUpload(event.dataTransfer.files?.[0] ?? null)
      }}
    >
      <input
        ref={fileInputRef}
        type="file"
        accept=".pdf,.docx,.txt,.md,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document,text/plain,text/markdown"
        className="hidden"
        onChange={(event) => {
          const file = event.target.files?.[0] ?? null
          event.target.value = ""
          void handleFileUpload(file)
        }}
        disabled={uploading}
      />

      {dragActive ? (
        <div className="pointer-events-none absolute inset-0 z-10 flex flex-col items-center justify-center gap-2 rounded-lg bg-background/80 text-sm font-medium text-foreground backdrop-blur-sm">
          <HugeiconsIcon icon={Upload04Icon} size={28} />
          <span>{t.uploadDropZone}</span>
        </div>
      ) : null}

      <div
        className={cn(
          "flex flex-wrap items-start gap-3",
          header ? "justify-between" : "justify-end"
        )}
      >
        {header}
        <Button
          type="button"
          onClick={() => fileInputRef.current?.click()}
          disabled={uploading}
        >
          {uploading ? (
            <Spinner className="size-4" />
          ) : (
            <HugeiconsIcon icon={Upload04Icon} size={16} />
          )}
          <span>{t.uploadTitle}</span>
        </Button>
      </div>

      {loadError ? (
        <Alert variant="destructive">
          <AlertDescription>{t.failedToLoad}</AlertDescription>
        </Alert>
      ) : null}

      {uploadError ? (
        <Alert variant="destructive">
          <AlertDescription>{uploadError}</AlertDescription>
        </Alert>
      ) : null}

      {materials.length === 0 ? (
        <Empty>
          <EmptyHeader>
            <EmptyMedia variant="icon">
              <HugeiconsIcon icon={DocumentAttachmentIcon} strokeWidth={2} />
            </EmptyMedia>
            <EmptyTitle>{t.listTitle}</EmptyTitle>
            <EmptyDescription>{t.empty}</EmptyDescription>
          </EmptyHeader>
        </Empty>
      ) : (
        <ul className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          {materials.map((material) => (
            <li key={material.id}>
              <Card>
                <CardContent className="flex items-start justify-between gap-3 p-4">
                  <div className="flex flex-1 items-start gap-3">
                    <span className="mt-0.5 inline-flex size-9 items-center justify-center rounded-md bg-muted text-muted-foreground">
                      <HugeiconsIcon icon={DocumentAttachmentIcon} size={18} />
                    </span>
                    <div className="flex flex-col gap-1">
                      <span className="font-medium">{material.title}</span>
                      <div className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
                        <MaterialStatusPill status={material.status} />
                        <span>
                          {new Date(material.createdAt).toLocaleDateString()}
                        </span>
                        {material.sizeBytes ? (
                          <span>· {formatSize(material.sizeBytes)}</span>
                        ) : null}
                      </div>
                    </div>
                  </div>
                  <div className="flex items-center gap-1">
                    <Button
                      type="button"
                      variant="ghost"
                      size="icon"
                      aria-label={t.move.action}
                      onClick={() => setMoveTarget(material)}
                    >
                      <HugeiconsIcon icon={Folder01Icon} size={16} />
                    </Button>
                    <Button
                      type="button"
                      variant="ghost"
                      size="icon"
                      aria-label={t.delete}
                      onClick={() => handleDelete(material)}
                    >
                      <HugeiconsIcon icon={Delete02Icon} size={16} />
                    </Button>
                  </div>
                </CardContent>
              </Card>
            </li>
          ))}
        </ul>
      )}

      <Dialog
        open={moveTarget !== null}
        onOpenChange={(open) => {
          if (!open) setMoveTarget(null)
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t.move.title}</DialogTitle>
            <DialogDescription>{t.move.subtitle}</DialogDescription>
          </DialogHeader>
          <div className="flex flex-col gap-2">
            <Button
              type="button"
              variant="outline"
              className="justify-between"
              disabled={moving || currentTopicId === null}
              onClick={() => moveTarget && handleMove(moveTarget, null)}
            >
              <span className="flex items-center gap-2">
                <HugeiconsIcon icon={DocumentAttachmentIcon} size={16} />
                {t.move.root}
              </span>
              {currentTopicId === null ? (
                <span className="text-xs text-muted-foreground">
                  {t.move.current}
                </span>
              ) : null}
            </Button>
            {topics.map((topic) => {
              const isCurrent = topic.id === currentTopicId
              return (
                <Button
                  key={`move-${topic.id}`}
                  type="button"
                  variant="outline"
                  className="justify-between"
                  disabled={moving || isCurrent}
                  onClick={() => moveTarget && handleMove(moveTarget, topic.id)}
                >
                  <span className="flex items-center gap-2">
                    <HugeiconsIcon icon={Folder01Icon} size={16} />
                    <span className="truncate">{topic.name}</span>
                  </span>
                  {isCurrent ? (
                    <span className="text-xs text-muted-foreground">
                      {t.move.current}
                    </span>
                  ) : null}
                </Button>
              )
            })}
          </div>
        </DialogContent>
      </Dialog>
    </div>
  )
}
