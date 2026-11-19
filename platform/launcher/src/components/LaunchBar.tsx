import { Download, LogIn, Play, ShieldCheck } from "lucide-react";
import type { ReactNode } from "react";
import { updateLabel, updatePercent, type GameStatus, type UpdateProgress } from "../services/gameGateway";
import type { GameAction } from "../services/useGameJob";
import { mb } from "./GameUpdatePanel";

interface Props {
  authenticated: boolean;
  game: GameStatus;
  update: UpdateProgress;
  required: boolean;
  lastAction: GameAction;
  onAction: (action: GameAction) => void;
  onDetails: () => void;
  children: ReactNode;
}

export function LaunchBar({ authenticated, game, update, required, lastAction, onAction, onDetails, children }: Props) {
  const working = game.state === "STARTING";
  const running = game.state === "RUNNING";
  const failed = game.state === "FAILED" || update.phase === "FAILED";
  const check = required || (failed && lastAction === "check");
  const showGameButton = !required || working || running;
  const stage = update.phase === "READY" && working
    ? lastAction === "check" ? "Завершаем проверку" : "Подготовка Minecraft"
    : updateLabel(update);
  let title = update.phase === "READY" ? "Сборка готова" : "Сборка ещё не проверена";
  let detail = update.version ?? "Проверка файлов начнётся при запуске.";
  if (!authenticated) { title = "Первый вход"; detail = "Подтвердите устройство через Telegram."; }
  if (required) { title = "Требуется обновление лаунчера"; detail = "Подготовка файлов игры доступна отдельно."; }
  if (failed) { title = check ? "Проверка не завершена" : "Запуск не завершён"; detail = game.state === "FAILED" ? game.message : update.message; }
  if (running) { title = "Minecraft запущен"; detail = "Лаунчер можно скрыть в трей."; }
  if (working) {
    title = stage;
    detail = update.phase === "DOWNLOADING" ? mb(update.downloadedBytes) + " / " + mb(update.totalBytes)
      : update.phase === "CHECKING" && update.totalFiles > 0 ? update.checkedFiles + " / " + update.totalFiles + " файлов"
      : update.message || "Можно свернуть окно.";
  }
  const measurable = update.phase === "DOWNLOADING" && update.totalBytes > 0 || update.phase === "CHECKING" && update.totalFiles > 0;
  const label = !authenticated ? "Войти" : running ? "Игра запущена" : working ? stage : failed ? check ? "Повторить проверку" : "Повторить запуск" : "Играть";
  return <footer className="launchbar community-launch">
    <ShieldCheck className="job-seal" aria-hidden="true" />
    <div className="job-copy"><strong role="status">{title}</strong><small title={detail}>{detail}</small>
      {working && <progress aria-label="Прогресс текущего этапа" max={100} value={measurable ? updatePercent(update) : undefined} />}
    </div>
    <button className="job-details" onClick={onDetails}>Подробности</button>
    {required && authenticated && !working && !running && update.phase !== "READY" &&
      <button className="job-details" onClick={() => onAction("check")}>Проверить сборку</button>}
    {children}
    {showGameButton && <button className="job-primary" disabled={working || running} onClick={() => onAction(check ? "check" : "launch")}>
      {!authenticated ? <LogIn size={18}/> : working ? <Download size={18}/> : <Play size={16}/>} {label}
    </button>}
  </footer>;
}
