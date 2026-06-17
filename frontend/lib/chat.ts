import { apiFetch } from "@/lib/api"

export type Citation = {
  materialId: string
  materialName: string
  chunkIndex: number
  preview: string
  score: number
}

export type ChatRole = "USER" | "ASSISTANT"

export type ChatMessage = {
  id: string
  conversationId: string
  role: ChatRole
  content: string
  citations: Citation[]
  createdAt: string
}

export type Conversation = {
  id: string
  topicId: string
  title: string
  createdAt: string
  updatedAt: string
  lastPreview?: string
}

export type ChatAskResponse = {
  conversationId: string
  answer: string
  citations: Citation[]
}

export async function listConversations(
  topicId?: string
): Promise<Conversation[]> {
  const qs = topicId ? `?topicId=${encodeURIComponent(topicId)}` : ""
  const data = await apiFetch<Conversation[]>(`/chat/conversations${qs}`, {
    cache: "no-store",
  })
  return data ?? []
}

export async function getConversationMessages(
  conversationId: string
): Promise<{ conversation: Conversation; messages: ChatMessage[] }> {
  const data = await apiFetch<{
    conversation: Conversation
    messages: ChatMessage[]
  }>(`/chat/conversations/${conversationId}/messages`, {
    cache: "no-store",
  })
  return (
    data ?? {
      conversation: {
        id: conversationId,
        topicId: "",
        title: "",
        createdAt: "",
        updatedAt: "",
      },
      messages: [],
    }
  )
}

export async function askChat(body: {
  topicId: string
  message: string
  conversationId?: string
}): Promise<ChatAskResponse> {
  const data = await apiFetch<ChatAskResponse>("/chat", {
    method: "POST",
    body,
  })
  if (!data) throw new Error("Empty chat response")
  return data
}
