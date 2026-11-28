import { Check, UserRound, X } from "lucide-react";
import { FormEvent, useEffect, useMemo, useState } from "react";
import { authGateway, type ProfileSettings } from "../services/authGateway";
import { coverColours, readLook } from "../services/profileLook";
import { useOverlay } from "./useOverlay";

export function EditProfileModal({
  open,
  username,
  account,
  initialSettings,
  onClose,
  onSaved,
}: {
  open: boolean;
  username: string;
  account: string;
  initialSettings: ProfileSettings;
  onClose: () => void;
  onSaved: (settings: ProfileSettings) => void;
}) {
  const [displayName, setDisplayName] = useState(initialSettings.displayName);
  const overlay = useOverlay<HTMLDivElement>(open, onClose);
  const [look, setLook] = useState(() => readLook(account));
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    if (!open) return;
    setDisplayName(initialSettings.displayName);
    setLook(readLook(account));
    setError("");
  }, [open, initialSettings, account]);

  const initials = useMemo(() => displayName.trim().slice(0, 2).toUpperCase() || "?", [displayName]);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!/^[a-zA-Z0-9_А-Яа-яЁё -]{3,20}$/.test(displayName.trim())) {
      setError("Ник должен содержать от 3 до 20 символов");
      return;
    }
    setSaving(true);
    setError("");
    try {
      const saved = await authGateway.updateProfileSettings(username, { ...initialSettings, displayName });
      localStorage.setItem("recreated.profile-look.v1:" + account, JSON.stringify(look));
      window.dispatchEvent(new Event("profile-appearance"));
      onSaved(saved);
    } catch {
      setError("Не удалось сохранить все изменения. Проверьте доступ к хранилищу и повторите.");
    } finally { setSaving(false); }
  }

  return (
    <div ref={overlay} className={`login-layer ${open ? "is-open" : ""}`} aria-hidden={!open}>
      <button className="login-backdrop" onClick={onClose} aria-label="Закрыть редактор профиля" tabIndex={open ? 0 : -1} />
      <section className="login-modal edit-profile-modal" role="dialog" aria-modal="true" aria-label="Изменение профиля">
        <button className="login-close" onClick={onClose} aria-label="Закрыть"><X size={18} /></button>
        <div className="edit-profile-heading">
          <div className="edit-avatar">{initials}<span /></div>
          <div><h2>Изменить профиль</h2><p>Оформление на этом устройстве</p></div>
        </div>
        <form onSubmit={submit}>
          <label>
            <span>ОТОБРАЖАЕМОЕ ИМЯ</span>
            <div><UserRound size={17} /><input autoFocus={open} value={displayName} onChange={(event) => setDisplayName(event.target.value)} placeholder="Игровой ник" /><small>{displayName.length}/20</small></div>
          </label>
          <div className="profile-studio-fields">
            <label>Цвет обложки<select value={look.colour} onChange={e => setLook({ ...look, colour: e.target.value })}>{coverColours.map((colour, i) => <option key={colour} value={colour}>{["Медь", "Шалфей", "Сталь", "Сирень"][i]}</option>)}</select></label>
            <label>О себе<input maxLength={120} value={look.bio} onChange={e => setLook({ ...look, bio: e.target.value })}/></label>
            <label>Интересы<input maxLength={80} value={look.interests} onChange={e => setLook({ ...look, interests: e.target.value })}/></label>
          </div>
          <div className={`login-error ${error ? "is-visible" : ""}`}>{error || "Изменения сохранятся на этом устройстве"}</div>
          <button className="login-submit" disabled={saving || !displayName}>
            {saving ? <span className="login-spinner" /> : <Check size={18} />}
            {saving ? "Сохраняем…" : "Сохранить изменения"}
          </button>
        </form>

      </section>
    </div>
  );
}
