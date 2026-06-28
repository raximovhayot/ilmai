import { apiFetch } from "@/lib/api"
import type { TopicResponse } from "@/lib/topics"

export type MaterialStatus = "PENDING" | "PROCESSING" | "READY" | "FAILED"

export type SpaceContentsResponse = {
  topics: TopicResponse[]
  items: MaterialResponse[]
  page: number
  size: number
  hasMore: boolean
}

export type MaterialResponse = {
  id: string
  topicId: string
  title: string
  contentType?: string | null
  sizeBytes?: number | null
  status: MaterialStatus
  retryCount?: number
  createdAt: string
  updatedAt: string
}

export async function listMaterials(
  topicId?: string | null,
  roomId?: string | null
): Promise<MaterialResponse[]> {
  const params = new URLSearchParams()
  if (roomId) {
    params.set("roomId", roomId)
  }
  if (topicId) {
    params.set("topicId", topicId)
  }
  const query = params.toString()
  const path = query ? `/materials?${query}` : "/materials"
  const data = await apiFetch<MaterialResponse[]>(path, {
    cache: "no-store",
  })
  return data ?? []
}

export async function getSpaceContents(
  page = 0,
  size = 24,
  roomId?: string | null
): Promise<SpaceContentsResponse> {
  const params = new URLSearchParams()
  if (roomId) {
    params.set("roomId", roomId)
  }
  params.set("page", String(page))
  params.set("size", String(size))
  const data = await apiFetch<SpaceContentsResponse>(
    `/materials/contents?${params.toString()}`,
    { cache: "no-store" }
  )
  return data ?? { topics: [], items: [], page, size, hasMore: false }
}

export async function getMaterial(
  materialId: string
): Promise<MaterialResponse | null> {
  return await apiFetch<MaterialResponse>(`/materials/${materialId}`, {
    cache: "no-store",
  })
}

export async function moveMaterial(
  materialId: string,
  topicId: string | null
): Promise<MaterialResponse | null> {
  return await apiFetch<MaterialResponse>(`/materials/${materialId}`, {
    method: "PATCH",
    body: { topicId },
  })
}

export async function uploadMaterialFile(
  spaceId: string,
  file: File,
  topicId?: string | null
): Promise<MaterialResponse | null> {
  const form = new FormData()
  form.append("spaceId", spaceId)
  if (topicId) {
    form.append("topicId", topicId)
  }
  form.append("file", file)
  return await apiFetch<MaterialResponse>("/materials", {
    method: "POST",
    body: form,
  })
}

export async function uploadMaterialPaste(
  spaceId: string,
  title: string,
  pastedText: string,
  topicId?: string | null
): Promise<MaterialResponse | null> {
  const safeTitle = title.trim()
  const fileName = /\.(txt|md)$/i.test(safeTitle)
    ? safeTitle
    : `${safeTitle}.txt`
  const file = new File([pastedText], fileName, { type: "text/plain" })
  return await uploadMaterialFile(spaceId, file, topicId)
}

export async function deleteMaterial(materialId: string): Promise<void> {
  await apiFetch<void>(`/materials/${materialId}`, {
    method: "DELETE",
  })
}
