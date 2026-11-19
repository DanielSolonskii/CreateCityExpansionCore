import { ArrowLeft, ChevronRight } from "lucide-react";
import { useLayoutEffect, useRef, useState } from "react";
import { newsDate, type NewsPost } from "../services/newsGateway";
import { NewsContent } from "./NewsContent";
export function NewsPage({ items, loading, error, onRetry, onBack }: {
  items: NewsPost[]; loading: boolean; error: string; onRetry: () => void; onBack: () => void;
}) {
  const [updatesOnly, setUpdatesOnly] = useState(false);
  const [selected, setSelected] = useState<NewsPost | null>(null);
  const listPosition = useRef(0);
  const listButton = useRef<string | null>(null);
  useLayoutEffect(() => {
    const main = document.querySelector("main");
    if (!main) return;
    if (selected) {
      main.scrollTop = 0;
      main.querySelector<HTMLButtonElement>(".article-view .back-button")?.focus({ preventScroll: true });
    } else if (listButton.current) {
      main.scrollTop = listPosition.current;
      Array.from(main.querySelectorAll<HTMLButtonElement>("[data-post]"))
        .find(button => button.dataset.post === listButton.current)?.focus({ preventScroll: true });
    }
  }, [selected]);
  if (selected) return <section className="article-view">
    <button className="back-button" onClick={() => setSelected(null)}><ArrowLeft size={16} /> Назад к новостям</button>
    <div className="article-view__meta"><span>{selected.releaseVersion ? `ОБНОВЛЕНИЕ · ${selected.releaseVersion}` : "ОБЪЯВЛЕНИЕ"}</span><time>{newsDate(selected.publishedAt)}</time></div>
    <h1>{selected.title}</h1><NewsContent post={selected} />
  </section>;
  const filtered = items.filter(p => !updatesOnly || p.releaseVersion !== null);
  return <section className="news-page">
    <header className="news-page__header"><button className="back-button" onClick={onBack}><ArrowLeft size={16} /> На главную</button>
      <div><span className="eyebrow">Лента сервера</span><h1>Новости и обновления</h1></div></header>
    <div className="news-toolbar"><div className="news-categories">
      <button aria-pressed={!updatesOnly} className={!updatesOnly ? "is-active" : ""} onClick={() => setUpdatesOnly(false)}>Все новости</button>
      <button aria-pressed={updatesOnly} className={updatesOnly ? "is-active" : ""} onClick={() => setUpdatesOnly(true)}>Changelog</button>
    </div><button className="text-button" onClick={onRetry} disabled={loading}>Обновить новости</button></div>
    {loading && <p role="status">Загружаем публикации…</p>}
    {error && <p role="alert">{error} <button className="text-button" onClick={onRetry}>Повторить</button></p>}
    {!loading && !error && filtered.length === 0 && <p>Публикаций пока нет.</p>}
    <div className="news-list">{filtered.map(p => <button className="news-list-item news-list-item--copper" key={p.id} data-post={p.id} onClick={() => { listPosition.current = document.querySelector("main")?.scrollTop ?? 0; listButton.current = p.id; setSelected(p); }}>
      <div><span className="news-category">{p.releaseVersion ? `ОБНОВЛЕНИЕ · ${p.releaseVersion}` : "НОВОСТИ"}</span><h3>{p.title}</h3><p>{p.body.slice(0, 180)}</p></div>
      <div className="news-list-item__aside"><time>{newsDate(p.publishedAt)}</time><ChevronRight size={18} /></div></button>)}</div>
  </section>;
}
