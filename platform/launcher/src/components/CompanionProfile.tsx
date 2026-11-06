import { ProfileStudio } from "./ProfileStudio";
import { Building2, Check, ChevronRight, Clock3, LogOut, Pencil, ShieldCheck } from "lucide-react";
import type { LauncherProfile } from "../types/launcher";
import { PlayerAvatar } from "./PlayerAvatar";

export function CompanionProfile({profile,registeredAt,compact=false,onOpen,onEdit,onLogout}:{
  profile:LauncherProfile;registeredAt?:string;compact?:boolean;onOpen?:()=>void;onEdit?:()=>void;onLogout?:()=>void;
}) {
  return <section className={`companion-profile ${compact ? "is-compact" : ""}`} aria-label="Профиль игрока">
    <header className="companion-profile__identity">
      <div className="companion-profile__avatar"><PlayerAvatar skin={profile.skinData} initials={profile.initials}/></div>
      <div><small>Профиль игрока</small><h2>{profile.displayName}</h2><p>{profile.subtitle}</p></div>
    </header>
    <div className="companion-profile__verified"><ShieldCheck size={16}/><span>Аккаунт подтверждён</span><Check size={14}/></div>
    <div className="companion-metrics">
      <div><Clock3 size={19}/><small>Время в игре</small><strong>{profile.playtime}</strong></div>
      <div><Building2 size={19}/><small>Город</small><strong>{profile.city}</strong></div>
    </div>
    {!compact&&<>
      <dl className="companion-profile__details"><div><dt>Роль в городе</dt><dd>{profile.role}</dd></div><div><dt>Дата регистрации</dt><dd>{profile.joinedAt}</dd></div></dl>
      <ProfileStudio key={profile.accountId} account={profile.accountId} registeredAt={registeredAt}/>
      <p className="companion-sync">{profile.syncLabel}</p>
      <div className="companion-profile__actions"><button onClick={onEdit}><Pencil size={16}/>Оформление профиля</button><button onClick={onLogout}><LogOut size={16}/>Выйти из аккаунта</button></div>
      <details className="companion-account-details"><summary>Данные аккаунта</summary><p>ID платформы</p><code>{profile.accountId}</code><small>Отображаемое имя меняется локально. Город, роль и статистика поступают с сервера.</small></details>
    </>}
    {compact&&<button className="companion-profile__open" onClick={onOpen}>Открыть профиль<ChevronRight size={17}/></button>}
  </section>;
}
