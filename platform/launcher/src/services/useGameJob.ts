import { listen } from "@tauri-apps/api/event";
import { useEffect, useRef, useState } from "react";
import { gameGateway, idleUpdate, stopped, type GameStatus } from "./gameGateway";

export type GameAction = "launch" | "check";

export function useGameJob() {
  const [game, setGame] = useState(stopped);
  const [update, setUpdate] = useState(idleUpdate);
  const [lastAction, setLastAction] = useState<GameAction>("launch");
  const pending = useRef(false);
  const revision = useRef(0);
  const refresh = useRef<() => void>(() => {});

  useEffect(() => {
    if (!("__TAURI_INTERNALS__" in window)) return;
    let alive = true;
    let timer: ReturnType<typeof setTimeout>;
    let stop: (() => void) | undefined;
    async function poll() {
      clearTimeout(timer);
      const ticket = ++revision.current;
      let activeJob = pending.current;
      try {
        const [status, progress] = await Promise.all([gameGateway.status(), gameGateway.updateStatus()]);
        if (!alive || ticket !== revision.current) return;
        activeJob ||= status.state === "STARTING";
        setUpdate(progress);
        if (!pending.current) {
          setGame(previous => previous.state === "FAILED" && status.state === "STOPPED" ? previous : status);
        }
      } catch { /* Keep the last native snapshot on an IPC failure. */ }
      if (alive && ticket === revision.current) {
        timer = setTimeout(poll, document.hidden ? 30000 : activeJob ? 750 : 12000);
      }
    }
    refresh.current = poll;
    void poll();
    void listen<GameStatus>("game-job", event => {
      if (!alive) return;
      setGame(event.payload);
      void poll();
    }).then(unlisten => { if (alive) stop = unlisten; else unlisten(); }).catch(() => {});
    window.addEventListener("focus", poll);
    document.addEventListener("visibilitychange", poll);
    return () => {
      alive = false;
      clearTimeout(timer);
      stop?.();
      refresh.current = () => {};
      window.removeEventListener("focus", poll);
      document.removeEventListener("visibilitychange", poll);
    };
  }, []);

  async function run(action: GameAction) {
    if (pending.current || game.state === "RUNNING" || game.state === "STARTING") return;
    pending.current = true;
    setLastAction(action);
    setGame({ ...stopped, state: "STARTING", message: "Проверка сборки" });
    setUpdate({ ...idleUpdate, phase: "CHECKING" });
    refresh.current();
    try {
      if (action === "check") {
        setUpdate(await gameGateway.prepare());
        setGame(stopped);
      } else {
        setGame(await gameGateway.start());
      }
    } catch (error) {
      setGame({ ...stopped, state: "FAILED", message: error instanceof Error ? error.message : String(error) });
    } finally {
      pending.current = false;
      refresh.current();
    }
  }
  return { game, update, lastAction, run };
}
