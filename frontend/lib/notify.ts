import { toast } from "sonner"

import { ApiClientError } from "@/lib/api"

export type ErrorMessages = {
  generic: string
  network: string
}

export function describeError(error: unknown, messages: ErrorMessages): string {
  if (error instanceof ApiClientError) {
    if (error.status === 0) return messages.network
    const first = error.errors[0]
    if (first?.message) return first.message
    return messages.generic
  }
  if (error instanceof Error && error.message) return error.message
  return messages.generic
}

export function notifyError(error: unknown, messages: ErrorMessages): void {
  toast.error(describeError(error, messages))
}

export function notifySuccess(message: string): void {
  toast.success(message)
}

export { toast }
