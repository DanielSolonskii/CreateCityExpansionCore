import {invoke} from "@tauri-apps/api/core";
export interface ServerProfile {minecraftUuid:string|null;username:string;registeredAt:string;city:string;role:string;cityState:"CURRENT"|"STALE"|"UNKNOWN";cityObservedAt:string|null;playTicks:number|null;statsObservedAt:string|null;skinUrl:string|null}
export interface ProfileResult {profile:ServerProfile;skinData:string|null}
export async function loadProfile():Promise<ProfileResult>{
  const result=await invoke<ProfileResult>("launcher_profile");const p=result?.profile;
  if(!p||typeof p.username!=="string"||!Number.isFinite(Date.parse(p.registeredAt))||typeof p.city!=="string"||typeof p.role!=="string"||!["CURRENT","STALE","UNKNOWN"].includes(p.cityState)||p.playTicks!==null&&(!Number.isSafeInteger(p.playTicks)||p.playTicks<0))throw new Error("Некорректные данные профиля");
  if(result.skinData&&!/^data:image\/png;base64,[A-Za-z0-9+/=]+$/.test(result.skinData))throw new Error("Некорректный аватар");return result;
}
export function roleLabel(role:string){return ({OWNER:"Глава города",ADMIN:"Заместитель",ENGINEER:"Инженер",TREASURER:"Казначей",BUILDER:"Строитель",RESIDENT:"Житель"} as Record<string,string>)[role]??"Не указана";}
export function playtime(ticks:number|null){if(ticks===null)return "Ещё нет данных";const minutes=Math.floor(ticks/1200);return minutes>=60?`${Math.floor(minutes/60)} ч ${minutes%60} мин`:`${minutes} мин`;}
