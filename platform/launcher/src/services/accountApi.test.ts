import { invoke } from "@tauri-apps/api/core";
import { afterEach, describe, expect, it, vi } from "vitest";
import { AccountApi, AccountError, createTransport, validateEndpoint, validateDeepLink, type Registration } from "./accountApi";
vi.mock("@tauri-apps/api/core", () => ({ invoke: vi.fn() }));
const id = "12345678-1234-1234-1234-123456789abc";
const alphaKey = "k".repeat(43);
const registration: Registration = { registrationId: id, pollingToken: "p".repeat(43), telegramDeepLink: `https://t.me/recreating_bot?start=${"v".repeat(43)}`, expiresAt: "2099-01-01T00:00:00Z", verificationCode: "ABCDE-FGHJK" };
afterEach(() => { vi.unstubAllGlobals(); vi.clearAllMocks(); Reflect.deleteProperty(window, "__TAURI_INTERNALS__"); });
describe("account contract", () => {
  it("registers and polls in Windows with no frontend endpoint configured", async () => {
    Object.defineProperty(window, "__TAURI_INTERNALS__", { value: {}, configurable: true });
    const fetchMock = vi.fn(); vi.stubGlobal("fetch", fetchMock);
    const native = vi.mocked(invoke);
    native.mockResolvedValueOnce({ status: 201, body: registration })
      .mockResolvedValueOnce({ status: 200, body: { state: "PENDING", expiresAt: registration.expiresAt } });
    const api = new AccountApi(createTransport(""));
    const created = await api.register("Second_Player", "test-password-123", alphaKey);
    expect(created).toEqual(registration);
    await expect(api.status(created)).resolves.toMatchObject({ state: "PENDING" });
    expect(native).toHaveBeenNthCalledWith(1, "account_request", { request: {
      operation: "register", body: { username: "Second_Player", password: "test-password-123", alphaKey }
    } });
    expect(native).toHaveBeenNthCalledWith(2, "account_request", { request: {
      operation: "status", registrationId: id, pollingToken: registration.pollingToken
    } });
    expect(fetchMock).not.toHaveBeenCalled();
  });
  it("keeps native transport failures sanitized and never falls back to browser fetch", async () => {
    Object.defineProperty(window, "__TAURI_INTERNALS__", { value: {}, configurable: true });
    const fetchMock = vi.fn(); vi.stubGlobal("fetch", fetchMock);
    vi.mocked(invoke).mockRejectedValueOnce(new Error("password=private"));
    await expect(createTransport("")({ operation: "status", registrationId: id, pollingToken: registration.pollingToken }))
      .rejects.toMatchObject({ code: "NETWORK_ERROR" });
    expect(fetchMock).not.toHaveBeenCalled();
  });
  it("still rejects an unconfigured browser endpoint before sending credentials", async () => {
    const fetchMock = vi.fn(); vi.stubGlobal("fetch", fetchMock);
    await expect(createTransport("")({ operation: "login", body: { username: "name", password: "private" } }))
      .rejects.toMatchObject({ code: "NOT_CONFIGURED" });
    expect(fetchMock).not.toHaveBeenCalled();
  });
  it("restarts with polling credential, validates rotated response and never sends password/alpha key", async () => {
    const next = { ...registration, pollingToken: "q".repeat(43), verificationCode: "23456-789AB" };
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify(next), { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);
    const api = new AccountApi(createTransport("http://127.0.0.1:18080"));
    expect(await api.restart(registration)).toEqual(next);
    expect(fetchMock.mock.calls[0][0]).toBe(`/account-api/api/registrations/${id}/restart`);
    expect(fetchMock.mock.calls[0][1]).toMatchObject({ method: "POST", headers: { Authorization: `Bearer ${registration.pollingToken}` } });
    expect(fetchMock.mock.calls[0][1].body).toBeUndefined();
    await expect(new AccountApi(async () => ({ status: 200, body: { ...next, verificationCode: "secret" } })).restart(registration))
      .rejects.toMatchObject({ code: "INVALID_RESPONSE" });
  });
  it("posts username/password/alphaKey only for registration and polls with the distinct secret", async () => {
    const fetchMock = vi.fn().mockResolvedValueOnce(new Response(JSON.stringify(registration), { status: 201 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ state: "PENDING", expiresAt: registration.expiresAt }), { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);
    const api = new AccountApi(createTransport("http://127.0.0.1:18080"));
    const result = await api.register(" Engineer_1 ", "test-password-123", " " + alphaKey + " ");
    expect(fetchMock.mock.calls[0][0]).toBe("/account-api/api/registrations");
    expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual({ username: "Engineer_1", password: "test-password-123", alphaKey });
    await api.status(result);
    expect(fetchMock.mock.calls[1][0]).toBe(`/account-api/api/registrations/${id}`);
    expect(fetchMock.mock.calls[1][1].headers).toEqual({ Authorization: `Bearer ${registration.pollingToken}` });
    expect(fetchMock.mock.calls[1][1].body).toBeUndefined();
    expect(fetchMock.mock.calls[0][1]).toMatchObject({ cache: "no-store", credentials: "omit", redirect: "error" });
    expect(JSON.stringify(localStorage)).not.toContain("test-password-123");
    expect(JSON.stringify(localStorage)).not.toContain(alphaKey);
  });
  it("login calls backend and requires AUTHENTICATED with a platform UUID", async () => {
    const transport = vi.fn().mockResolvedValue({ status: 200, body: { status: "AUTHENTICATED", platformUserId: id, username: "engineer" } });
    expect((await new AccountApi(transport).login("engineer", "test-password-123")).platformUserId).toBe(id);
    expect(transport).toHaveBeenCalledWith({ operation: "login", body: { username: "engineer", password: "test-password-123" } }, undefined);
    transport.mockResolvedValue({ status: 200, body: { status: "PENDING", platformUserId: id } });
    await expect(new AccountApi(transport).login("engineer", "test-password-123")).rejects.toMatchObject({ code: "INVALID_RESPONSE" });
  });
  it.each([400, 401, 404, 409, 410, 429, 503, 500])("maps HTTP %s without exposing raw server messages", async status => {
    const api = new AccountApi(async () => ({ status, body: { message: "password=DO-NOT-EXPOSE" } }));
    try { await api.status(registration); throw new Error("Expected failure"); }
    catch (error) { expect(error).toBeInstanceOf(AccountError); expect((error as Error).message).not.toContain("DO-NOT-EXPOSE"); }
  });
  it("preserves safe backend error codes", async () => {
    const api = new AccountApi(async () => ({ status: 409, body: { code: "TELEGRAM_ALREADY_LINKED" } }));
    await expect(api.status(registration)).rejects.toMatchObject({ code: "TELEGRAM_ALREADY_LINKED" });
  });
  it("validates unicode password code points/UTF-8 bytes and username", async () => {
    const transport = vi.fn().mockResolvedValue({ status: 201, body: registration });
    const api = new AccountApi(transport);
    await expect(api.register("1name", "test-password-123", alphaKey)).rejects.toMatchObject({ code: "INVALID_USERNAME" });
    await expect(api.register("name", "😀".repeat(11), alphaKey)).rejects.toMatchObject({ code: "INVALID_PASSWORD" });
    await expect(api.register("name", "😀".repeat(19), alphaKey)).rejects.toMatchObject({ code: "INVALID_PASSWORD" });
    expect(transport).not.toHaveBeenCalled();
    await api.register("name", "😀".repeat(12), alphaKey);
    expect(transport).toHaveBeenCalledOnce();
  });
  it("rejects malformed registration/status and unsafe deep links", async () => {
    const transport = vi.fn().mockResolvedValue({ status: 201, body: { ...registration, telegramDeepLink: "javascript:alert(1)" } });
    const api = new AccountApi(transport);
    await expect(api.register("name", "test-password-123", alphaKey)).rejects.toMatchObject({ code: "INVALID_RESPONSE" });
    transport.mockResolvedValue({ status: 200, body: { state: "COMPLETED", expiresAt: registration.expiresAt } });
    await expect(api.status(registration)).rejects.toMatchObject({ code: "INVALID_RESPONSE" });
  });
  it("sanitizes connection errors and honors cancellation", async () => {
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new Error("password=secret URL=token")));
    const api = new AccountApi(createTransport("https://api.example.test"));
    await expect(api.login("name", "test-password-123")).rejects.toMatchObject({ code: "NETWORK_ERROR" });
    const controller = new AbortController(); controller.abort();
    await expect(api.status(registration, controller.signal)).rejects.toMatchObject({ name: "AbortError" });
  });
  it.each(["", "http://186.246.28.214:8080", "https://user:password@example.test", "https://example.test/api", "https://example.test?secret=x"])("fails closed for endpoint %s", value => {
    expect(() => validateEndpoint(value)).toThrow(AccountError);
  });
  it("allows HTTPS and loopback dev only, validates Telegram URL", () => {
    expect(validateEndpoint("https://api.example.test/")).toBe("https://api.example.test");
    expect(validateEndpoint("http://127.0.0.1:18080")).toBe("http://127.0.0.1:18080");
    expect(validateDeepLink(registration.telegramDeepLink)).toBe(true);
    expect(validateDeepLink(registration.telegramDeepLink + "&other=x")).toBe(false);
    expect(validateDeepLink(registration.telegramDeepLink.replace("t.me", "evil.test"))).toBe(false);
  });
  it("rejects missing/malformed alpha keys without requests and maps a generic server rejection", async () => {
    const transport = vi.fn().mockResolvedValue({ status: 400, body: { code: "INVALID_ALPHA_KEY", detail: "used by another user" } });
    const api = new AccountApi(transport);
    for (const key of ["", "wrong", "k".repeat(44)]) {
      await expect(api.register("engineer", "test-password-123", key)).rejects.toMatchObject({ code: "INVALID_ALPHA_KEY" });
    }
    expect(transport).not.toHaveBeenCalled();
    await expect(api.register("engineer", "test-password-123", alphaKey)).rejects.toMatchObject({
      code: "INVALID_ALPHA_KEY", status: 400, message: "Альфа-ключ недействителен или недоступен. Проверьте ключ или обратитесь к организатору альфы."
    });
  });
});
