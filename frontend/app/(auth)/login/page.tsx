"use client"

import Link from "next/link"
import { HugeiconsIcon } from "@hugeicons/react"
import { SparklesIcon } from "@hugeicons/core-free-icons"

import { AuthErrorToast } from "@/components/auth/auth-error-toast"
import { DemoSignInButton } from "@/components/auth/demo-sign-in-button"
import { GoogleSignInButton } from "@/components/auth/google-sign-in-button"
import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"
import { useT } from "@/lib/i18n/provider"

export default function LoginPage() {
  const t = useT()

  return (
    <Card className="border-border/60 bg-card/80 shadow-xl backdrop-blur supports-[backdrop-filter]:bg-card/60">
      <AuthErrorToast />
      <CardHeader className="flex flex-col items-center gap-3 text-center">
        <span className="inline-flex size-11 items-center justify-center rounded-2xl bg-primary/10 text-primary lg:hidden">
          <HugeiconsIcon
            icon={SparklesIcon}
            strokeWidth={2}
            className="size-5"
          />
        </span>
        <CardTitle className="text-2xl font-semibold tracking-tight">
          {t.login.title}
        </CardTitle>
        <CardDescription>{t.login.subtitle}</CardDescription>
      </CardHeader>

      <CardContent className="flex flex-col gap-4">
        <GoogleSignInButton mode="signin" />
        <DemoSignInButton />
        <p className="text-center text-xs leading-relaxed text-muted-foreground">
          {t.login.moreOptions}
        </p>
      </CardContent>

      <CardFooter className="flex justify-center">
        <p className="text-sm text-muted-foreground">
          {t.login.noAccount}{" "}
          <Link
            href="/signup"
            className="font-medium text-foreground underline-offset-4 hover:underline"
          >
            {t.login.createAccount}
          </Link>
        </p>
      </CardFooter>
    </Card>
  )
}
