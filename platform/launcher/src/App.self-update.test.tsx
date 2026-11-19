import { cleanup, fireEvent, render, screen } from "@testing-library/react";
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
vi.mock("./services/serverStatusGateway", async importOriginal => {
  const actual = await importOriginal<typeof import("./services/serverStatusGateway")>();
  return { ...actual, getServerStatus: async () => actual.unavailableStatus };
});
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
    render(<App />);
    const check = await screen.findByRole("button", { name: /Проверить сборку/i });
    await screen.findByText("Повторить установку");
    expect((check as HTMLButtonElement).disabled).toBe(false);
    const panel = screen.getByRole("region", { name: "Обновление Launcher" });
    expect(panel.classList.contains("launcher-update")).toBe(true);
    expect(screen.getByRole("button",{name:/Launcher 0.1.2/}).getAttribute("aria-expanded")).toBe("true");
    fireEvent.click(check);
    await vi.waitFor(() => expect(invoke).toHaveBeenCalledWith("game_prepare"));
    await vi.waitFor(() => expect(screen.queryByRole("button", { name: /Проверить сборку/ })).toBeNull());
    expect(screen.queryByRole("button", { name: /^Играть/i })).toBeNull();
    expect(screen.getByRole("button", { name: "Обновить лаунчер" })).toBeTruthy();
    expect(invoke).not.toHaveBeenCalledWith("game_start");
  });
});
