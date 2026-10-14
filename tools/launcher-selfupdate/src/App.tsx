import { ChevronDown, Cog, Download, LogIn, Play, ShieldCheck } from "lucide-react";
import { WindowControls } from "./components/WindowControls";
import { LauncherUpdate } from "./LauncherUpdate";
import { useEffect, useMemo, useState } from "react";
import cityArt from "./assets/industrial-city.png";
import "./game.css";
import { AboutPage } from "./components/AboutPage";
import { BrandMark } from "./components/BrandMark";
import { EditProfileModal } from "./components/EditProfileModal";
import { NewsRail } from "./components/NewsRail";
import { NewsPage } from "./components/NewsPage";
import { LoginModal } from "./components/LoginModal";
import { ProfilePopover } from "./components/ProfilePopover";
import { RegisterModal } from "./components/RegisterModal";
import { ServerCard } from "./components/ServerCard";
import { SettingsPanel } from "./components/SettingsPanel";
import { launcherGateway } from "./services/mockLauncherGateway";
import { getServerStatus } from "./services/serverStatusGateway";
import { authGateway, type ProfileSettings } from "./services/authGateway";
import type { LauncherSnapshot } from "./types/launcher";
import { gameGateway, stopped, idleUpdate, updateLabel, updatePercent, type UpdateProgress, type GameStatus } from "./services/gameGateway";

type Page = "home" | "news" | "about";

export default function App() {
  const [data, setData] = useState<LauncherSnapshot | null>(null);
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [profileOpen, setProfileOpen] = useState(false);
  const [loginOpen, setLoginOpen] = useState(false);
  const [accountName, setAccountName] = useState<string | null>(() => authGateway.getSessionUsername());
  const [profileSettings, setProfileSettings] = useState<ProfileSettings>(() => {
    const username = authGateway.getSessionUsername();
    return username ? authGateway.getProfileSettings(username) : { displayName: "", telegram: "@", city: "" };
  });
  const authenticated = accountName !== null;
  const [page, setPage] = useState<Page>("home");
  const [registerOpen, setRegisterOpen] = useState(false);
  const [editProfileOpen, setEditProfileOpen] = useState(false);
  const [game, setGame] = useState<GameStatus>(stopped);
  const [update, setUpdate] = useState<UpdateProgress>(idleUpdate);
  const [launcherUpdateRequired, setLauncherUpdateRequired] = useState(false);
  const launchState = game.state;

  useEffect(() => {
    let active = true;
    let timer: ReturnType<typeof setTimeout>;
    async function refresh() {
      const status = await getServerStatus();
      if (active) {
        setData(previous => previous ? { ...previous, status } : previous);
        timer = setTimeout(refresh, 15000);
      }
    }
    void launcherGateway.getSnapshot().then(snapshot => {
      if (active) { setData(snapshot); void refresh(); }
    });
    return () => { active = false; clearTimeout(timer); };
  }, []);

  useEffect(() => {
    let active = true;
    void authGateway.restore().then(account => {
      if (active && account) handleAuthenticated(account.username);
    }).catch(() => { /* Fail closed: no offline/cached promotion to an authenticated account. */ });
    return () => { active = false; };
  }, []);

  useEffect(() => {
    let active = true;
    let timer: ReturnType<typeof setTimeout>;
    async function poll() {
      try {
        const [status, progress] = await Promise.all([gameGateway.status(), gameGateway.updateStatus()]);
        if (active) setUpdate(progress);
        if (active) setGame(previous => status.state === "STOPPED" &&
          (previous.state === "FAILED" || previous.state === "STARTING") ? previous : status);
      }
      catch { /* Keep the last known state, never infer STOPPED from IPC failure. */ }
      if (active) timer = setTimeout(poll, 1000);
    }
    void poll();
    return () => { active = false; clearTimeout(timer); };
  }, []);

  const actionLabel = useMemo(() => {
    if (launchState === "STARTING") return update.phase === "READY" ? "Запускаем Minecraft" : updateLabel(update);
    if (launchState === "RUNNING") return "Minecraft запущен";
    if (launchState === "FAILED") return "Повторить проверку";
    return update.phase === "READY" ? "Играть" : "Проверить сборку";
  }, [launchState, update]);

  async function handlePlay() {
    if (launcherUpdateRequired && update.phase === "READY" && launchState !== "FAILED") return;
    if (!authenticated) {
      setLoginOpen(true);
      return;
    }
    if (launchState === "STARTING" || launchState === "RUNNING") return;
    const ready = update.phase === "READY" && launchState !== "FAILED";
    setGame({ ...stopped, state: "STARTING", message: "Проверка обновлений" });
    if (!ready) setUpdate({ ...idleUpdate, phase: "CHECKING", message: "Проверка обновлений" });
    try {
      if (ready) setGame(await gameGateway.start());
      else { setUpdate(await gameGateway.prepare()); setGame(stopped); }
    }
    catch (error) { setGame({ ...stopped, state: "FAILED", message: String(error instanceof Error ? error.message : error) }); }
  }

  async function handleLogout() {
    await gameGateway.logout();
    try { await authGateway.logout(); }
    finally { setProfileOpen(false); setAccountName(null); }
  }

  function handleAuthenticated(username: string) {
    setAccountName(username);
    setProfileSettings(authGateway.getProfileSettings(username));
    setLoginOpen(false);
    setRegisterOpen(false);
  }

  if (!data) {
    return <div className="loading-screen"><span className="loading-gear">R:R</span><p>Запускаем механизмы…</p></div>;
  }

  const activeProfile = {
    ...data.profile,
    verified: authenticated,
    subtitle: authenticated ? "Create Server Account" : data.profile.subtitle,
    role: authenticated ? "Нет данных API" : data.profile.role,
    joinedAt: authenticated ? "Нет данных API" : data.profile.joinedAt,
    playtime: authenticated ? "—" : data.profile.playtime,
    displayName: authenticated ? profileSettings.displayName : data.profile.displayName,
    initials: authenticated ? profileSettings.displayName.slice(0, 2).toUpperCase() : data.profile.initials,
    accountId: authenticated ? authGateway.getPlatformUserId() ?? "" : data.profile.accountId,
    city: authenticated ? profileSettings.city : data.profile.city,
  };

  return (
    <div className="app-shell" style={{ "--hero-image": `url(${cityArt})` } as React.CSSProperties}>
      <div className="noise" />
      <header className="titlebar" data-tauri-drag-region>
        <BrandMark />
        <nav>
          <button className={`nav-link ${page === "home" ? "is-active" : ""}`} onClick={() => setPage("home")}>Главная</button>
          <button className={`nav-link ${page === "news" ? "is-active" : ""}`} onClick={() => setPage("news")}>Новости</button>
          <button className={`nav-link ${page === "about" ? "is-active" : ""}`} onClick={() => setPage("about")}>О сервере</button>
        </nav>
        <WindowControls />
      </header>
      <LauncherUpdate onRequired={setLauncherUpdateRequired} />

      <main className={page !== "home" ? "main--inner" : ""}>
        <div className="page-transition" key={page}>
        {page === "home" ? <>
        <section className="hero">
          <div className="hero__content">
            <div className="season-tag"><span>СЕЗОН 01</span><i /> ПРОМЫШЛЕННАЯ ЭРА</div>
            <h1>Строй.<br /><em>Соединяй.</em><br />Развивай.</h1>
            <p className="hero__lead">Мир, где города растут вокруг производства, а расстояния превращают железные дороги в артерии экономики.</p>
            <ServerCard status={data.status} />
          </div>
          <div className="hero__art-credit"><span>АРТ-КОНЦЕПТ</span><strong>Индустриальная долина</strong></div>
        </section>

        <div className="content-surface">
          <NewsRail items={data.news} onOpenNews={() => setPage("news")} />
        </div>
        </> : page === "news" ? <NewsPage items={data.news} onBack={() => setPage("home")} /> : <AboutPage onBack={() => setPage("home")} />}
        </div>
      </main>

      <footer className="launchbar">
        <button className={`profile-button ${profileOpen ? "is-open" : ""}`} onClick={() => authenticated ? setProfileOpen((value) => !value) : setLoginOpen(true)} aria-expanded={profileOpen}>
          <span className={`avatar ${authenticated ? "" : "avatar--guest"}`}>{authenticated ? activeProfile.initials : "?"}</span>
          <span><strong>{authenticated ? activeProfile.displayName : "Гостевой режим"}</strong><small>{authenticated ? activeProfile.subtitle : "Требуется вход"}</small></span>
          <ChevronDown size={15} className="profile-chevron" />
        </button>
        <div className="build-info">
          <span className="build-icon"><ShieldCheck size={17} /></span>
          <span><small>ВЕРСИЯ СБОРКИ</small><strong>{update.version ?? "Не проверена"}</strong></span>
          <span className="build-detail">Minecraft {data.build.minecraftVersion} · {data.build.loader} · {data.build.size}</span>
        </div>
        <button className="settings-button" onClick={() => setSettingsOpen(true)}><Cog size={19} /><span>Настройки</span></button>
        <div className="play-wrap">
          {launchState === "STARTING" && <div className="launch-progress" style={{ width: `${updatePercent(update)}%` }} />}
          {update.phase === "READY" && launchState !== "RUNNING" && <div className="update-ready">Готово · {update.version}</div>}
          {game.state === "FAILED" && <div role="alert" className="game-launch-error">{game.message}{game.logPath && <small>Лог: {game.logPath}</small>}</div>}
          <button className={`play-button play-button--${authenticated ? launchState.toLowerCase() : "login"}`} onClick={handlePlay} disabled={(launcherUpdateRequired && update.phase === "READY") || launchState === "STARTING" || launchState === "RUNNING"} title={launcherUpdateRequired && update.phase === "READY" ? "Требуется обновление Launcher" : game.logPath ?? game.message}>
            <i className="play-button__sweep" />
            <span className="play-button__icon">{!authenticated ? <LogIn size={22} /> : launchState === "STARTING" ? <Download size={21} className="spin-slow" /> : <Play size={22} fill="currentColor" />}</span>
            <span className="play-button__label">{authenticated ? actionLabel : "Войдите"}</span>
            <small>{authenticated ? (launchState === "STARTING" ?
              (update.phase === "CHECKING" ? `${update.checkedFiles}/${update.totalFiles} файлов` :
                `${(update.downloadedBytes / 1048576).toFixed(1)}/${(update.totalBytes / 1048576).toFixed(1)} МБ · ${update.completedFiles}/${update.changedFiles} файлов`) :
              launchState === "RUNNING" ? "RUNNING" : "NeoForge 21.1.251") : "в аккаунт для игры"}</small>
            <span className="play-button__segments" aria-hidden="true" />
          </button>
        </div>
      </footer>

      <ProfilePopover
        profile={activeProfile}
        open={profileOpen && authenticated}
        onClose={() => setProfileOpen(false)}
        onLogout={() => void handleLogout()}
        onEdit={() => { setProfileOpen(false); setEditProfileOpen(true); }}
      />
      <LoginModal
        open={loginOpen}
        onClose={() => setLoginOpen(false)}
        onSuccess={() => { const username = authGateway.getSessionUsername(); if (username) handleAuthenticated(username); }}
        onRegister={() => { setLoginOpen(false); setRegisterOpen(true); }}
      />
      <RegisterModal
        open={registerOpen}
        onClose={() => setRegisterOpen(false)}
        onLogin={() => { setRegisterOpen(false); setLoginOpen(true); }}
        onSuccess={() => { setRegisterOpen(false); setLoginOpen(true); }}
      />
      <EditProfileModal
        open={editProfileOpen && authenticated}
        username={accountName ?? ""}
        initialSettings={profileSettings}
        onClose={() => setEditProfileOpen(false)}
        onSaved={(settings) => { setProfileSettings(settings); setEditProfileOpen(false); }}
      />
      <SettingsPanel open={settingsOpen} onClose={() => setSettingsOpen(false)} />
    </div>
  );
}
