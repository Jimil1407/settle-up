import type {
  ApiErrorBody,
  AuthResponse,
  Expense,
  Group,
  GroupBalances,
  SettlementPlan,
} from './types'

const TOKEN_KEY = 'settleup.token'

/**
 * Where the API lives.
 *
 * In development this stays empty, so requests go to a relative `/api/...` and Vite's dev proxy
 * forwards them to the local backend — same origin, no CORS.
 *
 * In production the build receives `VITE_API_HOST` (a bare hostname, because Render blueprints
 * expose service hostnames without a scheme and do not support string interpolation), and we
 * prepend https:// ourselves.
 */
const rawHost = (import.meta.env.VITE_API_HOST as string | undefined)?.trim().replace(/^https?:\/\//, '')
const isLocal = rawHost?.startsWith('localhost') || rawHost?.startsWith('127.0.0.1')
const API_HOST = rawHost && !rawHost.includes('.') && !isLocal ? `${rawHost}.onrender.com` : rawHost
const API_ROOT = API_HOST ? (isLocal ? `http://${API_HOST}` : `https://${API_HOST}`) : ''

console.log('[SettleUp] API Endpoint configured as:', API_ROOT || '(relative / same-origin)')

/** Absolute URL for an API path, used for links the browser follows directly (e.g. CSV export). */
export function apiUrl(path: string): string {
  return `${API_ROOT}/api${path}`
}

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

export function setToken(token: string | null) {
  if (token) localStorage.setItem(TOKEN_KEY, token)
  else localStorage.removeItem(TOKEN_KEY)
}

export class ApiError extends Error {
  status: number
  body: ApiErrorBody | null

  constructor(status: number, body: ApiErrorBody | null, message: string) {
    super(message)
    this.status = status
    this.body = body
  }

  /** A settlement plan was overtaken by another write; the caller should refetch and retry. */
  get isStalePlan() {
    return this.status === 409
  }
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const token = getToken()
  const response = await fetch(apiUrl(path), {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...options.headers,
    },
  })

  if (!response.ok) {
    let body: ApiErrorBody | null = null
    try {
      body = await response.json()
    } catch {
      // Non-JSON error pages (a proxy timeout, say) still need to surface something readable.
    }
    const detail = body?.details?.length ? `: ${body.details.join(', ')}` : ''
    throw new ApiError(response.status, body, (body?.message ?? response.statusText) + detail)
  }

  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}

/**
 * Generates a fresh idempotency key per user action. The key identifies *the intent*, so a retry
 * of the same click reuses it and cannot double-charge, while a genuinely new click gets a new one.
 */
export function newIdempotencyKey(): string {
  return crypto.randomUUID()
}

export const api = {
  register: (email: string, displayName: string, password: string) =>
    request<AuthResponse>('/auth/register', {
      method: 'POST',
      body: JSON.stringify({ email, displayName, password }),
    }),

  login: (email: string, password: string) =>
    request<AuthResponse>('/auth/login', {
      method: 'POST',
      body: JSON.stringify({ email, password }),
    }),

  listGroups: () => request<Group[]>('/groups'),

  createGroup: (name: string) =>
    request<Group>('/groups', {
      method: 'POST',
      body: JSON.stringify({ name, currency: 'INR' }),
    }),

  getGroup: (groupId: number) => request<Group>(`/groups/${groupId}`),

  addMember: (groupId: number, email: string) =>
    request<Group>(`/groups/${groupId}/members`, {
      method: 'POST',
      body: JSON.stringify({ email }),
    }),

  balances: (groupId: number) => request<GroupBalances>(`/groups/${groupId}/balances`),

  listExpenses: (groupId: number) => request<Expense[]>(`/groups/${groupId}/expenses`),

  addExpense: (
    groupId: number,
    body: {
      description: string
      amount: string
      paidByUserId: number
      splitType: string
      participantIds?: number[]
    },
    idempotencyKey: string,
  ) =>
    request<Expense>(`/groups/${groupId}/expenses`, {
      method: 'POST',
      headers: { 'Idempotency-Key': idempotencyKey },
      body: JSON.stringify(body),
    }),

  settlementPlan: (groupId: number) => request<SettlementPlan>(`/groups/${groupId}/settlement-plan`),

  /**
   * Downloads the CSV export.
   *
   * Fetched rather than linked: the export endpoint requires a bearer token, and a plain
   * `<a href>` navigation cannot carry an Authorization header, so it would simply 401.
   */
  exportCsv: async (groupId: number): Promise<Blob> => {
    const token = getToken()
    const response = await fetch(apiUrl(`/groups/${groupId}/export/expenses.csv`), {
      headers: token ? { Authorization: `Bearer ${token}` } : {},
    })
    if (!response.ok) throw new ApiError(response.status, null, 'export failed')
    return response.blob()
  },

  recordSettlement: (
    groupId: number,
    body: { fromUserId: number; toUserId: number; amount: string; expectedVersion: number },
    idempotencyKey: string,
  ) =>
    request<unknown>(`/groups/${groupId}/settlements`, {
      method: 'POST',
      headers: { 'Idempotency-Key': idempotencyKey },
      body: JSON.stringify(body),
    }),
}
