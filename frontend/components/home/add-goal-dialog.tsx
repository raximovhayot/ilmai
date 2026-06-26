"use client"

import * as React from "react"
import { useRouter } from "next/navigation"
import { HugeiconsIcon } from "@hugeicons/react"
import { PlusSignIcon } from "@hugeicons/core-free-icons"

import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { useT } from "@/lib/i18n/provider"

type Props = {
  variant?: "default" | "outline" | "ghost"
  size?: "sm" | "default"
  withIcon?: boolean
  className?: string
}

export function AddGoalDialog({
  variant = "outline",
  size = "sm",
  withIcon = true,
  className,
}: Props) {
  const t = useT().home.addGoalDialog
  const router = useRouter()
  const [open, setOpen] = React.useState(false)
  const [goal, setGoal] = React.useState("")
  const [deadline, setDeadline] = React.useState("")

  const trimmedGoal = goal.trim()
  const canSubmit = trimmedGoal.length > 0

  const handleSubmit = React.useCallback(
    (event: React.FormEvent) => {
      event.preventDefault()
      if (!canSubmit) return
      const template = deadline
        ? t.seedWithDeadline.replace("{deadline}", deadline)
        : t.seedWithoutDeadline
      const seed = template.replace("{goal}", trimmedGoal)
      setOpen(false)
      router.push(`/companion?seed=${encodeURIComponent(seed)}`)
    },
    [canSubmit, deadline, router, t, trimmedGoal]
  )

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger
        render={<Button variant={variant} size={size} className={className} />}
      >
        {withIcon ? (
          <HugeiconsIcon
            icon={PlusSignIcon}
            strokeWidth={2}
            data-icon="inline-start"
          />
        ) : null}
        {t.trigger}
      </DialogTrigger>
      <DialogContent>
        <form onSubmit={handleSubmit} className="flex flex-col gap-6">
          <DialogHeader>
            <DialogTitle>{t.title}</DialogTitle>
            <DialogDescription>{t.description}</DialogDescription>
          </DialogHeader>

          <div className="flex flex-col gap-4">
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="add-goal-text">{t.goalLabel}</Label>
              <Input
                id="add-goal-text"
                value={goal}
                onChange={(e) => setGoal(e.target.value)}
                placeholder={t.goalPlaceholder}
                autoFocus
                required
              />
            </div>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="add-goal-deadline">{t.deadlineLabel}</Label>
              <Input
                id="add-goal-deadline"
                type="date"
                value={deadline}
                onChange={(e) => setDeadline(e.target.value)}
              />
              <p className="text-xs text-muted-foreground">{t.deadlineHint}</p>
            </div>
          </div>

          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              onClick={() => setOpen(false)}
            >
              {t.cancel}
            </Button>
            <Button type="submit" disabled={!canSubmit}>
              {t.submit}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
