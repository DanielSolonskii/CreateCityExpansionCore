import { invoke } from "@tauri-apps/api/core";
import type { ServerStatus } from "../types/launcher";
export const unavailableStatus: ServerStatus = {
  state: "unknown", label: "Нет связи с сервером", playersOnline: null,
  playersMax: null, ping: null, message: "Альфа тестирование",
};
export async function getServerStatus(): Promise<ServerStatus> {
  if (!("__TAURI_INTERNALS__" in window)) return { ...unavailableStatus, label: "Статус доступен в Launcher" };
  try { return await invoke<ServerStatus>("server_status"); }
  catch { return { ...unavailableStatus }; }
}
