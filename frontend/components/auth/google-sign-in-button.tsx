"use client"

import * as React from "react"
import { signIn } from "next-auth/react"
import { HugeiconsIcon } from "@hugeicons/react"
import { GoogleIcon } from "@hugeicons/core-free-icons"

import { Button } from "@/components/ui/button"
import { Spinner } from "@/components/ui/spinner"
import { useT } from "@/lib/i18n/provider"

type Props = {
  mode: "signin" | "signup"
  redirectTo?: string
}

function GoogleSignInButton({ mode, redirectTo = "/" }: Props) {
  const t = useT()
  const [isPending, setPending] = React.useState(false)

  function handleClick() {
    setPending(true)
    signIn("google", { callbackUrl: redirectTo }).catch(() => {
      setPending(false)
    })
  }

  const label =
    mode === "signup" ? t.signup.continueWithGoogle : t.login.continueWithGoogle

  return (
    <Button
      type="button"
      variant="outline"
      size="lg"
      className="w-full rounded-full"
      disabled={isPending}
      aria-busy={isPending}
      onClick={handleClick}
    >
      {isPending ? (
        <Spinner className="size-5 text-foreground" />
      ) : (
        <HugeiconsIcon
          icon={GoogleIcon}
          strokeWidth={2}
          className="size-5"
          data-icon="inline-start"
        />
      )}
      <span>{label}</span>
    </Button>
  )
}

export { GoogleSignInButton }
