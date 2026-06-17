"use client"

import * as React from "react"

const ENABLED =
  process.env.NEXT_PUBLIC_MOCK_API === "1" ||
  process.env.NEXT_PUBLIC_DEMO_MODE === "1"

let workerStarted = false
let startPromise: Promise<void> | null = null

async function ensureWorker(): Promise<void> {
  if (!ENABLED || workerStarted) return
  if (startPromise) return startPromise
  startPromise = (async () => {
    const { worker } = await import("@/mocks/browser")
    await worker.start({
      onUnhandledRequest: "bypass",
      quiet: true,
      serviceWorker: { url: "/mockServiceWorker.js" },
    })
    workerStarted = true
  })()
  return startPromise
}

export function MockProvider({ children }: { children: React.ReactNode }) {
  const [ready, setReady] = React.useState(!ENABLED)
  React.useEffect(() => {
    if (!ENABLED) return
    let cancelled = false
    ensureWorker().then(() => {
      if (!cancelled) setReady(true)
    })
    return () => {
      cancelled = true
    }
  }, [])
  if (!ready) return null
  return <>{children}</>
}
