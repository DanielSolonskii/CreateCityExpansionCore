import { invoke } from "@tauri-apps/api/core";

export type GamePhase = "STARTING" | "RUNNING" | "STOPPED" | "FAILED";
export interface GameStatus { state: GamePhase; pid: number | null; message: string; logPath: string | null }
export const stopped: GameStatus = { state: "STOPPED", pid: null, message: "Готово к запуску", logPath: null };
export interface UpdateProgress {
  phase: "" | "CHECKING" | "DOWNLOADING" | "VERIFYING" | "APPLYING" | "READY" | "FAILED";
  version: string | null; checkedFiles: number; totalFiles: number; completedFiles: number;
  changedFiles: number; downloadedBytes: number; totalBytes: number; message: string; cachedFiles?:number;verifiedBytes?:number;
}
export const idleUpdate: UpdateProgress = { phase: "", version: null, checkedFiles: 0, totalFiles: 0,
  completedFiles: 0, changedFiles: 0, downloadedBytes: 0, totalBytes: 0, message: "" };
export function updateLabel(progress: UpdateProgress): string {
  const labels = { "": "Проверить сборку", CHECKING: "Проверка обновлений", DOWNLOADING: "Загрузка", VERIFYING: "Проверка", APPLYING: "Применение", READY: "Играть", FAILED: "Повторить обновление" };
  return labels[progress.phase];
}
export function updatePercent(progress: UpdateProgress): number {
  const ratio = progress.phase === "CHECKING" ? progress.checkedFiles / (progress.totalFiles || 1) :
    progress.phase === "READY" || progress.phase === "VERIFYING" || progress.phase === "APPLYING" ? 1 :
      progress.downloadedBytes / (progress.totalBytes || 1);
  return Math.min(100, Math.max(0, ratio * 100));
}
function nativeOnly() {
  if (!("__TAURI_INTERNALS__" in window)) throw new Error("Minecraft запускается только из Windows Launcher, не из браузера.");
}
export const gameGateway = {
  async updateStatus(): Promise<UpdateProgress> {
    if (!("__TAURI_INTERNALS__" in window)) return idleUpdate;
    return invoke<UpdateProgress>("update_status");
  },
  async prepare(): Promise<UpdateProgress> {
    nativeOnly();
    return invoke<UpdateProgress>("game_prepare");
  },
  async status(): Promise<GameStatus> {
    if (!("__TAURI_INTERNALS__" in window)) return stopped;
    return invoke<GameStatus>("game_status");
  },
  async start(): Promise<GameStatus> {
    nativeOnly();
    // No username, Java path, shell command, password or game token from the WebView.
    return invoke<GameStatus>("game_start");
  },
  async logout(): Promise<void> {
    if ("__TAURI_INTERNALS__" in window) await invoke("game_logout");
  },
};
