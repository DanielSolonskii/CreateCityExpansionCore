import { invoke } from "@tauri-apps/api/core";

export interface Registration {
  registrationId: string;
  pollingToken: string;
  telegramDeepLink: string;
  expiresAt: string;
  verificationCode: string;
}
export interface RegistrationStatus {
  state: "PENDING" | "COMPLETED" | "EXPIRED";
  expiresAt: string;
  platformUserId?: string | null;
}
export interface Account { platformUserId: string; username: string; status: "AUTHENTICATED" }
export class AccountError extends Error {
  constructor(public readonly code: string, public readonly status = 0) {
    super(messages[code] ?? messages.BACKEND_ERROR);
    this.name = "AccountError";
  }
}
const messages: Record<string, string> = {
  NATIVE_REQUIRED: "Доверенный вход доступен в Windows Launcher, а не в браузере.",
  INVALID_DEVICE: "Доверие устройству отозвано или истекло. Войдите заново и подтвердите устройство.",
  DEVICE_EXPIRED: "Запрос устройства истёк. Выполните вход заново, чтобы получить новый QR.",
  DEVICE_LIMIT: "Слишком много устройств. Отзовите ненужные в Telegram: Аккаунт → Устройства.",
  NOT_CONFIGURED: "В этой сборке лаунчера не настроено подключение к серверу. Обновите лаунчер.",
  INVALID_ENDPOINT: "Нужен HTTPS адрес Backend; HTTP разрешён только для localhost.",
  NETWORK_ERROR: "Backend недоступен. Проверьте подключение и повторите попытку.",
  INVALID_RESPONSE: "Backend вернул неожиданный ответ. Повторите попытку позже.",
  INVALID_USERNAME: "Логин: 3–32 символа, первая — латинская буква, далее буквы, цифры или _.",
  INVALID_PASSWORD: "Пароль: минимум 12 символов и не более 72 байт UTF-8.",
  INVALID_ALPHA_KEY: "Альфа-ключ недействителен или недоступен. Проверьте ключ или обратитесь к организатору альфы.",
  INVALID_LOGIN: "Неверный логин/пароль или аккаунт ещё не подтверждён в Telegram.",
  USERNAME_UNAVAILABLE: "Этот логин уже занят.",
  TELEGRAM_ALREADY_LINKED: "Этот Telegram уже связан с другим аккаунтом.",
  IDENTITY_CONFLICT: "Логин или Telegram уже связан с другим аккаунтом.",
  INVALID_VERIFICATION: "Подтверждение недействительно или уже использовано.",
  REGISTRATION_NOT_FOUND: "Регистрация не найдена или ключ проверки недействителен.",
  REGISTRATION_EXPIRED: "Срок подтверждения истёк. Получите новый QR/код в этом окне. Альфа-ключ повторно не нужен.",
  REGISTRATION_UNAVAILABLE: "Эту регистрацию больше нельзя подтвердить.",
  VERIFICATION_NOT_CONFIGURED: "Подтверждение Telegram временно недоступно на сервере.",
  RATE_LIMITED: "Слишком много запросов. Подождите и повторите попытку.",
  BACKEND_ERROR: "Ошибка Backend. Повторите попытку позже.",
};
export const errorMessage = (error: unknown) => error instanceof AccountError ? error.message : messages.NETWORK_ERROR;
export const uuid = (value: unknown): value is string => typeof value === "string" && /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value);
const date = (value: unknown): value is string => typeof value === "string" && Number.isFinite(Date.parse(value));
export function validateEndpoint(value: string): string {
  if (!value) throw new AccountError("NOT_CONFIGURED");
  try {
    const url = new URL(value);
    const local = ["localhost", "127.0.0.1", "[::1]"].includes(url.hostname);
    if ((url.protocol !== "https:" && !(local && url.protocol === "http:")) ||
        url.username || url.password || url.search || url.hash || url.pathname !== "/") throw new Error();
    return url.origin;
  } catch { throw new AccountError("INVALID_ENDPOINT"); }
}
export function validateDeepLink(value: unknown): value is string {
  if (typeof value !== "string") return false;
  try {
    const url = new URL(value);
    const entries = [...url.searchParams.entries()];
    return url.protocol === "https:" && url.hostname === "t.me" && !url.port && !url.username && !url.password && !url.hash &&
      /^\/[A-Za-z0-9_]{5,32}$/.test(url.pathname) && entries.length === 1 && entries[0][0] === "start" &&
      /^[A-Za-z0-9_-]{32,64}$/.test(entries[0][1]);
  } catch { return false; }
}
type Operation = "register" | "status" | "login" | "restart";
export interface WireRequest { operation: Operation; registrationId?: string; pollingToken?: string; body?: { username: string; password: string; alphaKey?: string } }
export interface WireResponse { status: number; body: unknown }
export type Transport = (request: WireRequest, signal?: AbortSignal) => Promise<WireResponse>;
export function createTransport(base: string): Transport {
  return async (request, signal) => {
    // Windows uses its own release-pinned HTTPS endpoint; Vite env is only for browser transport.
    const native = "__TAURI_INTERNALS__" in window;
    const origin = native ? "" : validateEndpoint(base);
    signal?.throwIfAborted();
    try {
      if (native) {
        const response = await invoke<WireResponse>("account_request", { request });
        signal?.throwIfAborted();
        return response;
      }
      const prefix = import.meta.env.DEV ? "/account-api" : origin;
      const path = request.operation === "restart" ? `/api/registrations/${request.registrationId}/restart` :
        request.operation === "status" ? `/api/registrations/${request.registrationId}` :
        request.operation === "register" ? "/api/registrations" : "/api/auth/login";
      const response = await fetch(prefix + path, {
        method: request.operation === "status" ? "GET" : "POST",
        headers: request.operation === "status" || request.operation === "restart" ? { Authorization: `Bearer ${request.pollingToken}` } : { "Content-Type": "application/json" },
        body: request.body ? JSON.stringify(request.body) : undefined,
        cache: "no-store", credentials: "omit", redirect: "error",
        signal: signal ? AbortSignal.any([signal, AbortSignal.timeout(15000)]) : AbortSignal.timeout(15000),
      });
      let body: unknown;
      try { body = await response.json(); } catch { body = null; }
      return { status: response.status, body };
    } catch {
      signal?.throwIfAborted();
      throw new AccountError("NETWORK_ERROR");
    }
  };
}
export class AccountApi {
  constructor(private readonly transport: Transport) {}
  private async request(request: WireRequest, signal?: AbortSignal): Promise<Record<string, unknown>> {
    const response = await this.transport(request, signal);
    const data = response.body && typeof response.body === "object" ? response.body as Record<string, unknown> : {};
    if (response.status < 200 || response.status >= 300) {
      const fallback: Record<number, string> = { 400: "INVALID_VERIFICATION", 401: "INVALID_LOGIN", 404: "REGISTRATION_NOT_FOUND", 409: "USERNAME_UNAVAILABLE", 410: "REGISTRATION_EXPIRED", 429: "RATE_LIMITED", 503: "VERIFICATION_NOT_CONFIGURED" };
      throw new AccountError(typeof data.code === "string" && data.code in messages ? data.code : fallback[response.status] ?? "BACKEND_ERROR", response.status);
    }
    return data;
  }
  async register(username: string, password: string, alphaKey: string, signal?: AbortSignal): Promise<Registration> {
    if (!/^[A-Za-z][A-Za-z0-9_]{2,31}$/.test(username.trim())) throw new AccountError("INVALID_USERNAME");
    if ([...password].length < 12 || new TextEncoder().encode(password).length > 72) throw new AccountError("INVALID_PASSWORD");
    const key = alphaKey.trim();
    if (!/^[A-Za-z0-9_-]{43}$/.test(key)) throw new AccountError("INVALID_ALPHA_KEY");
    const data = await this.request({ operation: "register", body: { username: username.trim(), password, alphaKey: key } }, signal);
    if (!uuid(data.registrationId) || typeof data.pollingToken !== "string" || !/^[A-Za-z0-9_-]{32,64}$/.test(data.pollingToken) ||
        !validateDeepLink(data.telegramDeepLink) || !date(data.expiresAt) ||
        typeof data.verificationCode !== "string" || !/^[23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{5}-[23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{5}$/.test(data.verificationCode)) throw new AccountError("INVALID_RESPONSE");
    return data as unknown as Registration;
  }
  async status(registration: Registration, signal?: AbortSignal): Promise<RegistrationStatus> {
    const data = await this.request({ operation: "status", registrationId: registration.registrationId, pollingToken: registration.pollingToken }, signal);
    if (!["PENDING", "COMPLETED", "EXPIRED"].includes(String(data.state)) || !date(data.expiresAt) ||
        (data.state === "COMPLETED" && !uuid(data.platformUserId))) throw new AccountError("INVALID_RESPONSE");
    return data as unknown as RegistrationStatus;
  }
  async restart(registration: Registration, signal?: AbortSignal): Promise<Registration> {
    const data = await this.request({ operation: "restart", registrationId: registration.registrationId, pollingToken: registration.pollingToken }, signal);
    if (data.registrationId !== registration.registrationId || !uuid(data.registrationId) ||
        typeof data.pollingToken !== "string" || !/^[A-Za-z0-9_-]{43}$/.test(data.pollingToken) ||
        !validateDeepLink(data.telegramDeepLink) || !date(data.expiresAt) ||
        typeof data.verificationCode !== "string" || !/^[23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{5}-[23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{5}$/.test(data.verificationCode)) throw new AccountError("INVALID_RESPONSE");
    return data as unknown as Registration;
  }
  async login(username: string, password: string, signal?: AbortSignal): Promise<Account> {
    const data = await this.request({ operation: "login", body: { username: username.trim(), password } }, signal);
    if (data.status !== "AUTHENTICATED" || !uuid(data.platformUserId) || typeof data.username !== "string" ||
        !/^[A-Za-z][A-Za-z0-9_]{2,31}$/.test(data.username)) throw new AccountError("INVALID_RESPONSE");
    return data as unknown as Account;
  }
}
export const accountApi = new AccountApi(createTransport(import.meta.env.VITE_ACCOUNT_API_BASE_URL ?? ""));
export async function openTelegram(link: string) {
  if (!validateDeepLink(link)) throw new AccountError("INVALID_RESPONSE");
  if ("__TAURI_INTERNALS__" in window) await invoke("open_telegram", { link });
  else if (!window.open(link, "_blank", "noopener,noreferrer")) {
    // The visible deep-link remains available even when a browser blocks popups.
  }
}
