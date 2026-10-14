import { ArrowLeft, ChevronRight } from "lucide-react";
import { useState } from "react";
import { newsDate, type NewsPost } from "../services/newsGateway";
import { NewsContent } from "./NewsContent";
export function NewsPage({ items, loading, error, onRetry, onBack }: {
  items: NewsPost[]; loading: boolean; error: string; onRetry: () => void; onBack: () => void;
}) {
  const [updatesOnly, setUpdatesOnly] = useState(false);
  const [selected, setSelected] = useState<NewsPost | null>(null);
  if (selected) return <section className="article-view">
    <button className="back-button" onClick={() => setSelected(null)}><ArrowLeft size={16} /> Назад к новостям</button>
    <div className="article-view__meta"><span>{selected.releaseVersion ? `ОБНОВЛЕНИЕ · ${selected.releaseVersion}` : "ОБЪЯВЛЕНИЕ"}</span><time>{newsDate(selected.publishedAt)}</time></div>
    <h1>{selected.title}</h1><NewsContent post={selected} />
  </section>;
  const filtered = items.filter(p => !updatesOnly || p.releaseVersion !== null);
  return <section className="news-page">
    <header className="news-page__header"><button className="back-button" onClick={onBack}><ArrowLeft size={16} /> На главную</button>
      <div><span className="eyebrow">RE:CREATED · ИЗ TELEGRAM</span><h1>Новости и обновления</h1></div><p>Подтверждённые публикации команды сервера.</p></header>
    <div className="news-toolbar"><div className="news-categories">
      <button className={!updatesOnly ? "is-active" : ""} onClick={() => setUpdatesOnly(false)}>Все новости</button>
      <button className={updatesOnly ? "is-active" : ""} onClick={() => setUpdatesOnly(true)}>Changelog</button>
    </div><button className="text-button" onClick={onRetry} disabled={loading}>Обновить новости</button></div>
    {loading && <p role="status">Загружаем публикации…</p>}
    {error && <p role="alert">{error} <button className="text-button" onClick={onRetry}>Повторить</button></p>}
    {!loading && !error && filtered.length === 0 && <p>Публикаций пока нет.</p>}
    <div className="news-list">{filtered.map(p => <button className="news-list-item news-list-item--copper" key={p.id} onClick={() => setSelected(p)}>
      <div><span className="news-category">{p.releaseVersion ? `ОБНОВЛЕНИЕ · ${p.releaseVersion}` : "НОВОСТИ"}</span><h3>{p.title}</h3><p>{p.body.slice(0, 180)}</p></div>
      <div className="news-list-item__aside"><time>{newsDate(p.publishedAt)}</time><ChevronRight size={18} /></div></button>)}</div>
  </section>;
}
