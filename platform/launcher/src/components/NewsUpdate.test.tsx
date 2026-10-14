import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import { NewsPage } from "./NewsPage";
import { ChangelogModal } from "./ChangelogModal";
import { GameUpdatePanel } from "./GameUpdatePanel";
import { idleUpdate, stopped } from "../services/gameGateway";
const post = { id: "00000000-0000-4000-8000-000000000001", title: "Обновление 1.0.18", body: "<img src=x onerror=alert(1)>\nИсправления", releaseVersion: "1.0.18", publishedAt: "2026-10-12T00:00:00Z", hasPhoto: false };
afterEach(cleanup);
it("renders actual news as text, filters changelog and handles failures", () => {
  const retry=vi.fn();render(<NewsPage items={[post,{...post,id:"two",title:"Объявление",releaseVersion:null}]} loading={false} error="" onBack={vi.fn()} onRetry={retry} />);
  fireEvent.click(screen.getByText("Changelog"));expect(screen.queryByText("Объявление")).toBeNull();
  fireEvent.click(screen.getByText("Обновление 1.0.18"));expect(document.querySelector("img")).toBeNull();
  expect(screen.getByText(/<img src=x/)).toBeTruthy();
});
it("closes changelog using button or Escape", () => {
  HTMLDialogElement.prototype.showModal=function(){this.open=true;};HTMLDialogElement.prototype.close=function(){this.open=false;};
  const close=vi.fn();render(<ChangelogModal post={post} onClose={close} />);
  fireEvent.click(screen.getByText("Понятно, закрыть"));expect(close).toHaveBeenCalledTimes(1);
  fireEvent(screen.getByRole("dialog"),new Event("cancel",{bubbles:true,cancelable:true}));expect(close).toHaveBeenCalledTimes(2);
});
it("displays real byte/file counts and separate stages without false whole-update percentage", () => {
  const {rerender}=render(<GameUpdatePanel progress={{...idleUpdate,phase:"DOWNLOADING",downloadedBytes:1048576,totalBytes:2097152,completedFiles:1,changedFiles:2}} game={stopped} onRetry={vi.fn()} onClose={vi.fn()} />);
  expect(screen.getByText(/1.0 МБ \/ 2.0 МБ/)).toBeTruthy();expect(screen.getByRole("progressbar").getAttribute("aria-valuenow")).toBe("50");
  rerender(<GameUpdatePanel progress={{...idleUpdate,phase:"READY",version:"alpha-0.1.17"}} game={stopped} onRetry={vi.fn()} onClose={vi.fn()} />);
  expect(screen.getByText(/Вы используете актуальную сборку/)).toBeTruthy();
});
it("offers retry on failure and never applies while game runs", () => {
  const retry=vi.fn();const {rerender}=render(<GameUpdatePanel progress={{...idleUpdate,phase:"CHECKING"}} game={{...stopped,state:"FAILED",message:"Ошибка SHA-256"}} onRetry={retry} onClose={vi.fn()} />);
  fireEvent.click(screen.getByText("Повторить проверку сборки"));expect(retry).toHaveBeenCalledOnce();
  rerender(<GameUpdatePanel progress={idleUpdate} game={{...stopped,state:"RUNNING"}} onRetry={retry} onClose={vi.fn()} />);
  expect(screen.queryByText("Повторить проверку сборки")).toBeNull();expect(screen.getByText(/Обновление файлов заблокировано/)).toBeTruthy();
});
