"use client"

import * as React from "react"

import { useApi, type ApiCall } from "@/hooks/use-api"
import { ApiClientError } from "@/lib/api"

export type ApiResourceState<T> = {
  data: T | undefined
  error: ApiClientError | null
  loading: boolean
  refreshing: boolean
  reload: () => void
  setData: React.Dispatch<React.SetStateAction<T | undefined>>
}

type Options = {
  enabled?: boolean
}

function toApiClientError(error: unknown): ApiClientError {
  if (error instanceof ApiClientError) return error
  const message = error instanceof Error ? error.message : "Unexpected error"
  return new ApiClientError(0, [{ code: "UNKNOWN", message }])
}

export function useApiResource<T>(
  loader: ApiCall<T>,
  deps: React.DependencyList = [],
  options: Options = {}
): ApiResourceState<T> {
  const enabled = options.enabled ?? true
  const { run, status } = useApi()

  const [data, setData] = React.useState<T | undefined>(undefined)
  const [error, setError] = React.useState<ApiClientError | null>(null)
  const [loading, setLoading] = React.useState<boolean>(enabled)
  const [refreshing, setRefreshing] = React.useState<boolean>(false)
  const [reloadToken, setReloadToken] = React.useState(0)

  const loaderRef = React.useRef(loader)
  const runRef = React.useRef(run)

  React.useEffect(() => {
    loaderRef.current = loader
    runRef.current = run
  })

  React.useEffect(() => {
    if (!enabled) return
    if (status !== "authenticated") return

    let cancelled = false
    void (async () => {
      try {
        const result = await runRef.current(() => loaderRef.current())
        if (!cancelled) {
          setData(result)
          setError(null)
        }
      } catch (caught) {
        if (!cancelled) setError(toApiClientError(caught))
      } finally {
        if (!cancelled) {
          setLoading(false)
          setRefreshing(false)
        }
      }
    })()

    return () => {
      cancelled = true
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [enabled, status, reloadToken, ...deps])

  const reload = React.useCallback(() => {
    setRefreshing(true)
    setError(null)
    setReloadToken((value) => value + 1)
  }, [])

  return { data, error, loading, refreshing, reload, setData }
}
