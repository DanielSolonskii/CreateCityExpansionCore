import { useEffect, useRef, useState } from "react";
import { invoke, isTauri } from "@tauri-apps/api/core";

type Build = { version: string; buildId: string };
type Update = { available: boolean; version: string; mandatory: boolean; automatic?: boolean; message: string };
export function LauncherUpdate({ onRequired }: { onRequired: (required: boolean) => void }) {
  const [build, setBuild] = useState<Build | null>(null);
  const [update, setUpdate] = useState<Update | null>(null);
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);
  const [downloaded, setDownloaded] = useState(false);
  const [waiting, setWaiting] = useState(false);
  const started = useRef(false);
  const locked = useRef(false);
  const candidate = useRef<Update | null>(null);
  const verified = useRef(false);
  async function check() {
    if (locked.current) return;
    locked.current = true;
    setBusy(true);
    try {
      const next = await invoke<Update>("launcher_update_check");
      candidate.current = next; verified.current = false;
      setUpdate(next); setMessage(next.message); setDownloaded(false); setWaiting(false);
      onRequired(next.available);
      if (next.available && next.automatic !== false) await apply();
    } catch (error) { setMessage(String(error)); }
    finally { locked.current = false; setBusy(false); }
  }
  async function apply() {
    try {
      if (!verified.current) {
        setMessage("Автообновление: скачиваем и проверяем Launcher…");
        await invoke<string>("launcher_update_download");
        verified.current = true; setDownloaded(true);
      }
      const game = await invoke<{state: string}>("game_status");
      if (game.state === "RUNNING" || game.state === "STARTING") {
        setWaiting(true); setMessage("Закройте Minecraft — Launcher обновится автоматически."); return;
      }
      setWaiting(false);
      setMessage("Устанавливаем Launcher. Он автоматически откроется снова…");
      await invoke("launcher_update_install");
    } catch (error) {
      setWaiting(false); setMessage(String(error));
      onRequired(Boolean(candidate.current?.available && candidate.current.mandatory));
    }
  }
  useEffect(() => {
    if (!isTauri() || started.current) return;
    started.current = true; // No duplicate startup job under React StrictMode.
    void (async () => {
      try {
        setBuild(await invoke<Build>("launcher_build_info"));
        await invoke("launcher_update_ready");
        await check();
      } catch { setMessage("Не удалось подтвердить обновление; рабочая версия сохранена"); }
    })();
    // Startup only. Native side independently verifies all update inputs.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);
  async function install() {
    if (locked.current) return;
    locked.current = true;
    setBusy(true);
    try { await apply(); }
    finally { locked.current = false; setBusy(false); }
  }
  useEffect(() => {
    if (!waiting) return;
    const timer = setInterval(() => void install(), 2000);
    return () => clearInterval(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [waiting]);
  if (!build) return null;
  return <section aria-label="Обновление Launcher" style={{ position: "fixed", top: 78, left: 0, right: 0, zIndex: 12, minHeight: 48, padding: "6px 18px", fontSize: 12, background: "#161a20", color: "#dce3e8", display: "flex", flexWrap: "wrap", alignItems: "center", gap: 12 }}>
    <span title={build.buildId}>Launcher {build.version} · {build.buildId}</span>
    <span role="status" style={{ flex: 1 }}>{message}</span>
    {update?.available ? <button style={{ padding: "7px 12px", fontSize: 14, cursor: "pointer", background: "#7d4c2e", border: "1px solid #bd804d", borderRadius: 4 }} disabled={busy || waiting} onClick={() => void install()}>{waiting ? "Ожидаем закрытия Minecraft" : downloaded ? "Повторить установку" : "Повторить автообновление"}</button>
      : <button style={{ padding: "7px 12px", fontSize: 14, cursor: "pointer", background: "#28332f", border: "1px solid #64776c", borderRadius: 4 }} disabled={busy} onClick={() => void check()}>Проверить Launcher</button>}
  </section>;
}
