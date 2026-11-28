import { useEffect, useState, useRef } from "react";
import { invoke } from "@tauri-apps/api/core";
import { X, Check } from "lucide-react";
import "./telemetry-survey.css";
import { useOverlay } from "./useOverlay";
export function TelemetrySurvey({enabled}:{enabled:boolean}){
 const [open,setOpen]=useState(false),[reason,setReason]=useState(""),[interest,setInterest]=useState(""),[busy,setBusy]=useState(false),[error,setError]=useState("");
 const dismissed = useRef(false);
 const overlay = useOverlay<HTMLElement>(open && enabled, () => dismiss());
 function dismiss() {
   dismissed.current = true;
   setOpen(false);
   if (!busy) void invoke("telemetry_feedback", {reason:"SKIP", interest:"SKIP"}).catch(() => {});
 }
 useEffect(()=>{if(!enabled||!("__TAURI_INTERNALS__" in window)){setOpen(false);return;}let active=true;const poll=()=>!dismissed.current && !document.hidden && void invoke<{available:boolean}>("telemetry_survey").then(r=>{if(active&&!dismissed.current&&r?.available)setOpen(true);}).catch(()=>{});poll();const timer=setInterval(poll,15000);return()=>{active=false;clearInterval(timer);};},[enabled]);
 async function send(){setBusy(true);setError("");try{await invoke("telemetry_feedback",{reason:reason||"SKIP",interest:interest||"SKIP"});setOpen(false);setReason("");setInterest("");}catch{setError("Не удалось сохранить ответ. Можно закрыть окно.");}finally{setBusy(false);}}
 if(!open||!enabled)return null;
 return <div className="telemetry-survey-backdrop"><section ref={overlay} className="telemetry-survey" role="dialog" aria-modal="true" aria-labelledby="survey-title"><button className="icon-button survey-close" aria-label="Пропустить опрос" onClick={dismiss}><X size={20}/></button><span className="eyebrow">ОБРАТНАЯ СВЯЗЬ · НЕОБЯЗАТЕЛЬНО</span><h2 id="survey-title">Как прошла игра?</h2><p>Пара ответов поможет улучшить сервер. Опрос появляется не чаще раза в неделю.</p><div className="survey-choices">{[["GOOD","Всё хорошо"],["LAGS","Лаги или ошибки"],["CONFUSING","Не понял, что делать"],["NO_GOAL","Не нашёл занятие"],["OTHER","Другое"]].map(([code,label])=><button key={code} className={reason===code?"selected":""} onClick={()=>setReason(code)}>{label}</button>)}</div><h3>Что хочется продолжить?</h3><div className="survey-choices">{[["BUILD","Строительство"],["CITY","Город"],["INDUSTRY","Производство"],["TRADE","Торговлю"],["RAIL","Железные дороги"],["SOCIAL","Общение"]].map(([code,label])=><button key={code} className={interest===code?"selected":""} onClick={()=>setInterest(code)}>{label}</button>)}</div><small>Передаются только выбранные варианты. Без текста переписки, логов и координат. Диагностика отключается в настройках.</small>{error&&<p role="status">{error}</p>}<footer><button onClick={dismiss}>Пропустить</button><button onClick={()=>void send()} disabled={busy||!reason&&!interest}><Check size={16}/>{busy?"Сохраняем…":"Отправить"}</button></footer></section></div>;
}
