"use client"

import * as React from "react"
import { useSearchParams } from "next/navigation"
import { toast } from "sonner"

import { useT } from "@/lib/i18n/provider"

function AuthErrorToast() {
  const t = useT()
  const params = useSearchParams()
  const error = params.get("error")
  const shown = React.useRef(false)

  React.useEffect(() => {
    if (error && !shown.current) {
      shown.current = true
      toast.error(t.errors.signInFailed)
    }
  }, [error, t.errors.signInFailed])

  return null
}

export { AuthErrorToast }
