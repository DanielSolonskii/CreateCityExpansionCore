import { useEffect, useRef, useState } from "react";
import { useOverlay } from "./useOverlay";
import type { GameStatus, UpdateProgress } from "../services/gameGateway";
const steps = ["Проверка сборки", "Загрузка", "Проверка файлов", "Установка", "Готово"];
const phases = ["CHECKING", "DOWNLOADING", "VERIFYING", "APPLYING", "READY"];
export const mb = (bytes: number) => `${(Math.max(0, bytes) / 1048576).toFixed(1)} МБ`;
export function GameUpdatePanel({ progress: p, game, onRetry, onClose, retryLabel = "Повторить проверку сборки" }: {
  progress: UpdateProgress; game: GameStatus; retryLabel?: string; onRetry: () => void; onClose: () => void;
}) {
  const overlay = useOverlay<HTMLElement>(true, onClose);
  const sample = useRef({ bytes: p.downloadedBytes, at: performance.now() });
  const [speed, setSpeed] = useState(0);
  useEffect(() => {
    const now = performance.now(), elapsed = (now - sample.current.at) / 1000;
    setSpeed(previous => p.phase === "DOWNLOADING" && elapsed > 0 && p.downloadedBytes >= sample.current.bytes ? previous * .7 + (p.downloadedBytes - sample.current.bytes) / elapsed * .3 : 0);
    sample.current = { bytes: p.downloadedBytes, at: now };
  }, [p.downloadedBytes, p.phase]);
  const failed = p.phase === "FAILED" || game.state === "FAILED";
  const running = game.state === "RUNNING";
  const stage = phases.indexOf(p.phase);
  const percent = p.phase === "CHECKING" ? p.checkedFiles / (p.totalFiles || 1) * 100 : p.phase === "DOWNLOADING" ? p.downloadedBytes / (p.totalBytes || 1) * 100 : stage >= 2 ? 100 : 0;
  const busy = game.state === "STARTING" || (!failed && phases.slice(0, 4).includes(p.phase));
  const idle = !p.phase && !failed && !running && !busy;
  return <><div className="job-scrim" onClick={onClose} aria-hidden="true"/><section ref={overlay} role="dialog" aria-modal="true" className="game-update-panel job-drawer" aria-label="Обновление игровой сборки">
    <header><div><span className="eyebrow">ПОДГОТОВКА ИГРЫ</span><h2>{failed ? "Не удалось завершить" : running ? "Minecraft запущен" : p.phase === "READY" ? "Сборка готова к игре" : idle ? "Сборка ещё не проверена" : "Подготовка Minecraft"}</h2></div>
      <button className="text-button" onClick={onClose}>Скрыть</button></header>
    {p.version && <span className="version-badge">{p.version}</span>}
    <ol className="update-steps">{steps.map((s, i) => <li key={s} className={i === stage ? "current" : i < stage ? "done" : ""} aria-current={i === stage ? "step" : undefined}>{i < stage ? "✓" : i + 1} · {s}</li>)}</ol>
    {busy && <div role="progressbar" aria-label="Прогресс текущего этапа" aria-valuemin={0} aria-valuemax={100} aria-valuenow={p.phase === "VERIFYING" || p.phase === "APPLYING" ? undefined : Math.round(Math.max(0, Math.min(100, percent)))} className={`update-meter ${p.phase === "VERIFYING" || p.phase === "APPLYING" ? "is-indeterminate" : ""}`}><span style={{ width: `${Math.max(0, Math.min(100, percent))}%` }} /></div>}
    <p role={failed ? "alert" : "status"}>{failed ? (game.state === "FAILED" ? game.message : p.message) : running ? "Обновление файлов заблокировано, пока работает игра." : idle ? "Проверка начнётся при запуске игры." : p.message || "Получаем список файлов…"}</p>
    {p.phase === "CHECKING" && <p>Проверено {p.checkedFiles} / {p.totalFiles || "…"} файлов</p>}
    {p.phase === "CHECKING" && !!p.cachedFiles && <p>Из кэша ассетов: {p.cachedFiles} · код проверяется по SHA-256</p>}
    {p.phase === "DOWNLOADING" && <p>{mb(p.downloadedBytes)} / {mb(p.totalBytes)} · {p.completedFiles} / {p.changedFiles} файлов{speed > 0 && ` · ${mb(speed)}/с`}</p>}
    {p.phase === "VERIFYING" && <p>Проверяем целостность скачанных файлов перед установкой.</p>}
    {p.phase === "APPLYING" && <p>Применяем проверенные файлы. Можно свернуть окно: задача продолжится в трее.</p>}
    {p.phase === "READY" && !failed && !running && <p>{p.changedFiles ? `Обновлено файлов: ${p.changedFiles}.` : "Вы используете актуальную сборку."} Если включён автозапуск, Minecraft откроется автоматически.</p>}
    {failed && <><p>После устранения причины можно повторить операцию.</p><button className="news-primary" disabled={busy || running} onClick={onRetry}>{retryLabel}</button></>}
  </section></>;
}
