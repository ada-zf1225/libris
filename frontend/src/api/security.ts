import { startAuthentication, startRegistration, browserSupportsWebAuthn } from '@simplewebauthn/browser'
import { http } from './http'

// ---------- email ----------

export const requestEmailVerification = () => http.post('/api/my/security/email/request-verification')

// ---------- TOTP ----------

export interface TotpSetup {
  secret: string
  otpauthUrl: string
}

export const totpSetup = () => http.post<TotpSetup>('/api/my/security/totp/setup').then((r) => r.data)

export const totpConfirm = (code: string) =>
  http.post<{ recoveryCodes: string[] }>('/api/my/security/totp/confirm', { code }).then((r) => r.data)

export const totpDisable = (password: string, code: string) =>
  http.post('/api/my/security/totp/disable', { password, code })

export const regenerateRecoveryCodes = (code: string) =>
  http.post<{ recoveryCodes: string[] }>('/api/my/security/totp/recovery-codes', { code }).then((r) => r.data)

// ---------- passkeys ----------

export interface PasskeyView {
  credentialId: string
  label: string
  createdAt: string
  lastUsedAt: string | null
}

export const passkeys = {
  supported: () => browserSupportsWebAuthn(),
  list: () => http.get<PasskeyView[]>('/api/my/security/passkeys').then((r) => r.data),
  rename: (credentialId: string, label: string) =>
    http.put(`/api/my/security/passkeys/${encodeURIComponent(credentialId)}`, { label }),
  remove: (credentialId: string) =>
    http.delete(`/api/my/security/passkeys/${encodeURIComponent(credentialId)}`),

  /** Runs the WebAuthn registration ceremony against Spring Security's endpoints. */
  async register(label: string): Promise<void> {
    const { data: options } = await http.post('/webauthn/register/options')
    const credential = await startRegistration({ optionsJSON: options })
    await http.post('/webauthn/register', { publicKey: { credential, label } })
  },

  /** Passkey sign-in; establishes the session on success. */
  async authenticate(): Promise<void> {
    const { data: options } = await http.post('/webauthn/authenticate/options')
    const credential = await startAuthentication({ optionsJSON: options })
    await http.post('/login/webauthn', credential)
  },
}
