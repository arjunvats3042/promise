import axios from 'axios'

/* ==========================================================================
   Backend HTTP Client Configuration
   Proxies to the Python Django backend (/api/v1)
   ========================================================================== */

const API_BASE = import.meta.env.VITE_API_BASE_URL || '/api/v1'

export const client = axios.create({
  baseURL: API_BASE,
  headers: {
    'Content-Type': 'application/json',
  },
})

// Attach JWT access token to requests
client.interceptors.request.use((config) => {
  const token = localStorage.getItem('access_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// Handle 401 Unauthorized — clear session and redirect to /login
client.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('access_token')
      localStorage.removeItem('refresh_token')
      if (
        !window.location.pathname.startsWith('/login') &&
        !window.location.pathname.startsWith('/signup') &&
        window.location.pathname !== '/'
      ) {
        window.location.href = '/login'
      }
    }
    return Promise.reject(error)
  }
)

/* ==========================================================================
   Type Definitions Matching Backend Django Models
   ========================================================================== */

export interface AuthTokens {
  access_token: string
  refresh_token: string
  token_type?: string
  expires_in?: number
}

export interface UserProfile {
  id: string
  email: string
  name: string
  avatar_url?: string
  timezone: string
  email_verified: boolean
  has_password?: boolean
  google_linked?: boolean
  created_at?: string
}

export interface AuthResponse {
  tokens: AuthTokens
  user: UserProfile
}

export interface AuthSession {
  id: string
  device_name: string
  ip_address: string
  last_used_at: string
  is_current: boolean
  created_at: string
}

export type DuePrecision = 'NONE' | 'DATE' | 'DATETIME'
export type CommitmentStatus = 'PENDING' | 'WAITING' | 'SNOOZED' | 'COMPLETED' | 'CANCELLED'

export interface Commitment {
  id: string
  title: string
  description: string
  status: CommitmentStatus
  due_at: string | null
  due_precision: DuePrecision
  source: string
  snoozed_until: string | null
  completed_at: string | null
  cancelled_at: string | null
  created_at: string
  updated_at: string
  is_overdue: boolean
}

export interface CommitmentsResponse {
  count: number
  next: string | null
  previous: string | null
  results: Commitment[]
}

export interface Goal {
  id: string
  title: string
  description: string
  status: 'ACTIVE' | 'PAUSED' | 'COMPLETED' | 'CANCELLED'
  timezone: string
  start_date: string
  end_date: string | null
  recurrence_kind: 'DAILY' | 'WEEKLY_DAYS' | 'N_PER_PERIOD'
  weekdays: number[]
  period_unit: string | null
  times_per_period: number | null
  tracking_kind: 'BINARY' | 'COUNT'
  target_value: number | null
  target_unit: string | null
  source: string
  is_shared: boolean
  paused_at: string | null
  created_at: string
  updated_at: string
  is_ended: boolean
  progress: number | null
  current_streak: number
  unread_chat_count: number
  latest_chat_message: string | null
}

export interface GoalsResponse {
  count: number
  next: string | null
  previous: string | null
  results: Goal[]
}

export interface GoalParticipant {
  id: string
  user_id: string
  email: string
  name: string
  role: 'OWNER' | 'ADMIN' | 'MEMBER'
  status: 'PENDING' | 'ACCEPTED' | 'DECLINED'
  joined_at: string
}

export interface GoalInvitePreview {
  goal_id: string
  goal_title: string
  inviter_name: string
  inviter_email: string
  created_at: string
}

export interface GoalCheckInItem {
  id: string
  period_date: string
  status: 'COMPLETED' | 'SKIPPED'
  value: number | null
  notes: string | null
  created_at: string
}

export interface DailyMotivation {
  id?: string
  quote: string
  author?: string
  date?: string
}

export interface WeeklyInsights {
  period_label: string
  commitments_created: number
  commitments_completed: number
  commitments_overdue: number
  goals_checked_in: number
  longest_streak: number
  ai_suggestions: string[]
  motivation_line: string
}

export interface RefinedCommitment {
  status: 'READY' | 'NEEDS_CLARIFICATION'
  current_interpretation?: string
  missing_information?: string | null
  clarifying_question?: string | null
  refined_title?: string
  refined_description?: string
  suggested_due_at?: string | null
  suggested_due_precision?: 'NONE' | 'MINUTE' | 'HOUR' | 'DAY' | null
  reasoning?: string
}

export interface ParsedThoughtItem {
  type: 'commitment' | 'goal'
  title: string
  description?: string
  due_at?: string | null
  due_precision?: 'NONE' | 'MINUTE' | 'HOUR' | 'DAY' | null
}

export interface SupportBotResponse {
  answer: string
  suggested_followups: string[]
  is_off_topic: boolean
}

export interface GoalSuggestion {
  title: string
  description?: string
  recurrence_kind: 'DAILY' | 'WEEKLY_DAYS' | 'N_PER_PERIOD'
  tracking_kind: 'BINARY' | 'COUNT'
  target_value?: number | null
  target_unit?: string | null
  reasoning?: string
  status?: 'READY' | 'NEEDS_CLARIFICATION'
  clarification_question?: string | null
  weekdays?: number[]
}

/* ==========================================================================
   Backend Service Helpers
   Directly communicates with Django Backend endpoints
   ========================================================================== */

export const backend = {
  // Authentication & Users (/api/v1/auth/)
  auth: {
    async login(email: string, password: string): Promise<AuthResponse> {
      const { data } = await client.post<AuthResponse>('/auth/login/', {
        email,
        password,
        device_name: 'Web Browser',
      })
      if (data.tokens?.access_token) {
        localStorage.setItem('access_token', data.tokens.access_token)
        localStorage.setItem('refresh_token', data.tokens.refresh_token)
      }
      return data
    },

    async register(payload: { email: string; password: string; name: string }): Promise<AuthResponse> {
      const { data } = await client.post<AuthResponse>('/auth/register/', payload)
      if (data.tokens?.access_token) {
        localStorage.setItem('access_token', data.tokens.access_token)
        localStorage.setItem('refresh_token', data.tokens.refresh_token)
      }
      return data
    },

    async google(idToken: string): Promise<AuthResponse> {
      const { data } = await client.post<AuthResponse>('/auth/google/', {
        id_token: idToken,
        platform: 'web',
        device_name: navigator.userAgent.slice(0, 128),
      })
      if (data.tokens?.access_token) {
        localStorage.setItem('access_token', data.tokens.access_token)
        localStorage.setItem('refresh_token', data.tokens.refresh_token)
      }
      return data
    },

    async me(): Promise<UserProfile> {
      const { data } = await client.get<any>('/auth/me/')
      return data?.user || data
    },

    async logout(): Promise<void> {
      const refreshToken = localStorage.getItem('refresh_token')
      try {
        if (refreshToken) {
          await client.post('/auth/logout/', { refresh_token: refreshToken })
        }
      } catch {
        // Ignore network failure on logout
      } finally {
        localStorage.removeItem('access_token')
        localStorage.removeItem('refresh_token')
      }
    },

    async sessions(): Promise<AuthSession[]> {
      const { data } = await client.get<AuthSession[]>('/auth/sessions/')
      return Array.isArray(data) ? data : (data as any).results || []
    },

    async revokeSession(sessionId: string): Promise<void> {
      await client.post(`/auth/sessions/${sessionId}/revoke/`)
    },

    async deleteAccount(): Promise<void> {
      try {
        await client.delete('/auth/me/')
      } finally {
        localStorage.removeItem('access_token')
        localStorage.removeItem('refresh_token')
      }
    },
  },

  // Commitments (/api/v1/commitments/)
  commitments: {
    async list(params?: { status?: string; is_overdue?: boolean; page?: number }): Promise<CommitmentsResponse> {
      const { data } = await client.get<CommitmentsResponse>('/commitments/', { params })
      return data
    },

    async create(payload: { title: string; description?: string; due_at?: string | null; due_precision?: DuePrecision }): Promise<Commitment> {
      const { data } = await client.post<Commitment>(`/commitments/`, {
        ...payload,
        due_precision: payload.due_precision || 'NONE',
      })
      return data
    },

    async complete(id: string): Promise<Commitment> {
      const { data } = await client.post<Commitment>(`/commitments/${id}/complete/`)
      return data
    },

    async cancel(id: string): Promise<Commitment> {
      const { data } = await client.post<Commitment>(`/commitments/${id}/cancel/`)
      return data
    },

    async snooze(id: string, snoozedUntilIso: string): Promise<Commitment> {
      const { data } = await client.post<Commitment>(`/commitments/${id}/snooze/`, {
        snoozed_until: snoozedUntilIso,
      })
      return data
    },

    async unsnooze(id: string): Promise<Commitment> {
      const { data } = await client.post<Commitment>(`/commitments/${id}/unsnooze/`)
      return data
    },

    async wait(id: string): Promise<Commitment> {
      const { data } = await client.post<Commitment>(`/commitments/${id}/wait/`)
      return data
    },
  },

  // Goals / Daily Practices (/api/v1/goals/)
  goals: {
    async list(params?: { status?: string; page?: number }): Promise<GoalsResponse> {
      const { data } = await client.get<GoalsResponse>('/goals/', { params })
      return data
    },

    async create(payload: {
      title: string
      description?: string
      recurrence_kind: 'DAILY' | 'WEEKLY_DAYS' | 'N_PER_PERIOD'
      tracking_kind: 'BINARY' | 'COUNT'
      weekdays?: number[]
      start_date?: string
      target_value?: number | null
      target_unit?: string | null
    }): Promise<Goal> {
      const { data } = await client.post<Goal>('/goals/', payload)
      return data
    },

    async pause(id: string): Promise<Goal> {
      const { data } = await client.post<Goal>(`/goals/${id}/pause/`)
      return data
    },

    async resume(id: string): Promise<Goal> {
      const { data } = await client.post<Goal>(`/goals/${id}/resume/`)
      return data
    },

    async checkIn(id: string, payload: { status: 'COMPLETED' | 'SKIPPED'; value?: number; notes?: string; period_date?: string }) {
      const { data } = await client.post(`/goals/${id}/check-ins/`, payload)
      return data
    },

    async convertToShared(id: string): Promise<Goal> {
      const { data } = await client.post<Goal>(`/goals/${id}/convert-to-shared/`)
      return data
    },

    async chatMessages(id: string, limit = 50): Promise<Array<{
      id: string
      goal_id: string
      sender_user_id: string
      sender_name: string
      sender_avatar_url?: string | null
      body: string
      created_at: string
      is_mine?: boolean
    }>> {
      const { data } = await client.get(`/goals/${id}/chat/messages/`, { params: { limit } })
      return Array.isArray(data) ? data : (data as any).results || []
    },

    async sendChatMessage(id: string, body: string) {
      const { data } = await client.post(`/goals/${id}/chat/messages/`, { body })
      return data
    },

    async weeklyReflection(id: string) {
      const { data } = await client.get(`/goals/${id}/weekly-reflection/`)
      return data
    },

    async chatSummary(id: string) {
      const { data } = await client.get(`/goals/${id}/chat/summary/`)
      return data
    },

    async get(id: string): Promise<Goal> {
      const { data } = await client.get<Goal>(`/goals/${id}/`)
      return data
    },

    async complete(id: string): Promise<Goal> {
      const { data } = await client.post<Goal>(`/goals/${id}/complete/`)
      return data
    },

    async cancel(id: string): Promise<Goal> {
      const { data } = await client.post<Goal>(`/goals/${id}/cancel/`)
      return data
    },

    async listParticipants(id: string): Promise<GoalParticipant[]> {
      const { data } = await client.get(`/goals/${id}/participants/`)
      return Array.isArray(data) ? data : (data as any).results || []
    },

    async inviteParticipant(id: string, email: string): Promise<GoalParticipant> {
      const { data } = await client.post<GoalParticipant>(`/goals/${id}/participants/`, { email })
      return data
    },

    async removeParticipant(id: string, userId: string): Promise<void> {
      await client.delete(`/goals/${id}/participants/${userId}/`)
    },

    async listInvites(): Promise<GoalInvitePreview[]> {
      try {
        const { data } = await client.get('/goals/invites/')
        return Array.isArray(data) ? data : (data as any).results || []
      } catch {
        return []
      }
    },

    async acceptInvite(goalId: string): Promise<Goal> {
      const { data } = await client.post<Goal>(`/goals/${goalId}/participants/accept/`)
      return data
    },

    async declineInvite(goalId: string): Promise<void> {
      await client.post(`/goals/${goalId}/participants/decline/`)
    },

    async listCheckIns(id: string): Promise<GoalCheckInItem[]> {
      try {
        const { data } = await client.get(`/goals/${id}/check-ins/`)
        return Array.isArray(data) ? data : (data as any).results || []
      } catch {
        return []
      }
    },

    async leave(id: string): Promise<void> {
      await client.post(`/goals/${id}/leave/`)
    },
  },

  // AI Services (/api/v1/ai/ and /api/v1/motivation/)
  ai: {
    async motivation(): Promise<DailyMotivation> {
      const { data } = await client.get<DailyMotivation>('/motivation/today/')
      return data
    },

    async insights(): Promise<WeeklyInsights> {
      const { data } = await client.get<WeeklyInsights>('/ai/insights/weekly/')
      return data
    },

    async refine(prompt: string, timezone?: string): Promise<RefinedCommitment> {
      const tz = timezone || Intl.DateTimeFormat().resolvedOptions().timeZone || 'Asia/Kolkata'
      const { data } = await client.post<RefinedCommitment>('/ai/commitments/refine/', {
        prompt,
        timezone: tz,
      })
      return data
    },

    async parseThought(thought: string, timezone?: string): Promise<{ items: ParsedThoughtItem[] }> {
      const tz = timezone || Intl.DateTimeFormat().resolvedOptions().timeZone || 'Asia/Kolkata'
      const { data } = await client.post<{ items: ParsedThoughtItem[] }>('/ai/parse-thought/', {
        thought,
        timezone: tz,
      })
      return data
    },

    async support(question: string, history: Array<{ role: 'user' | 'assistant'; content: string }> = []): Promise<SupportBotResponse> {
      const { data } = await client.post<SupportBotResponse>('/ai/support/ask/', {
        question,
        conversation_history: history,
      })
      return data
    },

    async suggestGoal(prompt: string, timezone?: string): Promise<GoalSuggestion> {
      const tz = timezone || Intl.DateTimeFormat().resolvedOptions().timeZone || 'Asia/Kolkata'
      const { data } = await client.post<any>('/ai/goals/suggest/', {
        prompt,
        timezone: tz,
      })
      if (data && data.goal) {
        return {
          title: data.goal.title || prompt,
          description: data.goal.description || '',
          recurrence_kind: data.goal.recurrence_type || data.goal.recurrence_kind || 'DAILY',
          tracking_kind: data.goal.tracking_type || data.goal.tracking_kind || 'BINARY',
          target_value: data.goal.target_value,
          target_unit: data.goal.target_unit,
          status: data.status || 'READY',
          clarification_question: data.clarification_question,
          weekdays: data.goal.recurrence_days || data.goal.weekdays,
          reasoning: data.reasoning,
        }
      }
      return data
    },
  },

  // Global Search (/api/v1/search/)
  search: {
    async query(q: string): Promise<{ commitments: Commitment[]; goals: Goal[] }> {
      try {
        const { data } = await client.get('/search/', { params: { q } })
        return data
      } catch {
        const [cmtsRes, goalsRes] = await Promise.allSettled([
          backend.commitments.list({ page: 1 }),
          backend.goals.list({ page: 1 }),
        ])
        const lower = q.trim().toLowerCase()
        const commitments =
          cmtsRes.status === 'fulfilled'
            ? cmtsRes.value.results.filter(
                (c) =>
                  c.title.toLowerCase().includes(lower) ||
                  (c.description && c.description.toLowerCase().includes(lower))
              )
            : []
        const goals =
          goalsRes.status === 'fulfilled'
            ? goalsRes.value.results.filter(
                (g) =>
                  g.title.toLowerCase().includes(lower) ||
                  (g.description && g.description.toLowerCase().includes(lower))
              )
            : []
        return { commitments, goals }
      }
    },
  },
}

export default backend
