"use client"

import * as React from "react"

import { AppSidebar } from "@/components/app-shell/sidebar"
import { BottomTabBar } from "@/components/app-shell/bottom-tab-bar"
import { MobileTopBar } from "@/components/app-shell/mobile-top-bar"
import type { UserSummary } from "@/components/app-shell/user-menu"
import type { TopicResponse } from "@/lib/topics"
import type { PremiumStatus } from "@/lib/premium"
import type { ChatSession } from "@/lib/agent"
import { cn } from "@/lib/utils"

type AppShellProps = {
  topics: TopicResponse[]
  sessions: ChatSession[]
  user: UserSummary
  premium: PremiumStatus | null
  children: React.ReactNode
}

export function AppShell({
  topics,
  sessions,
  user,
  premium,
  children,
}: AppShellProps) {
  const [collapsed, setCollapsed] = React.useState(false)

  return (
    <div className="flex min-h-dvh bg-background text-foreground">
      <AppSidebar
        topics={topics}
        sessions={sessions}
        premium={premium}
        user={user}
        collapsed={collapsed}
        onToggle={() => setCollapsed((c) => !c)}
      />
      <div
        className={cn(
          "flex min-w-0 flex-1 flex-col transition-[padding] duration-200",
          collapsed ? "lg:ps-16" : "lg:ps-64"
        )}
      >
        <MobileTopBar user={user} />
        <main className="flex-1 overflow-y-auto pb-[calc(4.5rem+env(safe-area-inset-bottom))] lg:pb-0">
          <div className="mx-auto w-full max-w-6xl px-4 py-6 md:px-6 md:py-8">
            {children}
          </div>
        </main>
        <BottomTabBar />
      </div>
    </div>
  )
}
