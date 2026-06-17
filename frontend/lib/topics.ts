import { apiFetch } from "@/lib/api"

export type TopicResponse = {
  id: string
  spaceId: string
  name: string
  createdAt: string
  updatedAt: string
}

export async function listTopics(): Promise<TopicResponse[]> {
  const data = await apiFetch<TopicResponse[]>("/topics", {
    cache: "no-store",
  })
  return data ?? []
}

export async function createTopic(name: string): Promise<TopicResponse | null> {
  return await apiFetch<TopicResponse>("/topics", {
    method: "POST",
    body: { name },
  })
}

export async function renameTopic(
  topicId: string,
  name: string
): Promise<TopicResponse | null> {
  return await apiFetch<TopicResponse>(`/topics/${topicId}`, {
    method: "PATCH",
    body: { name },
  })
}

export async function deleteTopic(topicId: string): Promise<void> {
  await apiFetch<void>(`/topics/${topicId}`, {
    method: "DELETE",
  })
}
