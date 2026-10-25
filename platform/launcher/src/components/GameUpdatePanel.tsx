import { useEffect, useRef, useState } from "react";
import type { GameStatus, UpdateProgress } from "../services/gameGateway";
const steps = ["Проверка сборки", "Загрузка", "Проверка SHA-256", "Установка", "Готово"];
const phases = ["CHECKING", "DOWNLOADING", "VERIFYING", "APPLYING", "READY"];
export const mb = (bytes: number) => `${(Math.max(0, bytes) / 1048576).toFixed(1)} МБ`;
export function GameUpdatePanel({ progress: p, game, onRetry, onClose }: {
  progress: UpdateProgress; game: GameStatus; onRetry: () => void; onClose: () => void;
}) {
  const sample = useRef({ bytes: p.downloadedBytes, at: performance.now() });
  const [speed, setSpeed] = useState(0);
  useEffect(() => {
    const now = performance.now(), elapsed = (now - sample.current.at) / 1000;
    setSpeed(p.phase === "DOWNLOADING" && elapsed > 0 && p.downloadedBytes >= sample.current.bytes ? (p.downloadedBytes - sample.current.bytes) / elapsed : 0);
    sample.current = { bytes: p.downloadedBytes, at: now };
  }, [p.downloadedBytes, p.phase]);
  const failed = p.phase === "FAILED" || game.state === "FAILED";
  const running = game.state === "RUNNING";
  const stage = phases.indexOf(p.phase);
  const percent = p.phase === "CHECKING" ? p.checkedFiles / (p.totalFiles || 1) * 100 : p.phase === "DOWNLOADING" ? p.downloadedBytes / (p.totalBytes || 1) * 100 : stage >= 2 ? 100 : 0;
  const busy = game.state === "STARTING" || (!failed && phases.slice(0, 4).includes(p.phase));
  return <section className="game-update-panel" aria-label="Обновление игровой сборки">
    <header><div><span className="eyebrow">ПОДГОТОВКА ИГРЫ</span><h2>{failed ? "Не удалось завершить" : running ? "Minecraft запущен" : p.phase === "READY" ? "Сборка готова к игре" : "Подготовка Minecraft"}</h2></div>
      <button className="text-button" onClick={onClose}>Скрыть</button></header>
    {p.version && <span className="version-badge">{p.version}</span>}
    <ol className="update-steps">{steps.map((s, i) => <li key={s} className={i === stage ? "current" : i < stage ? "done" : ""} aria-current={i === stage ? "step" : undefined}>{i < stage ? "✓" : i + 1} · {s}</li>)}</ol>
    <div role="progressbar" aria-label="Прогресс текущего этапа" aria-valuemin={0} aria-valuemax={100} aria-valuenow={Math.round(Math.max(0, Math.min(100, percent)))} className="update-meter"><span style={{ width: `${Math.max(0, Math.min(100, percent))}%` }} /></div>
    <p role={failed ? "alert" : "status"}>{failed ? game.message || p.message : running ? "Обновление файлов заблокировано, пока работает игра." : p.message || "Получаем список файлов…"}</p>
    {p.phase === "CHECKING" && <p>Проверено {p.checkedFiles} / {p.totalFiles || "…"} файлов</p>}
    {p.phase === "CHECKING" && !!p.cachedFiles && <p>Из кэша ассетов: {p.cachedFiles} · код проверяется по SHA-256</p>}
    {p.phase === "DOWNLOADING" && <p>{mb(p.downloadedBytes)} / {mb(p.totalBytes)} · {p.completedFiles} / {p.changedFiles} файлов{speed > 0 && ` · ${mb(speed)}/с`}</p>}
    {p.phase === "VERIFYING" && <p>Проверяем целостность скачанных файлов перед установкой.</p>}
    {p.phase === "APPLYING" && <p>Применяем проверенные файлы. Можно свернуть окно: задача продолжится в трее.</p>}
    {p.phase === "READY" && !failed && !running && <p>{p.changedFiles ? `Обновлено файлов: ${p.changedFiles}.` : "Вы используете актуальную сборку."} Если включён автозапуск, Minecraft откроется автоматически.</p>}
    {failed && <><p>Игра не будет запущена. Повторите проверку; личные данные и настройки не удаляются.</p><button className="news-primary" disabled={busy || running} onClick={onRetry}>Повторить проверку сборки</button></>}
  </section>;
}
