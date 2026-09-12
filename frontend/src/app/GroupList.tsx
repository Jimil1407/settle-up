import { motion } from 'framer-motion'
import { ArrowRight, Plus, Users } from 'lucide-react'
import { useEffect, useState } from 'react'
import { api } from '../api'
import { EASE } from '../components/Reveal'
import type { Group } from '../types'

export function GroupList({ onOpen }: { onOpen: (groupId: number) => void }) {
  const [groups, setGroups] = useState<Group[]>([])
  const [name, setName] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [loading, setLoading] = useState(true)

  async function load() {
    try {
      setGroups(await api.listGroups())
    } catch (err) {
      setError((err as Error).message)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void load()
  }, [])

  async function create(event: React.FormEvent) {
    event.preventDefault()
    if (!name.trim()) return
    setBusy(true)
    setError(null)
    try {
      await api.createGroup(name.trim())
      setName('')
      await load()
    } catch (err) {
      setError((err as Error).message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <>
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="heading-md">Your groups</h1>
          <p className="mt-1.5 text-sm muted">Flats, trips, and anything else you share.</p>
        </div>
      </div>

      {error && (
        <div
          role="alert"
          className="mt-6 rounded-xl border border-red-500/30 bg-red-500/[0.08] px-3.5 py-2.5
                     text-sm text-red-600 dark:text-red-400"
        >
          {error}
        </div>
      )}

      <form onSubmit={create} className="card mt-6 flex flex-col gap-3 p-4 sm:flex-row sm:items-end">
        <div className="flex-1">
          <label className="label" htmlFor="groupName">
            New group
          </label>
          <input
            id="groupName"
            className="input"
            value={name}
            placeholder="Flat 402, Goa trip…"
            onChange={(e) => setName(e.target.value)}
          />
        </div>
        <button type="submit" className="btn-primary" disabled={busy || !name.trim()}>
          <Plus className="h-4 w-4" />
          Create group
        </button>
      </form>

      {loading ? (
        <div className="mt-4 space-y-3">
          {[0, 1].map((i) => (
            <div key={i} className="h-[86px] animate-pulse rounded-2xl bg-[rgb(var(--surface-2))]" />
          ))}
        </div>
      ) : groups.length === 0 ? (
        <div className="card mt-4 grid place-items-center px-6 py-16 text-center">
          <span className="grid h-12 w-12 place-items-center rounded-2xl bg-[rgb(var(--surface-2))]">
            <Users className="h-5 w-5 muted" />
          </span>
          <h2 className="mt-4 font-semibold">No groups yet</h2>
          <p className="mt-1 max-w-xs text-sm muted">
            Create one above, add the people you share costs with, and start logging expenses.
          </p>
        </div>
      ) : (
        <div className="mt-4 space-y-3">
          {groups.map((group, i) => (
            <motion.button
              key={group.id}
              type="button"
              onClick={() => onOpen(group.id)}
              initial={{ opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.35, ease: EASE, delay: i * 0.04 }}
              className="group card flex w-full items-center justify-between gap-4 p-5 text-left
                         transition-all duration-300 hover:-translate-y-0.5
                         hover:border-accent-400/40 hover:shadow-card-hover"
            >
              <span className="min-w-0">
                <span className="block font-semibold tracking-[-0.01em]">{group.name}</span>
                <span className="mt-1 block truncate text-sm muted">
                  {group.members.length} member{group.members.length === 1 ? '' : 's'} ·{' '}
                  {group.members.map((m) => m.displayName).join(', ')}
                </span>
              </span>
              <span className="flex shrink-0 items-center gap-3">
                <span className="pill">v{group.version}</span>
                <ArrowRight
                  className="h-4 w-4 muted transition-transform duration-200 group-hover:translate-x-0.5
                             group-hover:text-accent-500"
                />
              </span>
            </motion.button>
          ))}
        </div>
      )}
    </>
  )
}
