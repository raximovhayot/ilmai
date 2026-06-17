"use client"

import * as React from "react"
import { signIn } from "next-auth/react"
import { HugeiconsIcon } from "@hugeicons/react"
import { SparklesIcon } from "@hugeicons/core-free-icons"

import { Button } from "@/components/ui/button"
import { Spinner } from "@/components/ui/spinner"
import { useT } from "@/lib/i18n/provider"

const DEMO_MODE = process.env.NEXT_PUBLIC_DEMO_MODE === "1"

export function DemoSignInButton() {
  const t = useT()
  const [loading, setLoading] = React.useState(false)
  if (!DEMO_MODE) return null

  const onClick = async () => {
    setLoading(true)
    try {
      await signIn("demo", { callbackUrl: "/home" })
    } finally {
      setLoading(false)
    }
  }

  return (
    <>
      <div className="my-1 flex items-center gap-3 text-xs text-muted-foreground">
        <span className="h-px flex-1 bg-border" />
        <span>{t.nav.demoMode}</span>
        <span className="h-px flex-1 bg-border" />
      </div>
      <Button
        variant="outline"
        type="button"
        onClick={onClick}
        disabled={loading}
        className="w-full"
      >
        {loading ? (
          <Spinner data-icon="inline-start" />
        ) : (
          <HugeiconsIcon
            icon={SparklesIcon}
            strokeWidth={2}
            data-icon="inline-start"
          />
        )}
        Continue as demo user
      </Button>
    </>
  )
}
