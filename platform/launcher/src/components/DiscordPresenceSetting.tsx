import {useEffect,useState} from 'react';
import {invoke} from '@tauri-apps/api/core';
import {Monitor} from 'lucide-react';
export function DiscordPresenceSetting({open}:{open:boolean}){
 const [enabled,setEnabled]=useState(false),[ready,setReady]=useState(false),[busy,setBusy]=useState(false),[error,setError]=useState('');
 useEffect(()=>{if(!open)return;let active=true;setReady(false);setError('');void invoke<boolean>('discord_presence_enabled').then(value=>{if(active){setEnabled(value);setReady(true);}}).catch(()=>{if(active)setError('Настройки Discord недоступны.');});return()=>{active=false;};},[open]);
 async function toggle(){setBusy(true);setError('');try{await invoke('save_discord_presence',{enabled:!enabled});setEnabled(v=>!v);}catch{setError('Не удалось сохранить настройку Discord.');}finally{setBusy(false);}}
 return <><button className="setting-row" role="switch" aria-checked={enabled} aria-label="Активность в Discord" disabled={!ready||busy} onClick={()=>void toggle()}><Monitor size={18}/><span><strong>Активность в Discord</strong><small>Без ника и личных данных · применяется сразу</small></span><span className={`toggle ${enabled?'is-on':''}`}><i/></span></button>{error&&<div className="settings-note" role="alert">{error}</div>}</>;
}
