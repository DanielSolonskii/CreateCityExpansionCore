import { useEffect, useState } from "react";
const colours = ["#b58b60", "#719b87", "#708eb5", "#a783af"];
export function ProfileStudio({ account, registeredAt }: { account: string; registeredAt?: string }) {
  const key = `recreated.profile-look.v1:${account}`;
  const [look, setLook] = useState({ colour: colours[0], bio: "", interests: "", minimal: false });
  const [editing, setEditing] = useState(false);
  const [message, setMessage] = useState("");
  useEffect(() => {
    try { const p = JSON.parse(localStorage.getItem(key) || "{}"); setLook({ colour: colours.includes(p.colour) ? p.colour : colours[0], bio: typeof p.bio === "string" ? p.bio.slice(0,120) : "", interests: typeof p.interests === "string" ? p.interests.slice(0,80) : "", minimal: p.minimal === true }); }
    catch { setMessage("Не удалось загрузить оформление"); }
    setEditing(false);
  }, [key]);
  const days = registeredAt ? Math.floor((Date.now() - Date.parse(registeredAt)) / 86400000) : NaN;
  function save() {
    try { localStorage.setItem(key, JSON.stringify(look)); window.dispatchEvent(new Event("profile-appearance")); setMessage("Сохранено на этом устройстве"); setEditing(false); }
    catch { setMessage("Не удалось сохранить. Проверьте доступ к хранилищу"); }
  }
  return <div className="profile-studio" style={{ borderTop: `3px solid ${look.colour}` }}>
    {Number.isFinite(days) && days >= 0 && <p>В RE:CREATED уже {days} дн.</p>}
    {look.bio && <p>{look.bio}</p>}{look.interests && <small>Интересы: {look.interests}</small>}
    <button className="text-button" onClick={() => setEditing(!editing)} aria-expanded={editing}>Оформление и интересы</button>
    {editing && <div className="profile-studio-fields">
      <label>Цвет обложки<select value={look.colour} onChange={e => setLook({ ...look, colour: e.target.value })}>{colours.map((c,i) => <option key={c} value={c}>{["Медь","Шалфей","Сталь","Сирень"][i]}</option>)}</select></label>
      <label>О себе<input maxLength={120} value={look.bio} onChange={e => setLook({ ...look, bio: e.target.value })} placeholder="Что любите строить?" /></label>
      <label>Интересы<input maxLength={80} value={look.interests} onChange={e => setLook({ ...look, interests: e.target.value })} placeholder="Архитектура, поезда, производство" /></label>
      <label><input type="checkbox" checked={look.minimal} onChange={e => setLook({ ...look, minimal: e.target.checked })} /> Минимальные анимации</label>
      <small>Оформление видно только вам. Город и роль задаются сервером.</small>
      <button className="text-button" onClick={save}>Сохранить оформление</button>
    </div>}<small role="status">{message}</small>
  </div>;
}
