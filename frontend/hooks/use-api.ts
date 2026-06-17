"use client"

import * as React from "react"
import { useRouter } from "next/navigation"
import { useSession } from "next-auth/react"

import { ApiClientError } from "@/lib/api"

export type ApiCall<T> = () => Promise<T>

export type SessionStatus = "authenticated" | "loading" | "unauthenticated"

export type UseApiResult = {
  authenticated: boolean
  status: SessionStatus
  run: <T>(call: ApiCall<T>) => Promise<T>
}

function unauthenticated(): ApiClientError {
  return new ApiClientError(401, [
    { code: "AUTH_UNAUTHENTICATED", message: "Sign in to continue." },
  ])
}

function isUnauthorized(error: unknown): boolean {
  return error instanceof ApiClientError && error.status === 401
}

export function useApi(): UseApiResult {
  const { status, update } = useSession()
  const router = useRouter()
  const authenticated = status === "authenticated"

  const run = React.useCallback(
    async <T>(call: ApiCall<T>): Promise<T> => {
      if (status !== "authenticated") {
        router.push("/login")
        throw unauthenticated()
      }
      try {
        return await call()
      } catch (error) {
        if (!isUnauthorized(error)) throw error
        await update()
        try {
          return await call()
        } catch (retryError) {
          if (isUnauthorized(retryError)) router.push("/login")
          throw retryError
        }
      }
    },
    [status, update, router]
  )

  return { authenticated, status, run }
}
