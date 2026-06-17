"use client"

import * as React from "react"

import {
  DEFAULT_LANG,
  DICTIONARIES,
  LANGUAGES,
  type Dictionary,
  type Lang,
} from "@/lib/i18n/dictionary"

const STORAGE_KEY = "ilmai.lang"

function isLang(value: unknown): value is Lang {
  return (
    typeof value === "string" &&
    (LANGUAGES as readonly string[]).includes(value)
  )
}

function readStoredLang(): Lang {
  if (typeof window === "undefined") {
    return DEFAULT_LANG
  }
  try {
    const stored = window.localStorage.getItem(STORAGE_KEY)
    if (isLang(stored)) {
      return stored
    }
    const navLang = window.navigator.language.slice(0, 2).toLowerCase()
    if (isLang(navLang)) {
      return navLang
    }
  } catch {
    void 0
  }
  return DEFAULT_LANG
}

const listeners = new Set<() => void>()
let currentLang: Lang | null = null

function getClientSnapshot(): Lang {
  if (currentLang === null) {
    currentLang = readStoredLang()
  }
  return currentLang
}

function getServerSnapshot(): Lang {
  return DEFAULT_LANG
}

function notify() {
  listeners.forEach((listener) => listener())
}

function subscribe(listener: () => void): () => void {
  listeners.add(listener)
  const onStorage = (event: StorageEvent) => {
    if (event.key !== STORAGE_KEY) return
    const next = readStoredLang()
    if (next !== currentLang) {
      currentLang = next
      notify()
    }
  }
  window.addEventListener("storage", onStorage)
  return () => {
    listeners.delete(listener)
    window.removeEventListener("storage", onStorage)
  }
}

function writeStoredLang(next: Lang) {
  if (currentLang === next) return
  currentLang = next
  try {
    window.localStorage.setItem(STORAGE_KEY, next)
  } catch {
    void 0
  }
  notify()
}

type LanguageContextValue = {
  lang: Lang
  setLang: (lang: Lang) => void
  t: Dictionary
}

const LanguageContext = React.createContext<LanguageContextValue | null>(null)

function LanguageProvider({ children }: { children: React.ReactNode }) {
  const lang = React.useSyncExternalStore(
    subscribe,
    getClientSnapshot,
    getServerSnapshot
  )

  React.useEffect(() => {
    if (typeof document !== "undefined") {
      document.documentElement.lang = lang
    }
  }, [lang])

  const value = React.useMemo<LanguageContextValue>(
    () => ({ lang, setLang: writeStoredLang, t: DICTIONARIES[lang] }),
    [lang]
  )

  return (
    <LanguageContext.Provider value={value}>
      {children}
    </LanguageContext.Provider>
  )
}

function useLanguage(): LanguageContextValue {
  const ctx = React.useContext(LanguageContext)
  if (!ctx) {
    throw new Error("useLanguage must be used within <LanguageProvider>")
  }
  return ctx
}

function useT(): Dictionary {
  return useLanguage().t
}

export { LanguageProvider, useLanguage, useT }
