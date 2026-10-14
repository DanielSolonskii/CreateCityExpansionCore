import { invoke } from "@tauri-apps/api/core";
import { AccountError, type Account, uuid, validateDeepLink } from "./accountApi";

export interface DeviceEnrollment { telegramDeepLink: string; expiresAt: string }
type Operation = "begin" | "poll" | "restore" | "logout" | "cancel";
type Reply = { status: number; body: unknown };
function account(value: unknown): Account {
  const data = value as Partial<Account> | null;
  if (!data || data.status !== "AUTHENTICATED" || !uuid(data.platformUserId) ||
      typeof data.username !== "string" || !/^[a-z][a-z0-9_]{2,31}$/.test(data.username)) throw new AccountError("INVALID_RESPONSE");
  return data as Account;
}
async function request(operation: Operation, username?: string, password?: string): Promise<unknown> {
  if (!("__TAURI_INTERNALS__" in window)) throw new AccountError("NATIVE_REQUIRED");
  let reply: Reply;
  try { reply = await invoke<Reply>("device_request", { operation, username, password }); }
  catch { throw new AccountError("NETWORK_ERROR"); }
  if (reply.status < 200 || reply.status >= 300) {
    const body = reply.body as { code?: string } | null;
    const safe = ["INVALID_LOGIN", "INVALID_DEVICE", "DEVICE_EXPIRED", "DEVICE_LIMIT", "RATE_LIMITED", "VERIFICATION_NOT_CONFIGURED"];
    throw new AccountError(body?.code && safe.includes(body.code) ? body.code : "BACKEND_ERROR", reply.status);
  }
  return reply.body;
}
export const deviceGateway = {
  async begin(username: string, password: string): Promise<DeviceEnrollment> {
    const data = await request("begin", username.trim(), password) as DeviceEnrollment;
    if (!data || !validateDeepLink(data.telegramDeepLink) || !/^d_[A-Za-z0-9_-]{43}$/.test(new URL(data.telegramDeepLink).searchParams.get("start") ?? "") ||
        !Number.isFinite(Date.parse(data.expiresAt))) throw new AccountError("INVALID_RESPONSE");
    return { telegramDeepLink: data.telegramDeepLink, expiresAt: data.expiresAt };
  },
  async poll(): Promise<Account | null> {
    const body = await request("poll");
    if ((body as {state?:string})?.state === "PENDING") return null;
    if ((body as {state?:string})?.state === "REVOKED") throw new AccountError("INVALID_DEVICE");
    return account(body);
  },
  async restore(): Promise<Account | null> {
    const body = await request("restore");
    return body === null ? null : account(body);
  },
  async logout(): Promise<void> { await request("logout"); },
  async cancel(): Promise<void> { await request("cancel"); },
};
