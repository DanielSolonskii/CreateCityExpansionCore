import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
const { invoke } = vi.hoisted(() => ({ invoke: vi.fn() }));
vi.mock("@tauri-apps/api/core", () => ({ invoke, isTauri: () => true }));
vi.mock("./services/authGateway", () => ({ authGateway: {
  getSessionUsername: () => "drannch1k", getRememberedUsername: () => "drannch1k", restore: async () => ({ username: "drannch1k" }),
  getProfileSettings: () => ({ displayName: "drannch1k", telegram: "@", city: "" }),
  getPlatformUserId: () => "test-user",
} }));
vi.mock("./services/serverStatusGateway", () => ({ getServerStatus: async () => ({ state: "unknown", label: "Нет данных", playersOnline: null, playersMax: null, ping: null, message: "Альфа тестирование" }) }));
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
      if (command === "game_status") return Promise.resolve(stopped);
      if (command === "update_status") return Promise.resolve(prepared ? { ...idleUpdate, phase: "READY", version: "alpha-0.1.4" } : idleUpdate);
      if (command === "game_prepare") { prepared = true; return Promise.resolve({ ...idleUpdate, phase: "READY", version: "alpha-0.1.4" }); }
      return Promise.resolve();
    });
    render(<App />);
    const check = await screen.findByRole("button", { name: /Проверить сборку/i });
    await screen.findByText("Обновить до 0.1.4");
    expect((check as HTMLButtonElement).disabled).toBe(false);
    const panel = screen.getByRole("region", { name: "Обновление Launcher" });
    expect(panel.style.position).toBe("fixed"); expect(panel.style.top).toBe("78px"); expect(Number(panel.style.zIndex)).toBeGreaterThan(10);
    fireEvent.click(check);
    await waitFor(() => expect(invoke).toHaveBeenCalledWith("game_prepare"));
    await waitFor(() => expect((screen.getByRole("button", { name: /^Играть/i }) as HTMLButtonElement).disabled).toBe(true));
    expect(invoke).not.toHaveBeenCalledWith("game_start");
  });
});
