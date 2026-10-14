import { ArrowUpRight } from "lucide-react";
import { newsDate, type NewsPost } from "../services/newsGateway";
export function NewsRail({ items, loading, error, onOpenNews }: { items: NewsPost[]; loading: boolean; error: string; onOpenNews: () => void }) {
  return <section className="news-section"><div className="section-heading"><div><span className="rule" /><span className="eyebrow">СВОДКИ И ОБНОВЛЕНИЯ</span></div>
    <button className="text-button" onClick={onOpenNews}>Все новости <ArrowUpRight size={14} /></button></div>
    {loading && <p role="status">Загружаем новости…</p>}{error && <p role="status">{error}</p>}
    {!loading && !error && !items.length && <p>Здесь появятся публикации команды сервера.</p>}
    <div className="news-grid">{items.slice(0, 3).map(p => <button className="news-card news-card--copper" key={p.id} onClick={onOpenNews}>
      <div className="news-card__meta"><span>{p.releaseVersion ? `ОБНОВЛЕНИЕ · ${p.releaseVersion}` : "НОВОСТИ"}</span><time>{newsDate(p.publishedAt)}</time></div>
      <h3>{p.title}</h3><p>{p.body.slice(0, 150)}</p><ArrowUpRight size={17} /></button>)}</div>
  </section>;
}
