import { afterEach, describe, expect, it, vi } from "vitest";
const { invoke } = vi.hoisted(() => ({ invoke: vi.fn() }));
vi.mock("@tauri-apps/api/core", () => ({ invoke }));
import { gameGateway, stopped, idleUpdate, updateLabel, updatePercent } from "./gameGateway";
afterEach(() => { delete (window as unknown as Record<string, unknown>).__TAURI_INTERNALS__; invoke.mockReset(); });
describe("standalone game bridge", () => {
  it("gets update progress and prepares only in native Launcher", async () => {
    expect(await gameGateway.updateStatus()).toEqual(idleUpdate);
    await expect(gameGateway.prepare()).rejects.toThrow("Windows Launcher");
    (window as unknown as Record<string, unknown>).__TAURI_INTERNALS__ = {};
    invoke.mockResolvedValue({ ...idleUpdate, phase: "READY", version: "alpha-0.1.0" });
    expect((await gameGateway.prepare()).version).toBe("alpha-0.1.0");
    expect(invoke).toHaveBeenLastCalledWith("game_prepare");
    await gameGateway.updateStatus();
    expect(invoke).toHaveBeenLastCalledWith("update_status");
  });
  it("reports real bytes/files rather than mock progress", () => {
    expect(updateLabel({ ...idleUpdate, phase: "CHECKING" })).toBe("Проверка обновлений");
    expect(updateLabel({ ...idleUpdate, phase: "DOWNLOADING" })).toBe("Загрузка");
    expect(updateLabel({ ...idleUpdate, phase: "VERIFYING" })).toBe("Проверка");
    expect(updateLabel({ ...idleUpdate, phase: "READY" })).toBe("Играть");
    expect(updatePercent({ ...idleUpdate, phase: "DOWNLOADING", totalBytes: 100, downloadedBytes: 25 })).toBe(25);
    expect(updatePercent({ ...idleUpdate, phase: "CHECKING", totalFiles: 4, checkedFiles: 3 })).toBe(75);
  });
  it("does not claim a mock launch in browser", async () => {
    expect(await gameGateway.status()).toEqual(stopped);
    await expect(gameGateway.start()).rejects.toThrow("Windows Launcher");
    expect(invoke).not.toHaveBeenCalled();
  });
  it("launches without WebView-controlled identity or command", async () => {
    (window as unknown as Record<string, unknown>).__TAURI_INTERNALS__ = {};
    invoke.mockResolvedValue({ ...stopped, state: "RUNNING", pid: 123 });
    expect((await gameGateway.start()).state).toBe("RUNNING");
    expect(invoke).toHaveBeenCalledWith("game_start");
  });
  it("preserves FAILED/status/log path and clears native login on logout", async () => {
    (window as unknown as Record<string, unknown>).__TAURI_INTERNALS__ = {};
    const failed = { ...stopped, state: "FAILED", message: "Java failed", logPath: "game.log" };
    invoke.mockResolvedValue(failed);
    expect(await gameGateway.status()).toEqual(failed);
    await gameGateway.logout();
    expect(invoke).toHaveBeenLastCalledWith("game_logout");
  });
});
