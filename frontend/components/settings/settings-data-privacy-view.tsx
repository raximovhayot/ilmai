"use client"

import * as React from "react"
import { signOut, useSession } from "next-auth/react"
import { toast } from "sonner"
import { HugeiconsIcon } from "@hugeicons/react"
import {
  AlertCircleIcon,
  Delete02Icon,
  Logout03Icon,
  RefreshIcon,
} from "@hugeicons/core-free-icons"

import { Alert, AlertDescription } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardFooter } from "@/components/ui/card"
import { Separator } from "@/components/ui/separator"
import { Spinner } from "@/components/ui/spinner"
import { apiFetch } from "@/lib/api"
import { useT } from "@/lib/i18n/provider"

import { SettingsPageShell, SettingsRow } from "./settings-shared"

const DEMO_MODE =
  process.env.NEXT_PUBLIC_DEMO_MODE === "1" ||
  process.env.NEXT_PUBLIC_MOCK_API === "1"

export function SettingsDataPrivacyView() {
  const t = useT()
  const { status } = useSession()

  const [signingOut, setSigningOut] = React.useState(false)
  const [resetting, setResetting] = React.useState(false)

  const handleSignOut = () => {
    if (signingOut) return
    setSigningOut(true)
    void signOut({ callbackUrl: "/login" }).catch(() => setSigningOut(false))
  }

  const handleReset = async () => {
    if (status !== "authenticated" || resetting) return
    if (!window.confirm(t.settings.danger.resetConfirm)) return
    setResetting(true)
    try {
      await apiFetch<{ ok: boolean }>("/demo/reset", {
        method: "POST",
      })
      toast.success(t.settings.danger.resetSuccess)
      window.location.reload()
    } catch {
      toast.error(t.errors.generic)
    } finally {
      setResetting(false)
    }
  }

  return (
    <SettingsPageShell
      title={t.settings.danger.title}
      subtitle={t.settings.danger.subtitle}
      icon={AlertCircleIcon}
    >
      <Card className="border-destructive/30">
        <CardContent className="flex flex-col p-0">
          {DEMO_MODE && (
            <>
              <SettingsRow
                icon={RefreshIcon}
                label={t.settings.danger.resetLabel}
                description={t.settings.danger.resetDescription}
              >
                <Button
                  size="sm"
                  variant="outline"
                  onClick={handleReset}
                  disabled={resetting}
                >
                  {resetting ? (
                    <Spinner data-icon="inline-start" />
                  ) : (
                    <HugeiconsIcon
                      icon={Delete02Icon}
                      strokeWidth={2}
                      data-icon="inline-start"
                    />
                  )}
                  {t.settings.danger.resetButton}
                </Button>
              </SettingsRow>
              <Separator />
            </>
          )}
          <SettingsRow
            icon={Logout03Icon}
            label={t.settings.danger.signOutLabel}
            description={t.settings.danger.signOutDescription}
          >
            <Button
              size="sm"
              variant="outline"
              className="border-destructive/40 text-destructive hover:bg-destructive/10 hover:text-destructive"
              onClick={handleSignOut}
              disabled={signingOut}
            >
              {signingOut ? (
                <Spinner data-icon="inline-start" />
              ) : (
                <HugeiconsIcon
                  icon={Logout03Icon}
                  strokeWidth={2}
                  data-icon="inline-start"
                />
              )}
              {t.settings.danger.signOutButton}
            </Button>
          </SettingsRow>
        </CardContent>
        {!DEMO_MODE && (
          <CardFooter>
            <Alert>
              <HugeiconsIcon icon={AlertCircleIcon} strokeWidth={2} />
              <AlertDescription>{t.settings.danger.warning}</AlertDescription>
            </Alert>
          </CardFooter>
        )}
      </Card>
    </SettingsPageShell>
  )
}
