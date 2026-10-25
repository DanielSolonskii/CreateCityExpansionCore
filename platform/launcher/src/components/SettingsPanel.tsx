import { Check, FolderOpen, HardDrive, Monitor, SlidersHorizontal, Volume2, X } from "lucide-react";
import { useEffect, useState } from "react";
import { launcherSettings } from "../services/launcherSettings";

export function SettingsPanel({ open, onClose,onRepair,busy=false }: { open: boolean; onClose: () => void;onRepair?:()=>void;busy?:boolean }) {
  const [autoStart,setAutoStart]=useState(true);
  const [diagnostics,setDiagnostics]=useState(true);
  const [memory, setMemory] = useState(4);
  const [closeAfterLaunch, setCloseAfterLaunch] = useState(false);
  const [resolution,setResolution]=useState("1280x720");
  const [ready,setReady]=useState(false),[saving,setSaving]=useState(false),[message,setMessage]=useState("");
  useEffect(()=>{if(!open)return;let active=true;setReady(false);setMessage("");void launcherSettings.load().then(s=>{if(active){setAutoStart(s.autoStart!==false);setDiagnostics(s.diagnosticsEnabled!==false);setMemory(s.memoryGb);setResolution(`${s.width}x${s.height}`);setCloseAfterLaunch(s.hideAfterLaunch);setReady(true);}}).catch(()=>{if(active)setMessage("Не удалось загрузить настройки. Откройте Windows Launcher.");});return()=>{active=false;};},[open]);
  async function save(){setSaving(true);const [width,height]=resolution.split("x").map(Number);try{await launcherSettings.save({memoryGb:memory,width,height,hideAfterLaunch:closeAfterLaunch,autoStart,diagnosticsEnabled:diagnostics});setMessage("Сохранено. Применится при следующем запуске игры.");}catch{setMessage("Не удалось сохранить настройки.");}finally{setSaving(false);}}

  return (
    <>
      <button className={`settings-backdrop ${open ? "is-open" : ""}`} onClick={onClose} aria-label="Закрыть настройки" tabIndex={open ? 0 : -1} />
      <aside className={`settings-panel ${open ? "is-open" : ""}`} aria-hidden={!open}>
        <header>
          <div><span className="eyebrow">ЛОКАЛЬНЫЕ ПАРАМЕТРЫ</span><h2>Настройки</h2></div>
          <button className="icon-button" onClick={onClose}><X size={20} /></button>
        </header>
        <div className="settings-group">
          <div className="setting-label"><SlidersHorizontal size={17} /><span>Память для игры</span><strong>{memory} ГБ</strong></div>
          <input aria-label="Память для игры" disabled={!ready || saving} type="range" min="4" max="16" step="1" value={memory} onChange={(event) => setMemory(Number(event.target.value))} />
          <div className="range-labels"><span>4 ГБ</span><span>Оставьте 2–4 ГБ для Windows</span><span>16 ГБ</span></div>
        </div>
        <button className="setting-row" onClick={()=>{void launcherSettings.openFolder().catch(()=>setMessage("Не удалось открыть папку игры."));}}><FolderOpen size={18} /><span><strong>Папка игры</strong><small>%LOCALAPPDATA%\Recreated\instances\alpha</small></span><span>Открыть</span></button>
        <label className="setting-row"><Monitor size={18}/><span><strong>Разрешение</strong><small>Размер окна Minecraft</small></span><select aria-label="Разрешение" disabled={!ready || saving} value={resolution} onChange={e=>setResolution(e.target.value)}>{["1280x720","1600x900","1920x1080"].map(r=><option key={r} value={r}>{r.replace("x"," × ")}</option>)}</select></label>
        <button className="setting-row" disabled={!ready || saving} onClick={() => setCloseAfterLaunch((value) => !value)}><HardDrive size={18} /><span><strong>Скрывать Launcher в трей</strong><small>После запуска Minecraft</small></span><span className={`toggle ${closeAfterLaunch ? "is-on" : ""}`}><i /></span></button>
        <button className="setting-row" disabled={!ready||saving} onClick={()=>setAutoStart(v=>!v)}><Monitor size={18}/><span><strong>Запускать игру после подготовки</strong><small>Одно нажатие «Играть», работает и в трее</small></span><span className={`toggle ${autoStart?"is-on":""}`}><i/></span></button>
        <button className="setting-row" disabled={!ready||saving} onClick={()=>setDiagnostics(v=>!v)}><SlidersHorizontal size={18}/><span><strong>Диагностика и обратная связь</strong><small>FPS, запуск, ошибки · без переписки и координат</small></span><span className={`toggle ${diagnostics?"is-on":""}`}><i/></span></button>
        {onRepair&&<button className="setting-row" disabled={!ready||saving||busy} onClick={onRepair}><HardDrive size={18}/><span><strong>Полная проверка и восстановление</strong><small>Проверить все файлы, включая кэш ассетов</small></span></button>}
        <button className="setting-row" disabled={!ready || saving} onClick={()=>void save()}><Check size={16}/><span><strong>{saving ? "Сохранение…" : "Сохранить настройки"}</strong><small>При следующем запуске игры</small></span></button>
        <div className="settings-note" role="status">{message || "Настройки хранятся только на этом устройстве."}</div>
      </aside>
    </>
  );
}
