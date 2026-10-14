import { Activity, Radio, Users } from "lucide-react";
import type { ServerStatus } from "../types/launcher";

export function ServerCard({ status }: { status: ServerStatus }) {
  return (
    <section className="server-card glass-panel" aria-label="Статус сервера">
      <div className="server-card__top">
        <div className="status-heading">
          <span className={`status-light status-light--${status.state}`} />
          <div>
            <span className="eyebrow">СТАТУС СЕТИ</span>
            <strong>{status.label}</strong>
          </div>
        </div>
        <span className="ping"><Radio size={13} /> {status.ping === null ? "—" : `${status.ping} мс`}</span>
      </div>
      <div className="server-card__message"><Activity size={15} /> {status.message}</div>
      <div className="server-card__players">
        <Users size={16} />
        <span>{status.playersOnline === null || status.playersMax === null ? "Игроки: нет данных" : <><strong>{status.playersOnline}</strong> / {status.playersMax} игроков</>}</span>
        <div className="capacity"><span style={{ width: `${status.playersOnline !== null && status.playersMax ? Math.min(100, Math.max(0, status.playersOnline / status.playersMax * 100)) : 0}%` }} /></div>
      </div>
    </section>
  );
}
