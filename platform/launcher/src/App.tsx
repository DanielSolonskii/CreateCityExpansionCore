import { TelemetrySurvey } from "./components/TelemetrySurvey";
import {listen} from "@tauri-apps/api/event";
import {loadProfile,roleLabel,playtime,type ProfileResult} from "./services/profileGateway";
import {PlayerAvatar} from "./components/PlayerAvatar";
import { ChevronDown, Cog, Download, LogIn, Play, ShieldCheck } from "lucide-react";
import { WindowControls } from "./components/WindowControls";
import { LauncherUpdate } from "./LauncherUpdate";
import { useEffect, useMemo, useState } from "react";
import cityArt from "./assets/industrial-city.png";
import "./game.css";
import "./profile-update.css";
import { AboutPage } from "./components/AboutPage";
import { BrandMark } from "./components/BrandMark";
import { EditProfileModal } from "./components/EditProfileModal";
import { NewsRail } from "./components/NewsRail";
import { NewsPage } from "./components/NewsPage";
import { ChangelogModal } from "./components/ChangelogModal";
import { GameUpdatePanel } from "./components/GameUpdatePanel";
import { getNews, unreadRelease, markReleaseSeen, type NewsPost } from "./services/newsGateway";
import "./news-update.css";
import { StartupIntro } from "./components/StartupIntro";
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
  const [opening, setOpening] = useState(true);
  useEffect(() => { const timer = setTimeout(() => setOpening(false), 1250); return () => clearTimeout(timer); }, []);
  const [official,setOfficial]=useState<ProfileResult|null>(null);
  const [profileError,setProfileError]=useState(false);
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
  const [news, setNews] = useState<NewsPost[]>([]);
  const [newsLoading, setNewsLoading] = useState(true);
  const [newsError, setNewsError] = useState("");
  const [newsAttempt, setNewsAttempt] = useState(0);
  const [changelog, setChangelog] = useState<NewsPost | null>(null);
  const [updateOpen, setUpdateOpen] = useState(false);
  useEffect(() => {
    let active = true;
    setNewsLoading(true); setNewsError("");
    void getNews().then(posts => {
      if (active) { setNews(posts); if (newsAttempt === 0) setChangelog(unreadRelease(posts)); }
    }).catch(() => { if (active) setNewsError("Новости временно недоступны. Игра и вход продолжают работать."); })
      .finally(() => { if (active) setNewsLoading(false); });
    return () => { active = false; };
  }, [newsAttempt]);

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

  useEffect(()=>{
    if(!accountName){setOfficial(null);return;}let active=true;let timer:ReturnType<typeof setTimeout>;
    async function refresh(){try{const result=await loadProfile();if(active){setOfficial(result);setProfileError(false);}}catch{if(active)setProfileError(true);}if(active)timer=setTimeout(refresh,60000);}
    setOfficial(null);void refresh();return()=>{active=false;clearTimeout(timer);};
  },[accountName]);
  useEffect(()=>{if(!("__TAURI_INTERNALS__" in window))return;let alive=true;let stop:(()=>void)|undefined;
    void listen<GameStatus>("game-job",e=>{if(alive){setGame(e.payload);void gameGateway.updateStatus().then(p=>{if(alive)setUpdate(p);});}}).then(s=>{if(alive)stop=s;else s();}).catch(()=>{/* Native polling remains a fallback. */});
    const restore=()=>{void Promise.all([gameGateway.status(),gameGateway.updateStatus()]).then(([g,p])=>{if(alive){setGame(g);setUpdate(p);}});};
    window.addEventListener("focus",restore);return()=>{alive=false;stop?.();window.removeEventListener("focus",restore);};
  },[]);

  const actionLabel = useMemo(() => {
    if (launchState === "STARTING") return update.phase === "READY" ? "Запускаем Minecraft" : updateLabel(update);
    if (launchState === "RUNNING") return "Minecraft запущен";
    if (launchState === "FAILED") return "Повторить запуск";
    return launcherUpdateRequired&&update.phase!=="READY"?"Проверить сборку":"Играть";
  }, [launchState, update,launcherUpdateRequired]);

  async function handlePlay() {
    if (launcherUpdateRequired&&update.phase==="READY") return;
    if (!authenticated) {
      setLoginOpen(true);
      return;
    }
    if (launchState === "STARTING" || launchState === "RUNNING") return;
    setUpdateOpen(true);
    const ready = false;
    setGame({ ...stopped, state: "STARTING", message: "Проверка обновлений" });
    if (!ready) setUpdate({ ...idleUpdate, phase: "CHECKING", message: "Проверка обновлений" });
    try {
      if(launcherUpdateRequired){setUpdate(await gameGateway.prepare());setGame(stopped);}else setGame(await gameGateway.start());
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
    return <div className="loading-screen">{opening && <StartupIntro />}<span className="loading-gear">R:R</span><p>Запускаем механизмы…</p></div>;
  }

  const activeProfile = {
    ...data.profile,
    verified: authenticated,
    subtitle: authenticated ? `Игровой ник: ${official?.profile.username??accountName}` : data.profile.subtitle,
    role: authenticated ? official ? official.profile.cityState==="UNKNOWN"?"Ожидает синхронизации":official.profile.role?roleLabel(official.profile.role):"Без городской роли" : "Загрузка…" : data.profile.role,
    joinedAt: authenticated ? official ? new Date(official.profile.registeredAt).toLocaleDateString("ru-RU") : "Загрузка…" : data.profile.joinedAt,
    playtime: authenticated ? official ? playtime(official.profile.playTicks) : "Загрузка…" : data.profile.playtime,
    displayName: authenticated ? profileSettings.displayName || official?.profile.username || accountName || "Игрок" : data.profile.displayName,
    initials: authenticated ? profileSettings.displayName.slice(0, 2).toUpperCase() : data.profile.initials,
    accountId: authenticated ? authGateway.getPlatformUserId() ?? "" : data.profile.accountId,
    city: authenticated ? official ? official.profile.cityState==="UNKNOWN"?"Ожидает синхронизации":official.profile.city||"Без города" : profileError?"Нет соединения":"Загрузка…" : data.profile.city,
    skinData:official?.skinData,
    syncLabel:profileError?"Не удалось обновить данные · показаны последние полученные":official?`Синхронизация ${official.profile.cityState==="CURRENT"?"актуальна":"ожидается"}${official.profile.cityObservedAt?" · "+new Date(official.profile.cityObservedAt).toLocaleTimeString("ru-RU"):""}`:"Получаем данные игрового сервера…",
  };

  return (
    <div className="app-shell" style={{ "--hero-image": `url(${cityArt})` } as React.CSSProperties}>
      {opening && <StartupIntro />}
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
          <NewsRail items={news} loading={newsLoading} error={newsError} onOpenNews={() => setPage("news")} />
        </div>
        </> : page === "news" ? <NewsPage items={news} loading={newsLoading} error={newsError} onRetry={() => setNewsAttempt(n => n + 1)} onBack={() => setPage("home")} /> : <AboutPage onBack={() => setPage("home")} />}
        </div>
      </main>
      {updateOpen && <GameUpdatePanel progress={update} game={game} onRetry={() => void handlePlay()} onClose={() => setUpdateOpen(false)} />}
      <ChangelogModal post={changelog} onClose={() => { if (changelog) markReleaseSeen(changelog.id); setChangelog(null); }} />

      <footer className="launchbar">
        <button className={`profile-button ${profileOpen ? "is-open" : ""}`} onClick={() => authenticated ? setProfileOpen((value) => !value) : setLoginOpen(true)} aria-expanded={profileOpen}>
          <span className={`avatar ${authenticated ? "" : "avatar--guest"}`}>{authenticated ? <PlayerAvatar skin={activeProfile.skinData} initials={activeProfile.initials}/> : "?"}</span>
          <span><strong>{authenticated ? activeProfile.displayName : "Гостевой режим"}</strong><small>{authenticated ? activeProfile.subtitle : "Требуется вход"}</small></span>
          <ChevronDown size={15} className="profile-chevron" />
        </button>
        <div className="build-info">
          <span className="build-icon"><ShieldCheck size={17} /></span>
          <span><small>ВЕРСИЯ СБОРКИ</small><strong>{update.version ?? "Не проверена"}</strong></span>
          <button className="text-button build-detail" onClick={() => setUpdateOpen(true)}>Прогресс сборки</button>
        </div>
        <button className="settings-button" onClick={() => setSettingsOpen(true)}><Cog size={19} /><span>Настройки</span></button>
        <div className="play-wrap">
          {launchState === "STARTING" && <div className="launch-progress" style={{ width: `${updatePercent(update)}%` }} />}
          {update.phase === "READY" && launchState !== "RUNNING" && <div className="update-ready">Готово · {update.version}</div>}
          {game.state === "FAILED" && <div role="alert" className="game-launch-error">{game.message}{game.logPath && <small>Лог: {game.logPath}</small>}</div>}
          <button className={`play-button play-button--${authenticated ? launchState.toLowerCase() : "login"}`} onClick={handlePlay} disabled={(launcherUpdateRequired&&update.phase==="READY") || launchState === "STARTING" || launchState === "RUNNING"} title={launcherUpdateRequired && update.phase === "READY" ? "Требуется обновление Launcher" : game.logPath ?? game.message}>
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
      <TelemetrySurvey enabled={authenticated && game.state!=="RUNNING" && game.state!=="STARTING"}/>
      <SettingsPanel open={settingsOpen} onClose={() => setSettingsOpen(false)} busy={launchState==="STARTING"||launchState==="RUNNING"} onRepair={()=>{
        setSettingsOpen(false);setUpdateOpen(true);setGame({...stopped,state:"STARTING",message:"Полная проверка сборки"});
        void gameGateway.prepare().then(p=>{setUpdate(p);setGame(stopped);}).catch(e=>setGame({...stopped,state:"FAILED",message:String(e)}));
      }}/>
    </div>
  );
}
