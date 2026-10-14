import { invoke } from "@tauri-apps/api/core";
import { validateEndpoint, uuid } from "./accountApi";
export interface NewsPost { id: string; title: string; body: string; releaseVersion: string | null; publishedAt: string; hasPhoto: boolean }
export function parseNews(value: unknown): NewsPost[] {
  if (!Array.isArray(value) || value.length > 50) throw new Error("Некорректная лента новостей");
  const ids = new Set<string>();
  return value.map(p => {
    if (!p || !uuid(p.id) || ids.has(p.id) || typeof p.title !== "string" || p.title.length > 120 ||
        typeof p.body !== "string" || p.body.length > 4000 || typeof p.publishedAt !== "string" || !Number.isFinite(Date.parse(p.publishedAt)) ||
        typeof p.hasPhoto !== "boolean" || !(p.releaseVersion === null || typeof p.releaseVersion === "string" && /^\d{1,5}\.\d{1,5}(?:\.\d{1,5}){1,2}(?:-[A-Za-z0-9.-]{1,32})?$/.test(p.releaseVersion)))
      throw new Error("Некорректная лента новостей");
    ids.add(p.id); return p as NewsPost;
  });
}
export async function getNews(): Promise<NewsPost[]> {
  if ("__TAURI_INTERNALS__" in window) return parseNews(await invoke("launcher_news"));
  const base = validateEndpoint(import.meta.env.VITE_ACCOUNT_API_BASE_URL ?? "");
  const response = await fetch((import.meta.env.DEV ? "/account-api" : base) + "/api/launcher/news", {
    credentials: "omit", redirect: "error", cache: "no-store", signal: AbortSignal.timeout(10000),
  });
  if (!response.ok) throw new Error("Новости временно недоступны");
  const body = await response.text();
  if (body.length > 1024 * 1024) throw new Error("Некорректная лента новостей");
  return parseNews(JSON.parse(body));
}
const SEEN = "recreated.news.seen.v1";
export function unreadRelease(posts: NewsPost[]): NewsPost | null {
  const release = posts.find(p => p.releaseVersion !== null);
  if (!release) return null;
  try { return localStorage.getItem(SEEN) === release.id ? null : release; } catch { return release; }
}
export function markReleaseSeen(id: string) { try { localStorage.setItem(SEEN, id); } catch { /* News preference only; not credentials. */ } }
export const newsDate = (value: string) => new Date(value).toLocaleDateString("ru-RU", { day: "numeric", month: "long" });
