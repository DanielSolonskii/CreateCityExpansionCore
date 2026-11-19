import { Check, FolderOpen, HardDrive, X } from "lucide-react";
import { useEffect, useState } from "react";
import { launcherSettings, type LauncherSettings, type MemoryInfo } from "../services/launcherSettings";
import { DiscordPresenceSetting } from "./DiscordPresenceSetting";
import { useOverlay } from "./useOverlay";

function Switch({ label, hint, value, disabled, onChange }: {
  label: string; hint?: string; value: boolean; disabled?: boolean; onChange: () => void;
}) {
  return <button type="button" role="switch" aria-checked={value} aria-label={label} disabled={disabled}
    className="setting-row" onClick={onChange}>
    <span><strong>{label}</strong>{hint && <small>{hint}</small>}</span>
    <span className={`toggle ${value ? "is-on" : ""}`} aria-hidden="true"><i/></span>
  </button>;
}

export function SettingsPanel({ open, onClose, onRepair, busy = false }: {
  open: boolean; onClose: () => void; onRepair?: () => void; busy?: boolean;
}) {
  const overlay = useOverlay<HTMLElement>(open, onClose);
  const [saved, setSaved] = useState<LauncherSettings | null>(null);
  const [draft, setDraft] = useState<LauncherSettings | null>(null);
  const [ram, setRam] = useState<MemoryInfo | null>(null);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState("");
  const [minimal, setMinimal] = useState(false);
  useEffect(() => {
    if (!open) return;
    let active = true;
    setSaved(null); setDraft(null); setRam(null); setMessage("");
    setMinimal(document.documentElement.dataset.motion === "minimal");
    void launcherSettings.memory().then(info => { if (active) setRam(info); }).catch(() => {});
    void launcherSettings.load().then(settings => {
      if (!active) return;
      const value = { ...settings, autoStart: settings.autoStart !== false, diagnosticsEnabled: settings.diagnosticsEnabled !== false };
      setSaved(value); setDraft(value);
    }).catch(() => { if (active) setMessage("Настройки игры доступны в установленном лаунчере."); });
    return () => { active = false; };
  }, [open]);

  const locked = !draft || saving;
  const dirty = draft && saved && (Object.keys(draft) as (keyof LauncherSettings)[]).some(key => draft[key] !== saved[key]);
  function change(values: Partial<LauncherSettings>) {
    setMessage("");
    setDraft(current => current && { ...current, ...values });
  }
  async function save() {
    if (!draft || !dirty || saving) return;
    setSaving(true);
    try {
      await launcherSettings.save(draft);
      setSaved(draft); setMessage("Сохранено. Применится при следующем запуске игры.");
    } catch { setMessage("Не удалось сохранить настройки."); }
    finally { setSaving(false); }
  }
  function toggleMotion() {
    try {
      localStorage.setItem("recreated.motion", minimal ? "normal" : "minimal");
      setMinimal(!minimal);
      document.documentElement.dataset.motion = minimal ? "normal" : "minimal";
      window.dispatchEvent(new Event("profile-appearance"));
    } catch { setMessage("Не удалось сохранить настройку движения."); }
  }

  return <>
    {open && <div className="settings-backdrop is-open" onClick={onClose} aria-hidden="true"/>}
    <aside ref={overlay} role="dialog" aria-modal="true" aria-label="Настройки Launcher" aria-hidden={!open}
      className={`settings-panel ${open ? "is-open" : ""}`}>
      <header><h2>Настройки</h2><button className="icon-button" aria-label="Закрыть настройки" onClick={onClose}><X size={20}/></button></header>
      <section><h3>Игра</h3><div className="settings-group">
        <label className="setting-label" htmlFor="game-memory">Память для игры <strong>{draft ? draft.memoryGb + " ГБ" : "—"}</strong></label>
        <input id="game-memory" aria-label="Память для игры" disabled={locked || !ram || ram.maxGb < 4} type="range" min="4" max={Math.max(4, ram?.maxGb ?? 4)}
          step="1" value={draft?.memoryGb ?? 4} onChange={e => change({ memoryGb: Number(e.target.value) })}/>
        {ram && <small>Установлено {ram.totalGb} ГБ · доступно для игры до {ram.maxGb} ГБ</small>}
        {ram && ram.maxGb >= 4 && <button className="text-button" disabled={locked} onClick={() => change({ memoryGb: ram.recommendedGb })}>Рекомендуется {ram.recommendedGb} ГБ · применить</button>}
        {ram && draft && (ram.maxGb < 4 || draft.memoryGb > ram.maxGb) && <p role="alert">Недостаточно памяти для выбранного значения.</p>}
      </div>
      <label className="setting-row"><span><strong>Разрешение</strong><small>Размер окна Minecraft</small></span>
        <select aria-label="Разрешение" disabled={locked} value={draft ? draft.width + "x" + draft.height : "1280x720"} onChange={e => {
          const [width, height] = e.target.value.split("x").map(Number); change({ width, height });
        }}>{["1280x720","1600x900","1920x1080"].map(r => <option key={r} value={r}>{r.replace("x"," × ")}</option>)}</select></label>
      <button className="setting-row" onClick={() => void launcherSettings.openFolder().catch(() => setMessage("Не удалось открыть папку игры."))}><FolderOpen size={18}/><span><strong>Папка игры</strong></span><span>Открыть</span></button>
      <Switch label="Запускать игру после подготовки" disabled={locked} value={draft?.autoStart !== false} onChange={() => change({ autoStart: !draft?.autoStart })}/>
      </section><section><h3>Лаунчер</h3>
      <Switch label="Скрывать Launcher в трей" hint="После запуска Minecraft" disabled={locked} value={draft?.hideAfterLaunch ?? false} onChange={() => change({ hideAfterLaunch: !draft?.hideAfterLaunch })}/>
      <Switch label="Минимальные анимации" hint="На этом устройстве · применяется сразу" value={minimal} onChange={toggleMotion}/>
      <DiscordPresenceSetting open={open}/>
      </section><section><h3>Диагностика</h3>
      <Switch label="Диагностика и обратная связь" hint="FPS, запуск и ошибки" disabled={locked} value={draft?.diagnosticsEnabled !== false} onChange={() => change({ diagnosticsEnabled: !draft?.diagnosticsEnabled })}/>
      {onRepair && <button className="setting-row" disabled={locked || busy} onClick={onRepair}><HardDrive size={18}/><span><strong>Полная проверка и восстановление</strong><small>Включая кэш ассетов</small></span></button>}
      </section><div className="settings-save"><button className="job-primary" disabled={!dirty || saving} onClick={save}><Check size={16}/>{saving ? "Сохранение…" : "Сохранить настройки"}</button>
      <p className="settings-note" role="status">{message || (dirty ? "Есть несохранённые изменения." : "Настройки хранятся на этом устройстве.")}</p></div>
    </aside>
  </>;
}
