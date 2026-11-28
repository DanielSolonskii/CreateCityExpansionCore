import { useEffect, useRef } from "react";
import { newsDate, type NewsPost } from "../services/newsGateway";
import { NewsContent } from "./NewsContent";
export function ChangelogModal({ post, onClose }: { post: NewsPost | null; onClose: () => void }) {
  const dialog = useRef<HTMLDialogElement>(null);
  useEffect(() => { if (post && !dialog.current?.open) dialog.current?.showModal(); else if (!post && dialog.current?.open) dialog.current.close(); }, [post]);
  return <dialog className="changelog-dialog" ref={dialog} onCancel={e => { e.preventDefault(); onClose(); }} aria-labelledby="changelog-title">
    {post && <><span className="eyebrow">{post.releaseVersion ? "Обновление · " + post.releaseVersion : "Новости сервера"} · {newsDate(post.publishedAt)}</span><h2 id="changelog-title">{post.title}</h2>
      <NewsContent post={post} /><button className="news-primary" autoFocus onClick={onClose}>Понятно, закрыть</button></>}
  </dialog>;
}
