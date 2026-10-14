import { beforeEach, describe, expect, it, vi } from "vitest";
import { parseNews, unreadRelease, markReleaseSeen, getNews, type NewsPost } from "./newsGateway";
const { invoke } = vi.hoisted(() => ({ invoke: vi.fn() }));
vi.mock("@tauri-apps/api/core", () => ({ invoke }));
export const post: NewsPost = { id: "00000000-0000-4000-8000-000000000001", title: "Обновление 1.0.18", body: "Изменения", releaseVersion: "1.0.18", publishedAt: "2026-10-12T00:00:00Z", hasPhoto: false };
beforeEach(() => { localStorage.clear(); vi.resetAllMocks(); delete (window as unknown as Record<string,unknown>).__TAURI_INTERNALS__; });
describe("Launcher news", () => {
  it("validates feed, versions, duplicate IDs and sizes", () => {
    expect(parseNews([post])).toEqual([post]);
    for(const v of [null, [post,post], [{...post,releaseVersion:"http://evil"}], [{...post,body:"x".repeat(4001)}], [{...post,publishedAt:"bad"}]])expect(()=>parseNews(v)).toThrow();
  });
  it("marks only dismissed release seen and shows a later post", () => {
    expect(unreadRelease([post])).toEqual(post);markReleaseSeen(post.id);expect(unreadRelease([post])).toBeNull();
    expect(unreadRelease([{...post,id:"00000000-0000-4000-8000-000000000002"},post])).not.toBeNull();
    expect(unreadRelease([{...post,releaseVersion:null}])).toBeNull();
  });
  it("uses fixed native read-only command without credentials", async () => {
    (window as unknown as Record<string,unknown>).__TAURI_INTERNALS__={};invoke.mockResolvedValue([post]);
    expect(await getNews()).toEqual([post]);expect(invoke).toHaveBeenCalledWith("launcher_news");
    invoke.mockRejectedValue(new Error("offline"));await expect(getNews()).rejects.toThrow();
  });
});
