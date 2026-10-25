import {PlayerAvatar} from "./PlayerAvatar";
import { Building2, Check, Clock3, Copy, LogOut, Pencil, UserRoundCog, X } from "lucide-react";
import { useState } from "react";
import type { LauncherProfile } from "../types/launcher";

export function ProfilePopover({
  profile,
  open,
  onClose,
  onLogout,
  onEdit,
}: {
  profile: LauncherProfile;
  open: boolean;
  onClose: () => void;
  onLogout: () => void;
  onEdit: () => void;
}) {
  const [copied, setCopied] = useState(false);

  function copyAccountId() {
    void navigator.clipboard?.writeText(profile.accountId);
    setCopied(true);
    window.setTimeout(() => setCopied(false), 1400);
  }

  return (
    <>
      <button
        className={`profile-dismiss ${open ? "is-open" : ""}`}
        onClick={onClose}
        aria-label="Закрыть профиль"
        tabIndex={open ? 0 : -1}
      />
      <section className={`profile-popover ${open ? "is-open" : ""}`} aria-hidden={!open}>
        <div className="profile-popover__hero">
          <div className="profile-popover__gear" aria-hidden="true" />
          <button className="profile-close" onClick={onClose} aria-label="Закрыть"><X size={17} /></button>
          <div className="profile-avatar-large"><PlayerAvatar skin={profile.skinData} initials={profile.initials}/>{profile.online&&<span className="online-dot"/>}</div>
          <div className="profile-identity">
            <span className="eyebrow">ПРОФИЛЬ ИГРОКА</span>
            <h2>{profile.displayName}</h2><small>{profile.subtitle}</small>
            <button className="account-id" onClick={copyAccountId}>
              {profile.accountId} {copied ? <Check size={12} /> : <Copy size={12} />}
            </button>
          </div>
        </div>

        <div className="profile-auth-state">
          <span><Check size={13} /></span>
          <div><strong>{profile.verified ? "Аккаунт подтверждён" : "Локальный профиль"}</strong><small>{profile.syncLabel ?? "Данные игрового сервера"}</small></div>
        </div>

        <div className="profile-stats">
          <div><Clock3 size={15} /><span><small>В ИГРЕ</small><strong>{profile.playtime}</strong></span></div>
          <div><Building2 size={15} /><span><small>ГОРОД</small><strong>{profile.city}</strong></span></div>
        </div>

        <div className="profile-details">
          <div><span>Роль в городе</span><strong>{profile.role}</strong></div>
          <div><span>Дата регистрации</span><strong>{profile.joinedAt}</strong></div>
        </div>

        <div className="profile-actions">
          <button onClick={onEdit}><Pencil size={15} /> Изменить профиль</button>
          <button onClick={onLogout}><UserRoundCog size={15} /> Сменить аккаунт</button>
        </div>
        <button className="profile-logout" onClick={onLogout}><LogOut size={15} /> Выйти из профиля</button>
      </section>
    </>
  );
}
