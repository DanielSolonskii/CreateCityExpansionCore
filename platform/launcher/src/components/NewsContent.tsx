import type { NewsPost } from "../services/newsGateway";
export function NewsContent({ post }: { post: NewsPost }) {
  return <><div className="news-body">{post.body || "Подробности обновления появятся позже."}</div>
    {post.hasPhoto && <p className="news-note">Пост опубликован с фотографией. Фотография доступна в Telegram-боте.</p>}</>;
}
