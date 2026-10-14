import {Maximize2,Minimize2,Minus,PanelBottomClose,X} from "lucide-react";
import {useEffect,useState} from "react";
import {nativeWindow,windowGateway,type WindowAction} from "../services/windowGateway";
import "../window-controls.css";

export function WindowControls() {
  const [maximized,setMaximized]=useState(false);
  const [busy,setBusy]=useState(false);
  const [error,setError]=useState("");
  useEffect(()=>{
    let active=true;
    let unlisten:(()=>void)|undefined;
    const refresh=()=>{ void windowGateway.isMaximized().then(value=>{if(active)setMaximized(value);}).catch(()=>{if(active)setError("Не удалось определить состояние окна.");}); };
    refresh();
    void windowGateway.onResize(refresh).then(stop=>{ if(active)unlisten=stop;else stop(); }).catch(()=>{if(active)setError("Не удалось отслеживать состояние окна.");});
    return ()=>{active=false;unlisten?.();};
  },[]);
  async function run(action:WindowAction) {
    if(busy)return;
    setBusy(true);setError("");
    try { await windowGateway.run(action); if(action==="maximize")setMaximized(await windowGateway.isMaximized()); }
    catch { setError("Не удалось изменить окно. Попробуйте ещё раз."); }
    finally {setBusy(false);}
  }
  const disabled=!nativeWindow()||busy;
  return <div className="window-actions" data-window-controls>
    <button aria-label="Скрыть в трей" title="Скрыть в трей" disabled={disabled} onClick={()=>void run("hide")}><PanelBottomClose size={15}/></button>
    <button aria-label="Свернуть" title="Свернуть" disabled={disabled} onClick={()=>void run("minimize")}><Minus size={15}/></button>
    <button aria-label={maximized?"Восстановить":"Развернуть"} title={maximized?"Восстановить":"Развернуть"} aria-pressed={maximized} disabled={disabled} onClick={()=>void run("maximize")}>{maximized?<Minimize2 size={13}/>:<Maximize2 size={13}/>}</button>
    <button aria-label="Закрыть" title="Закрыть Launcher" disabled={disabled} onClick={()=>void run("close")}><X size={16}/></button>
    {error&&<span role="alert" className="window-control-error">{error}</span>}
  </div>;
}
