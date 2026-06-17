import { ApiClientError, apiFetch } from "@/lib/api"

export type TelegramLink = {
  id?: string | null
  telegramUsername?: string | null
  chatId?: number | null
  linkedAt?: string | null
  linkCode?: string | null
  linkCodeExpiresAt?: string | null
  botUsername?: string | null
}

export function isLinked(link: TelegramLink | null): boolean {
  return !!link?.linkedAt
}

export function isPending(link: TelegramLink | null): boolean {
  return !!link && !link.linkedAt && !!link.linkCode
}

export function telegramDeepLink(link: TelegramLink | null): string | null {
  if (!link?.botUsername || !link.linkCode) return null
  return `https://t.me/${link.botUsername}?start=${link.linkCode}`
}

export async function getTelegram(): Promise<TelegramLink | null> {
  try {
    return await apiFetch<TelegramLink>("/telegram", {
      cache: "no-store",
    })
  } catch (error) {
    if (error instanceof ApiClientError && error.status === 404) return null
    throw error
  }
}

export async function createLinkCode(): Promise<TelegramLink> {
  return await apiFetch<TelegramLink>("/telegram/link-code", {
    method: "POST",
  })
}

export async function unlinkTelegram(): Promise<void> {
  await apiFetch<void>("/telegram", {
    method: "DELETE",
  })
}
