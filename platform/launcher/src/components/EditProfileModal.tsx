import { AtSign, Building2, Check, Pencil, UserRound, X } from "lucide-react";
import { FormEvent, useEffect, useMemo, useState } from "react";
import { authGateway, type ProfileSettings } from "../services/authGateway";

export function EditProfileModal({
  open,
  username,
  initialSettings,
  onClose,
  onSaved,
}: {
  open: boolean;
  username: string;
  initialSettings: ProfileSettings;
  onClose: () => void;
  onSaved: (settings: ProfileSettings) => void;
}) {
  const [displayName, setDisplayName] = useState(initialSettings.displayName);
  const [telegram, setTelegram] = useState(initialSettings.telegram);
  const [city, setCity] = useState(initialSettings.city);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    if (!open) return;
    setDisplayName(initialSettings.displayName);
    setTelegram(initialSettings.telegram);
    setCity(initialSettings.city);
    setError("");
  }, [open, initialSettings]);

  const initials = useMemo(() => displayName.trim().slice(0, 2).toUpperCase() || "?", [displayName]);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!/^[a-zA-Z0-9_А-Яа-яЁё -]{3,20}$/.test(displayName.trim())) {
      setError("Ник должен содержать от 3 до 20 символов");
      return;
    }
    setSaving(true);
    setError("");
    const saved = await authGateway.updateProfileSettings(username, { displayName, telegram, city });
    setSaving(false);
    onSaved(saved);
  }

  return (
    <div className={`login-layer ${open ? "is-open" : ""}`} aria-hidden={!open}>
      <button className="login-backdrop" onClick={onClose} aria-label="Закрыть редактор профиля" tabIndex={open ? 0 : -1} />
      <section className="login-modal edit-profile-modal" role="dialog" aria-modal="true" aria-label="Изменение профиля">
        <button className="login-close" onClick={onClose} aria-label="Закрыть"><X size={18} /></button>
        <div className="edit-profile-heading">
          <div className="edit-avatar">{initials}<span /></div>
          <div><span className="eyebrow">НАСТРОЙКА ПРОФИЛЯ</span><h2>Имя в лаунчере</h2><p>Локальные настройки отображения. Telegram binding и город на сервере не меняются.</p></div>
        </div>
        <form onSubmit={submit}>
          <label>
            <span>ОТОБРАЖАЕМОЕ ИМЯ</span>
            <div><UserRound size={17} /><input autoFocus={open} value={displayName} onChange={(event) => setDisplayName(event.target.value)} placeholder="Игровой ник" /><small>{displayName.length}/20</small></div>
          </label>
          <p className="profile-official-note">Город, роль и привязка Telegram подтверждаются сервером. Здесь меняется только локальное отображаемое имя.</p>
          <div className={`login-error ${error ? "is-visible" : ""}`}>{error || "Изменения сохранятся на этом устройстве"}</div>
          <button className="login-submit" disabled={saving || !displayName}>
            {saving ? <span className="login-spinner" /> : <Check size={18} />}
            {saving ? "Сохраняем…" : "Сохранить изменения"}
          </button>
        </form>
        <div className="edit-profile-id"><Pencil size={12} /> ID аккаунта остаётся неизменным: <strong>{username}</strong></div>
      </section>
    </div>
  );
}
