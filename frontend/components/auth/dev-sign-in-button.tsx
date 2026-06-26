"use client"

import * as React from "react"
import { signIn } from "next-auth/react"
import { HugeiconsIcon } from "@hugeicons/react"
import { CodeIcon } from "@hugeicons/core-free-icons"

import { Button } from "@/components/ui/button"
import { Spinner } from "@/components/ui/spinner"

const DEV_LOGIN = process.env.NEXT_PUBLIC_DEV_LOGIN === "1"

export function DevSignInButton() {
  const [loading, setLoading] = React.useState(false)
  if (!DEV_LOGIN) return null

  const onClick = async () => {
    setLoading(true)
    try {
      await signIn("dev", { callbackUrl: "/home" })
    } finally {
      setLoading(false)
    }
  }

  return (
    <>
      <div className="my-1 flex items-center gap-3 text-xs text-muted-foreground">
        <span className="h-px flex-1 bg-border" />
        <span>Dev login</span>
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
            icon={CodeIcon}
            strokeWidth={2}
            data-icon="inline-start"
          />
        )}
        Continue as dev user
      </Button>
    </>
  )
}
