import { act, cleanup, renderHook } from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import { gameGateway, idleUpdate, stopped } from "./gameGateway";
import { useGameJob } from "./useGameJob";

vi.mock("@tauri-apps/api/event", () => ({ listen: vi.fn(async () => () => {}) }));

afterEach(() => {
  cleanup();
  vi.useRealTimers();
  vi.restoreAllMocks();
  Reflect.deleteProperty(window, "__TAURI_INTERNALS__");
});

it("polls slowly at rest, follows an active job and refreshes on return from the tray", async () => {
  vi.useFakeTimers();
  Object.defineProperty(window, "__TAURI_INTERNALS__", { value: {}, configurable: true });
  const visibility = vi.spyOn(document, "hidden", "get").mockReturnValue(false);
  const status = vi.spyOn(gameGateway, "status").mockResolvedValue(stopped);
  const progress = vi.spyOn(gameGateway, "updateStatus").mockResolvedValue(idleUpdate);
  const { result } = renderHook(() => useGameJob());
  const advance = (ms: number) => act(async () => { await vi.advanceTimersByTimeAsync(ms); });
  await advance(11000);
  expect(status).toHaveBeenCalledTimes(1);
  status.mockResolvedValue({ ...stopped, state: "STARTING" });
  await advance(1000);
  await advance(750);
  expect(status).toHaveBeenCalledTimes(3);
  visibility.mockReturnValue(true);
  await act(async () => { document.dispatchEvent(new Event("visibilitychange")); });
  await advance(29000);
  expect(status).toHaveBeenCalledTimes(4);
  status.mockResolvedValue({ ...stopped, state: "RUNNING" });
  visibility.mockReturnValue(false);
  await act(async () => { window.dispatchEvent(new Event("focus")); });
  expect(result.current.game.state).toBe("RUNNING");
  expect(progress).toHaveBeenCalledTimes(5);
});
