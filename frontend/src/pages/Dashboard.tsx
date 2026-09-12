import { useState } from 'react'
import { AppShell } from '../app/AppShell'
import { GroupDetail } from '../app/GroupDetail'
import { GroupList } from '../app/GroupList'
import type { AuthResponse } from '../types'

export function Dashboard({
  user,
  onSignOut,
}: {
  user: AuthResponse
  onSignOut: () => void
}) {
  const [openGroupId, setOpenGroupId] = useState<number | null>(null)

  return (
    <AppShell user={user} onSignOut={onSignOut}>
      {openGroupId === null ? (
        <GroupList onOpen={setOpenGroupId} />
      ) : (
        <GroupDetail
          groupId={openGroupId}
          currentUserId={user.userId}
          onBack={() => setOpenGroupId(null)}
        />
      )}
    </AppShell>
  )
}
