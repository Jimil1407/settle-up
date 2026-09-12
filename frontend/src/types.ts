export interface AuthResponse {
  token: string
  userId: number
  email: string
  displayName: string
}

export interface Member {
  userId: number
  displayName: string
  email: string
}

export interface Group {
  id: number
  name: string
  currency: string
  version: number
  createdAt: string
  members: Member[]
}

export interface Balance {
  userId: number
  displayName: string
  balancePaise: number
  balanceRupees: string
  formatted: string
}

export interface GroupBalances {
  groupId: number
  version: number
  balances: Balance[]
  settled: boolean
}

export interface ExpenseSplit {
  userId: number
  displayName: string
  sharePaise: number
  shareRupees: string
}

export interface Expense {
  id: number
  groupId: number
  description: string
  totalPaise: number
  totalRupees: string
  formattedTotal: string
  paidByUserId: number
  paidByName: string
  splitType: 'EQUAL' | 'WEIGHTED' | 'EXACT'
  recurring: boolean
  createdAt: string
  splits: ExpenseSplit[]
}

export interface Transfer {
  fromUserId: number
  fromName: string
  toUserId: number
  toName: string
  amountPaise: number
  amountRupees: string
  formatted: string
}

export interface SettlementPlan {
  groupId: number
  /** The version these transfers were derived from; echoed back when recording a payment. */
  version: number
  rawDebtCount: number
  simplifiedTransferCount: number
  transfers: Transfer[]
}

export interface ApiErrorBody {
  status: number
  error: string
  message: string
  details?: string[]
  meta?: Record<string, unknown>
}
