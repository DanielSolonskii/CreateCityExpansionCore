import { act, cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { LauncherSnapshot } from "./types/launcher";
const { invoke, serverStatus } = vi.hoisted(() => ({
  invoke: vi.fn(),
  serverStatus: { state: "unknown" as const, label: "Нет данных", playersOnline: null, playersMax: null, ping: null, message: "Альфа тестирование" },
}));
vi.mock("@tauri-apps/api/core", () => ({ invoke, isTauri: () => true }));
vi.mock("./services/authGateway", () => ({ authGateway: {
  getSessionUsername: () => "drannch1k", getRememberedUsername: () => "drannch1k", restore: async () => ({ username: "drannch1k" }),
  getProfileSettings: () => ({ displayName: "drannch1k", telegram: "@", city: "" }),
  getPlatformUserId: () => "test-user",
} }));
vi.mock("./services/serverStatusGateway", () => ({ getServerStatus: async () => serverStatus }));
vi.mock("./services/mockLauncherGateway", () => ({ launcherGateway: {
  getSnapshot: async (): Promise<LauncherSnapshot> => ({
    profile: { id: "test", displayName: "drannch1k", subtitle: "", initials: "DR", accountId: "test-user", role: "", city: "", playtime: "", joinedAt: "", verified: true },
    status: serverStatus,
    build: { version: "alpha-0.1.4", minecraftVersion: "1.21.1", loader: "NeoForge", size: "", updateAvailable: false },
    news: [],
  }),
} }));
import App from "./App";
import { idleUpdate, stopped } from "./services/gameGateway";
afterEach(() => { cleanup(); vi.resetAllMocks(); delete (window as unknown as Record<string, unknown>).__TAURI_INTERNALS__; });
describe("Mandatory Launcher update", () => {
  it("keeps game file check enabled while blocking actual Play", async () => {
    (window as unknown as Record<string, unknown>).__TAURI_INTERNALS__ = {};
    let prepared = false;
    invoke.mockImplementation((command: string) => {
      if (command === "launcher_build_info") return Promise.resolve({ version: "0.1.2", buildId: "test" });
      if (command === "launcher_update_check") return Promise.resolve({ available: true, mandatory: true, version: "0.1.4", message: "Обновление" });
      if (command === "launcher_update_install") return Promise.reject("Тестовый отказ установки");
      if (command === "launcher_news") return Promise.resolve([]);
      if (command === "game_status") return Promise.resolve(stopped);
      if (command === "update_status") return Promise.resolve(prepared ? { ...idleUpdate, phase: "READY", version: "alpha-0.1.4" } : idleUpdate);
      if (command === "game_prepare") { prepared = true; return Promise.resolve({ ...idleUpdate, phase: "READY", version: "alpha-0.1.4" }); }
      return Promise.resolve();
    });
    await act(async () => { render(<App />); });
    const check = screen.getByText("Проверить сборку", { selector: "footer button span" }).closest("button")!;
    expect(check.disabled).toBe(false);
    const retry = screen.getByText("Повторить установку", { selector: "button" });
    expect(retry.closest("section")?.getAttribute("aria-label")).toBe("Обновление Launcher");
    const summary = screen.getByText("Launcher 0.1.2", { selector: "button span" }).closest("button")!;
    expect(summary.getAttribute("aria-expanded")).toBe("true");
    await act(async () => { fireEvent.click(check); });
    expect(invoke).toHaveBeenCalledWith("game_prepare");
    const play = screen.getByText("Играть", { selector: "footer button span" }).closest("button")!;
    expect(play.disabled).toBe(true);
    fireEvent.click(play);
    expect(invoke).not.toHaveBeenCalledWith("game_start");
  });
});
