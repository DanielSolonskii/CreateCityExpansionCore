import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { StrictMode } from "react";
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
    if (command === "game_status") return Promise.resolve({state:"STOPPED"});
    return Promise.resolve();
  }); });
  it("automatically verifies then installs once, including StrictMode startup", async () => {
    const required = vi.fn(); render(<StrictMode><LauncherUpdate onRequired={required} /></StrictMode>);
    await waitFor(() => expect(invoke).toHaveBeenCalledWith("launcher_update_install"));
    const commands=invoke.mock.calls.map(c=>c[0]);
    expect(commands.filter(c=>c==="launcher_update_download")).toHaveLength(1);
    expect(commands.indexOf("launcher_update_ready")).toBeLessThan(commands.indexOf("launcher_update_check"));
    expect(commands.indexOf("launcher_update_download")).toBeLessThan(commands.indexOf("launcher_update_install"));
    expect(screen.getByText("Launcher 0.1.1 · test")).toBeTruthy();
  });
  it("never offers install after failed verification", async () => {
    invoke.mockImplementation((command: string) => {
      if (command === "launcher_build_info") return Promise.resolve({ version: "0.1.1", buildId: "test" });
      if (command === "launcher_update_check") return Promise.resolve({ available: true, version: "0.1.2", mandatory: false, message: "Доступно" });
      if (command === "launcher_update_download") return Promise.reject("SHA-256 не совпадает");
      return Promise.resolve();
    });
    render(<LauncherUpdate onRequired={vi.fn()} />);
    expect(await screen.findByText("SHA-256 не совпадает")).toBeTruthy();
    expect(invoke).not.toHaveBeenCalledWith("launcher_update_install");
  });
  it("defers install while Minecraft runs, then resumes without another download", async () => {
    let running=true;
    const original=invoke.getMockImplementation()!;
    invoke.mockImplementation((command: string)=>command==="game_status" ? Promise.resolve({state:running?"RUNNING":"STOPPED"}) : original(command));
    render(<LauncherUpdate onRequired={vi.fn()} />);
    await screen.findByText("Ожидаем закрытия Minecraft");
    expect(invoke).not.toHaveBeenCalledWith("launcher_update_install");
    running=false;
    await waitFor(()=>expect(invoke).toHaveBeenCalledWith("launcher_update_install"),{timeout:3500});
    expect(invoke.mock.calls.filter(c=>c[0]==="launcher_update_download")).toHaveLength(1);
  });
  it("does not install when current or offline, and offers retry", async () => {
    const original=invoke.getMockImplementation()!;
    invoke.mockImplementation((command:string)=>command==="launcher_update_check" ? Promise.reject("Нет сети") : original(command));
    render(<LauncherUpdate onRequired={vi.fn()} />);
    await screen.findByText("Нет сети");
    expect(invoke).not.toHaveBeenCalledWith("launcher_update_download");
    invoke.mockImplementation((command:string)=>command==="launcher_update_check" ? Promise.resolve({available:false,version:"0.1.1",mandatory:false,message:"Launcher обновлён"}) : original(command));
    fireEvent.click(screen.getByText("Проверить Launcher"));
    await screen.findByText("Launcher обновлён");
    expect(invoke).not.toHaveBeenCalledWith("launcher_update_install");
  });
  it("does not loop automatically after an aborted helper installation", async () => {
    const original=invoke.getMockImplementation()!;
    invoke.mockImplementation((command:string)=>command==="launcher_update_check" ? Promise.resolve({available:true,version:"0.1.2",mandatory:false,automatic:false,message:"Нужен ручной повтор"}) : original(command));
    render(<LauncherUpdate onRequired={vi.fn()} />);
    await screen.findByText("Нужен ручной повтор");
    expect(invoke).not.toHaveBeenCalledWith("launcher_update_download");
    fireEvent.click(screen.getByText("Повторить автообновление"));
    await waitFor(()=>expect(invoke).toHaveBeenCalledWith("launcher_update_install"));
  });
});
