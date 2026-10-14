import { type Account } from "./accountApi";
import { deviceGateway } from "./deviceGateway";
export interface ProfileSettings { displayName: string; telegram: string; city: string }
const PROFILE_KEY = "re-created.profile-settings";
const USERNAME_KEY = "create-server-launcher.remembered-username";
export class AuthGateway {
  private account: Account | null = null;
  constructor() {
    // Remove legacy mock credentials and fake sessions, never read their contents.
    try {
      for (const storage of [localStorage, sessionStorage]) {
        for (const key of ["re-created.mock-user", "create-server-launcher.mock-session"]) storage.removeItem(key);
      }
    } catch { /* Storage may be unavailable; no fallback credential store. */ }
  }
  getSessionUsername() { return this.account?.username ?? null; }
  getPlatformUserId() { return this.account?.platformUserId ?? null; }
  getRememberedUsername() { try { return localStorage.getItem(USERNAME_KEY) ?? ""; } catch { return ""; } }
  async login({ username, password, remember }: { username: string; password: string; remember: boolean }, signal?: AbortSignal) {
    const enrollment = await deviceGateway.begin(username, password);
    if (signal?.aborted) {
      await deviceGateway.cancel();
      signal.throwIfAborted();
    }
    try {
      if (remember) localStorage.setItem(USERNAME_KEY, username.trim());
      else localStorage.removeItem(USERNAME_KEY);
    } catch { /* Login remains valid for this process if username cannot be remembered. */ }
    return enrollment;
  }
  async finishDevice(signal?: AbortSignal) {
    const account = await deviceGateway.poll();
    signal?.throwIfAborted();
    if (account) this.account = account;
    return account;
  }
  async restore() {
    if (!("__TAURI_INTERNALS__" in window)) return null;
    const account = await deviceGateway.restore();
    this.account = account;
    return account;
  }
  async logout() {
    this.account = null;
    if ("__TAURI_INTERNALS__" in window) await deviceGateway.logout();
  }
  getProfileSettings(username: string): ProfileSettings {
    return this.settings()[username.toLowerCase()] ?? { displayName: username, telegram: "", city: "Не выбран" };
  }
  async updateProfileSettings(username: string, settings: ProfileSettings) {
    const normalized = { displayName: settings.displayName.trim(), telegram: settings.telegram.trim(), city: settings.city.trim() };
    const all = this.settings();
    all[username.toLowerCase()] = normalized;
    localStorage.setItem(PROFILE_KEY, JSON.stringify(all));
    return normalized; // Local display preferences, not platform identity mutations.
  }
  private settings(): Record<string, ProfileSettings> {
    try { return JSON.parse(localStorage.getItem(PROFILE_KEY) ?? "{}"); } catch { return {}; }
  }
}
export const authGateway = new AuthGateway();
