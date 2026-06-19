import { redirect } from "next/navigation"

import { auth } from "@/auth"

export default async function OnboardingLayout({
  children,
}: {
  children: React.ReactNode
}) {
  const session = await auth()

  if (!session?.user) {
    redirect("/login")
  }

  return <div className="min-h-dvh bg-background">{children}</div>
}
