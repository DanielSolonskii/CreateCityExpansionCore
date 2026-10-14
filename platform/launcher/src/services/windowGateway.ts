import { getCurrentWindow } from "@tauri-apps/api/window";
export type WindowAction = "minimize" | "maximize" | "hide" | "close";
export const nativeWindow = () => "__TAURI_INTERNALS__" in window;
export const windowGateway = {
  async run(action: WindowAction) {
    if (!nativeWindow()) throw new Error("Window controls require Windows Launcher");
    const current = getCurrentWindow();
    switch (action) {
      case "minimize": await current.minimize(); break;
      case "maximize": await current.toggleMaximize(); break;
      case "hide": await current.hide(); break;
      case "close": await current.close(); break;
    }
  },
  async isMaximized() { return nativeWindow() ? getCurrentWindow().isMaximized() : false; },
  async onResize(callback: () => void) {
    if (!nativeWindow()) return () => {};
    return getCurrentWindow().onResized(callback);
  },
};
