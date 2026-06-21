"use client"

import * as React from "react"
import { signOut, useSession } from "next-auth/react"
import { toast } from "sonner"

import { useT } from "@/lib/i18n/provider"

function SessionGuard() {
  const t = useT()
  const { data: session } = useSession()
  const handled = React.useRef(false)

  React.useEffect(() => {
    if (handled.current) return
    if (session?.error !== "RefreshAccessTokenError") return
    handled.current = true
    toast.error(t.errors.sessionExpired)
    void signOut({ callbackUrl: "/login" })
  }, [session?.error, t.errors.sessionExpired])

  return null
}

export { SessionGuard }
