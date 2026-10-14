import {cleanup,fireEvent,render,screen,waitFor} from "@testing-library/react";
import {afterEach,beforeEach,expect,it,vi} from "vitest";
const {native,resize}=vi.hoisted(()=>({native:{minimize:vi.fn(),toggleMaximize:vi.fn(),hide:vi.fn(),close:vi.fn(),isMaximized:vi.fn(),onResized:vi.fn()},resize:{callback:()=>{}}}));
vi.mock("@tauri-apps/api/window",()=>({getCurrentWindow:()=>native}));
import {WindowControls} from "./WindowControls";
beforeEach(()=>{Object.defineProperty(window,"__TAURI_INTERNALS__",{configurable:true,value:{}});Object.values(native).forEach(fn=>fn.mockReset());native.isMaximized.mockResolvedValue(false);native.onResized.mockImplementation(async(callback)=>{resize.callback=callback;return vi.fn();});});
afterEach(()=>{cleanup();delete (window as unknown as Record<string,unknown>).__TAURI_INTERNALS__;});
it("minimize, hide and close are separate native operations",async()=>{
  render(<WindowControls/>);
  for(const [name,method] of [["Свернуть","minimize"],["Скрыть в трей","hide"],["Закрыть","close"]] as const){
    fireEvent.click(screen.getByRole("button",{name}));
    await waitFor(()=>expect(native[method]).toHaveBeenCalledTimes(1));
    await waitFor(()=>expect((screen.getByRole("button",{name}) as HTMLButtonElement).disabled).toBe(false));
  }
  expect(native.toggleMaximize).not.toHaveBeenCalled();
});
it("toggle uses real maximized state and also follows native resize events",async()=>{
  render(<WindowControls/>);
  await waitFor(()=>expect(native.onResized).toHaveBeenCalled());
  native.isMaximized.mockResolvedValue(true);
  fireEvent.click(screen.getByRole("button",{name:"Развернуть"}));
  await waitFor(()=>expect(screen.getByRole("button",{name:"Восстановить"}).getAttribute("aria-pressed")).toBe("true"));
  native.isMaximized.mockResolvedValue(false);
  fireEvent.click(screen.getByRole("button",{name:"Восстановить"}));
  await waitFor(()=>expect(screen.getByRole("button",{name:"Развернуть"}).getAttribute("aria-pressed")).toBe("false"));
  native.isMaximized.mockResolvedValue(true);resize.callback();
  await waitFor(()=>expect(screen.getByRole("button",{name:"Восстановить"})).toBeTruthy());
  expect(native.toggleMaximize).toHaveBeenCalledTimes(2);
});
it("native failure is visible without an unhandled rejection; browser controls disabled",async()=>{
  native.minimize.mockRejectedValue(new Error("private diagnostic"));render(<WindowControls/>);
  fireEvent.click(screen.getByRole("button",{name:"Свернуть"}));
  await waitFor(()=>expect(screen.getByRole("alert").textContent).toContain("Не удалось изменить окно"));
  expect(document.body.textContent).not.toContain("private diagnostic");cleanup();
  delete (window as unknown as Record<string,unknown>).__TAURI_INTERNALS__;render(<WindowControls/>);
  expect((screen.getByRole("button",{name:"Свернуть"}) as HTMLButtonElement).disabled).toBe(true);
});
