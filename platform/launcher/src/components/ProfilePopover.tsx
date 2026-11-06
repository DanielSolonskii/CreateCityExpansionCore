import {PlayerAvatar} from "./PlayerAvatar";
import { Building2, Check, Clock3, Copy, LogOut, Pencil, X } from "lucide-react";
import { useState } from "react";
import { useOverlay } from "./useOverlay";
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
  const [copyError, setCopyError] = useState("");
  const overlay = useOverlay<HTMLElement>(open, onClose);

  async function copyAccountId() {
    setCopied(false); setCopyError("");
    try {
      await navigator.clipboard.writeText(profile.accountId);
      setCopied(true);
      window.setTimeout(() => setCopied(false), 1400);
    } catch { setCopyError("Не удалось скопировать ID. Выделите его вручную."); }
  }

  return (
    <>
      <button
        className={`profile-dismiss ${open ? "is-open" : ""}`}
        onClick={onClose}
        aria-label="Закрыть профиль"
        tabIndex={open ? 0 : -1}
      />
      <section ref={overlay} role="dialog" aria-modal="true" aria-label="Профиль игрока" className={`profile-popover ${open ? "is-open" : ""}`} aria-hidden={!open}>
        <div className="profile-popover__hero">
          <div className="profile-popover__gear" aria-hidden="true" />
          <button className="profile-close" onClick={onClose} aria-label="Закрыть"><X size={17} /></button>
          <div className="profile-avatar-large"><PlayerAvatar skin={profile.skinData} initials={profile.initials}/>{profile.online&&<span className="online-dot"/>}</div>
          <div className="profile-identity">
            <span className="eyebrow">ПРОФИЛЬ ИГРОКА</span>
            <h2>{profile.displayName}</h2><small>{profile.subtitle}</small>
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
          <button onClick={onEdit}><Pencil size={15} /> Оформление профиля</button>
        </div>
        <details className="profile-technical"><summary>Данные аккаунта</summary>
          <button className="account-id" aria-label="Скопировать ID аккаунта" onClick={() => void copyAccountId()}>{profile.accountId} {copied ? <Check size={14} /> : <Copy size={14} />}</button>
          {copied && <small role="status">ID скопирован</small>}{copyError && <small role="alert">{copyError}</small>}
        </details>
        <button className="profile-logout" onClick={onLogout}><LogOut size={15} /> Выйти из профиля</button>
      </section>
    </>
  );
}
