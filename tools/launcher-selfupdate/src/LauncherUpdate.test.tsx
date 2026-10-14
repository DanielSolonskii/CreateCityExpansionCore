import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
const { invoke } = vi.hoisted(() => ({ invoke: vi.fn() }));
vi.mock("@tauri-apps/api/core", () => ({ invoke, isTauri: () => true }));
import { LauncherUpdate } from "./LauncherUpdate";
describe("Launcher self-update", () => {
  afterEach(cleanup);
  beforeEach(() => { invoke.mockReset(); invoke.mockImplementation((command: string) => {
    if (command === "launcher_build_info") return Promise.resolve({ version: "0.1.1", buildId: "test" });
    if (command === "launcher_update_check") return Promise.resolve({ available: true, version: "0.1.2", mandatory: true, message: "Доступна новая версия" });
    if (command === "launcher_update_download") return Promise.resolve("Проверено");
    return Promise.resolve();
  }); });
  it("shows version and requires explicit verified download then install", async () => {
    const required = vi.fn(); render(<LauncherUpdate onRequired={required} />);
    const button = await screen.findByText("Обновить до 0.1.2");
    await waitFor(() => expect(required).toHaveBeenCalledWith(true));
    fireEvent.click(button);
    fireEvent.click(await screen.findByText("Установить и перезапустить"));
    await waitFor(() => expect(invoke).toHaveBeenCalledWith("launcher_update_install"));
    expect(screen.getByText("Launcher 0.1.1 · test")).toBeTruthy();
  });
  it("never offers install after failed verification", async () => {
    invoke.mockImplementation((command: string) => {
      if (command === "launcher_build_info") return Promise.resolve({ version: "0.1.1", buildId: "test" });
      if (command === "launcher_update_check") return Promise.resolve({ available: true, version: "0.1.2", mandatory: false, message: "Доступно" });
      if (command === "launcher_update_download") return Promise.reject("SHA-256 не совпадает");
      return Promise.resolve();
    });
    render(<LauncherUpdate onRequired={vi.fn()} />); fireEvent.click(await screen.findByText("Обновить до 0.1.2"));
    expect(await screen.findByText("SHA-256 не совпадает")).toBeTruthy(); expect(screen.queryByText("Установить и перезапустить")).toBeNull();
  });
});
