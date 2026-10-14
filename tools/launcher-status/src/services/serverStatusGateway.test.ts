import { describe, it, expect, vi, afterEach } from "vitest";
import { invoke } from "@tauri-apps/api/core";
import { getServerStatus } from "./serverStatusGateway";
vi.mock("@tauri-apps/api/core", () => ({ invoke: vi.fn() }));
afterEach(() => { delete (window as unknown as Record<string, unknown>).__TAURI_INTERNALS__; vi.resetAllMocks(); });
describe("live server status", () => {
  it("does not invent players in browser", async () => { expect((await getServerStatus()).playersOnline).toBeNull(); expect(invoke).not.toHaveBeenCalled(); });
  it("uses native counts", async () => { Object.assign(window, { __TAURI_INTERNALS__: {} }); vi.mocked(invoke).mockResolvedValue({ state: "online", playersOnline: 2, playersMax: 20, ping: 8, message: "Альфа тестирование" }); expect((await getServerStatus()).playersOnline).toBe(2); expect(invoke).toHaveBeenCalledWith("server_status"); });
  it("clears stale data on IPC failure", async () => { Object.assign(window, { __TAURI_INTERNALS__: {} }); vi.mocked(invoke).mockRejectedValue(new Error("IPC")); expect(await getServerStatus()).toMatchObject({ state: "unknown", playersOnline: null, ping: null }); });
});
