import { StrictMode } from "react";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { idleUpdate, stopped } from "../services/gameGateway";
import { LaunchBar } from "./LaunchBar";
import { GameUpdatePanel } from "./GameUpdatePanel";
import { NewsRail } from "./NewsRail";
import { EditProfileModal } from "./EditProfileModal";
import { ProfileStudio } from "./ProfileStudio";
import { TelemetrySurvey } from "./TelemetrySurvey";

const { invoke } = vi.hoisted(() => ({ invoke: vi.fn() }));
vi.mock("@tauri-apps/api/core", async original => ({ ...await original<typeof import("@tauri-apps/api/core")>(), invoke }));
beforeEach(() => localStorage.clear());
afterEach(() => { cleanup(); vi.resetAllMocks(); Reflect.deleteProperty(window, "__TAURI_INTERNALS__"); });

it("retries a failed repair as a check and a failed launch as a launch", () => {
  const run = vi.fn();
  const props = { authenticated: true, game: { ...stopped, state: "FAILED" as const, message: "Нет связи" },
    update: idleUpdate, required: false, onDetails: vi.fn(), onAction: run, children: null };
  const view = render(<LaunchBar {...props} lastAction="check"/>);
  fireEvent.click(screen.getByRole("button", { name: "Повторить проверку" }));
  expect(run).toHaveBeenLastCalledWith("check");
  view.rerender(<LaunchBar {...props} lastAction="launch"/>);
  fireEvent.click(screen.getByRole("button", { name: "Повторить запуск" }));
  expect(run).toHaveBeenLastCalledWith("launch");
});

it("keeps idle details honest and restores focus after a StrictMode mount", async () => {
  const opener = document.createElement("button");
  document.body.append(opener); opener.focus();
  const view = render(<StrictMode><GameUpdatePanel game={stopped} progress={idleUpdate} onRetry={vi.fn()} onClose={vi.fn()}/></StrictMode>);
  expect(screen.queryByRole("progressbar")).toBeNull();
  expect(screen.queryByText("Получаем список файлов…")).toBeNull();
  view.unmount();
  await waitFor(() => expect(document.activeElement).toBe(opener));
  opener.remove();
});

it("opens the selected home story and keeps the full-feed action separate", () => {
  const post = { id:"two", title:"Новый вокзал", body:"Новости города", releaseVersion:null, hasPhoto:false, publishedAt:"2026-10-12T00:00:00Z" };
  const story = vi.fn(), feed = vi.fn();
  render(<NewsRail items={[post]} loading={false} error="" onOpenNews={feed} onOpenPost={story}/>);
  fireEvent.click(screen.getByRole("button",{ name:/Новый вокзал/ }));
  expect(story).toHaveBeenCalledWith(post); expect(feed).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole("button",{ name:/Все новости/ }));
  expect(feed).toHaveBeenCalledOnce();
});

it("saves the existing local profile name, cover and bio from one editor", async () => {
  const saved = vi.fn();
  localStorage.setItem("recreated.profile-look.v1:account", JSON.stringify({colour:"#719b87",bio:"Старый текст",minimal:true}));
  render(<><ProfileStudio account="account"/><EditProfileModal account="account" open username="Engineer"
    initialSettings={{displayName:"Engineer",telegram:"@",city:""}} onSaved={saved} onClose={vi.fn()}/></>);
  expect((screen.getByLabelText("Цвет обложки") as HTMLSelectElement).value).toBe("#719b87");
  fireEvent.change(screen.getByLabelText("О себе"), {target:{value:"Строю станции"}});
  fireEvent.change(screen.getByPlaceholderText("Игровой ник"), {target:{value:"Машинист"}});
  fireEvent.click(screen.getByRole("button",{name:"Сохранить изменения"}));
  await waitFor(() => expect(saved).toHaveBeenCalledWith(expect.objectContaining({displayName:"Машинист"})));
  expect(screen.getByText("Строю станции", { selector:"p" })).toBeTruthy();
  expect(JSON.parse(localStorage.getItem("recreated.profile-look.v1:account")!).minimal).toBe(true);
});

it("dismisses the survey even if the native feedback command fails", async () => {
  Object.defineProperty(window,"__TAURI_INTERNALS__",{value:{},configurable:true});
  invoke.mockImplementation(async command => {
    if (command === "telemetry_survey") return {available:true};
    throw Error("offline");
  });
  render(<TelemetrySurvey enabled/>);
  fireEvent.click(await screen.findByRole("button",{name:"Пропустить опрос"}));
  expect(screen.queryByRole("dialog")).toBeNull();
});
