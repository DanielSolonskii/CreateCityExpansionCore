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
  const [failed, setFailed] = useState(false);
  const [expanded, setExpanded] = useState(false);
  const started = useRef(false);
  const locked = useRef(false);
  const candidate = useRef<Update | null>(null);
  const verified = useRef(false);
  async function check() {
    if (locked.current) return;
    locked.current = true;
    setBusy(true);
    setFailed(false);
    try {
      const next = await invoke<Update>("launcher_update_check");
      candidate.current = next; verified.current = false;
      setUpdate(next); setMessage(next.message); setDownloaded(false); setWaiting(false);
      onRequired(next.available && next.mandatory);
      if (next.available) setExpanded(true);
      if (next.available && next.automatic !== false) await apply();
    } catch (error) { setFailed(true); setExpanded(true); setMessage(String(error)); }
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
      setFailed(true); setExpanded(true);
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
      } catch { setFailed(true); setExpanded(true); setMessage("Не удалось подтвердить обновление; рабочая версия сохранена"); }
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
  return <section aria-label="Обновление Launcher" className={`launcher-update ${expanded ? "is-expanded" : ""} ${failed ? "has-error" : ""}`}>
    <button className="launcher-update__summary" aria-expanded={expanded} onClick={() => setExpanded(v => !v)}>
      <span className={busy ? "status-light is-busy" : "status-light"} />
      <span>Launcher {build.version}</span><span>{busy ? "Обновляем…" : update?.available ? "Доступно обновление" : failed ? "Нет связи" : "О приложении"}</span>
    </button>
    {expanded && <div className="launcher-update__body">
      <small>Сборка {build.buildId}</small><p role="status">{message}</p>
      {update?.available ? <button className="text-button" disabled={busy || waiting} onClick={() => { setFailed(false); void install(); }}>{waiting ? "Ожидаем закрытия Minecraft" : downloaded ? "Повторить установку" : "Повторить автообновление"}</button>
        : <button className="text-button" disabled={busy} onClick={() => void check()}>Проверить Launcher</button>}
      <button className="text-button" onClick={() => setExpanded(false)}>Скрыть</button>
    </div>}
  </section>;
}
