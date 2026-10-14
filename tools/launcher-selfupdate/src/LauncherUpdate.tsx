import { useEffect, useState } from "react";
import { invoke, isTauri } from "@tauri-apps/api/core";

type Build = { version: string; buildId: string };
type Update = { available: boolean; version: string; mandatory: boolean; message: string };
export function LauncherUpdate({ onRequired }: { onRequired: (required: boolean) => void }) {
  const [build, setBuild] = useState<Build | null>(null);
  const [update, setUpdate] = useState<Update | null>(null);
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);
  const [downloaded, setDownloaded] = useState(false);
  async function check() {
    setBusy(true);
    try {
      const next = await invoke<Update>("launcher_update_check");
      setUpdate(next); setMessage(next.message); setDownloaded(false);
      onRequired(next.available && next.mandatory);
    } catch (error) { setMessage(String(error)); }
    finally { setBusy(false); }
  }
  useEffect(() => {
    if (!isTauri()) return;
    void invoke<Build>("launcher_build_info").then(setBuild);
    void invoke("launcher_update_ready").catch(() => setMessage("Не удалось подтвердить обновление; рабочая версия сохранена"));
    void check();
    // Startup only. Native side independently verifies all update inputs.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);
  async function install() {
    setBusy(true);
    try {
      if (!downloaded) {
        setMessage("Скачиваем и проверяем обновление Launcher…");
        setMessage(await invoke<string>("launcher_update_download")); setDownloaded(true);
      } else {
        setMessage("Устанавливаем Launcher. Он автоматически откроется снова…");
        await invoke("launcher_update_install");
      }
    } catch (error) { setMessage(String(error)); }
    finally { setBusy(false); }
  }
  if (!build) return null;
  return <section aria-label="Обновление Launcher" style={{ position: "fixed", top: 78, left: 0, right: 0, zIndex: 12, minHeight: 48, padding: "6px 18px", fontSize: 12, background: "#161a20", color: "#dce3e8", display: "flex", flexWrap: "wrap", alignItems: "center", gap: 12 }}>
    <span title={build.buildId}>Launcher {build.version} · {build.buildId}</span>
    <span role="status" style={{ flex: 1 }}>{message}</span>
    {update?.available ? <button style={{ padding: "7px 12px", fontSize: 14, cursor: "pointer", background: "#7d4c2e", border: "1px solid #bd804d", borderRadius: 4 }} disabled={busy} onClick={() => void install()}>{downloaded ? "Установить и перезапустить" : `Обновить до ${update.version}`}</button>
      : <button style={{ padding: "7px 12px", fontSize: 14, cursor: "pointer", background: "#28332f", border: "1px solid #64776c", borderRadius: 4 }} disabled={busy} onClick={() => void check()}>Проверить Launcher</button>}
  </section>;
}
