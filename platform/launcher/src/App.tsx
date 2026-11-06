import { TelemetrySurvey } from "./components/TelemetrySurvey";
import {CommunityLinks} from './components/CommunityLinks';
import './profile-readable.css';
import {listen} from "@tauri-apps/api/event";
import {loadProfile,roleLabel,playtime,type ProfileResult} from "./services/profileGateway";
import {PlayerAvatar} from "./components/PlayerAvatar";
import { Cog, Download, Gamepad2, Info, LogIn, Newspaper, Play, ShieldCheck, UserRound } from "lucide-react";
import { WindowControls } from "./components/WindowControls";
import { LauncherUpdate } from "./LauncherUpdate";
import { useEffect, useMemo, useState, useRef } from "react";
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
import { CompanionProfile } from "./components/CompanionProfile";
import { LoginModal } from "./components/LoginModal";
import { RegisterModal } from "./components/RegisterModal";
import { ServerCard } from "./components/ServerCard";
import { SettingsPanel } from "./components/SettingsPanel";
import { launcherGateway } from "./services/mockLauncherGateway";
import { getServerStatus } from "./services/serverStatusGateway";
import { authGateway, type ProfileSettings } from "./services/authGateway";
import type { LauncherSnapshot } from "./types/launcher";
import { gameGateway, stopped, idleUpdate, updateLabel, updatePercent, type UpdateProgress, type GameStatus } from "./services/gameGateway";

type Page = "home" | "news" | "profile" | "about";

export default function App() {
  const [official,setOfficial]=useState<ProfileResult|null>(null);
  const [profileError,setProfileError]=useState(false);
  const [data, setData] = useState<LauncherSnapshot | null>(null);
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [loginOpen, setLoginOpen] = useState(false);
  const [accountName, setAccountName] = useState<string | null>(() => authGateway.getSessionUsername());
  const [profileSettings, setProfileSettings] = useState<ProfileSettings>(() => {
    const username = authGateway.getSessionUsername();
    return username ? authGateway.getProfileSettings(username) : { displayName: "", telegram: "@", city: "" };
  });
  const authenticated = accountName !== null;
  const [page, setPage] = useState<Page>("home");
  const scrollPositions = useRef<Partial<Record<Page, number>>>({});
  function navigate(next: Page) {
    const main = document.querySelector("main");
    scrollPositions.current[page] = main?.scrollTop ?? 0;
    setPage(next);
    requestAnimationFrame(() => { if (main) main.scrollTop = scrollPositions.current[next] ?? 0; });
  }
  useEffect(() => {
    function restoreAppearance() {
      let minimal = false, colour = "#b58b60";
      try {
        const account = authGateway.getPlatformUserId();
        const value = accountName && account ? JSON.parse(localStorage.getItem("recreated.profile-look.v1:" + account) || "{}") : {};
        minimal = value.minimal === true;
        if (["#b58b60","#719b87","#708eb5","#a783af"].includes(value.colour)) colour = value.colour;
      } catch { /* Storage is optional. */ }
      document.documentElement.dataset.motion = minimal ? "minimal" : "normal";
      document.documentElement.style.setProperty("--profile-accent", colour);
    }
    restoreAppearance();
    window.addEventListener("profile-appearance", restoreAppearance);
    return () => window.removeEventListener("profile-appearance", restoreAppearance);
  }, [accountName]);
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
      if (active) { setNews(posts);  }
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
    setUpdateOpen(false);
    setGame({ ...stopped, state: "STARTING", message: "Проверка обновлений" });
    setUpdate({ ...idleUpdate, phase: "CHECKING", message: "Проверка обновлений" });
    try {
      if(launcherUpdateRequired){setUpdate(await gameGateway.prepare());setGame(stopped);}else setGame(await gameGateway.start());
    }
    catch (error) { setUpdateOpen(true); setGame({ ...stopped, state: "FAILED", message: String(error instanceof Error ? error.message : error) }); }
  }

  async function handleLogout() {
    await gameGateway.logout();
    try { await authGateway.logout(); }
    finally { navigate("home"); setAccountName(null); }
  }

  function handleAuthenticated(username: string) {
    setAccountName(username);
    setProfileSettings(authGateway.getProfileSettings(username));
    setLoginOpen(false);
    setRegisterOpen(false);
  }

  if (!data) {
    return <div className="loading-screen"><span className="companion-loading">RE:CREATED</span><p>Открываем Launcher…</p></div>;
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
    <div className="app-shell companion-shell" style={{ "--hero-image": `url(${cityArt})` } as React.CSSProperties}>
      <aside className="companion-sidebar">
        <BrandMark />
        <span className="companion-sidebar__caption">Твой игровой мир</span>
        <nav aria-label="Основная навигация">
          <button aria-label="Главная страница" aria-current={page==="home"?"page":undefined} className={page==="home"?"is-active":""} onClick={()=>navigate("home")}><Gamepad2 size={20}/><span>Играть</span></button>
          <button aria-current={page==="news"?"page":undefined} className={page==="news"?"is-active":""} onClick={()=>navigate("news")}><Newspaper size={20}/><span>Новости</span></button>
          <button aria-current={page==="profile"?"page":undefined} className={page==="profile"?"is-active":""} onClick={()=>navigate("profile")}><UserRound size={20}/><span>Профиль</span></button>
          <button aria-current={page==="about"?"page":undefined} className={page==="about"?"is-active":""} onClick={()=>navigate("about")}><Info size={20}/><span>О сервере</span></button>
        </nav>
        <div className="companion-sidebar__bottom">
          <span className="companion-sidebar__caption">Сообщество</span><CommunityLinks/>
          <button className="companion-settings" onClick={()=>setSettingsOpen(true)}><Cog size={20}/><span>Настройки</span></button>
          <button className="companion-account" onClick={()=>authenticated?navigate("profile"):setLoginOpen(true)}>
            <span className="companion-account__avatar">{authenticated?<PlayerAvatar skin={activeProfile.skinData} initials={activeProfile.initials}/>:<UserRound size={23}/>}</span>
            <span><strong>{authenticated?activeProfile.displayName:"Войти в аккаунт"}</strong><small>{authenticated?"Профиль RE:CREATED":"Для запуска игры"}</small></span>
          </button>
        </div>
      </aside>
      <header className="titlebar" data-tauri-drag-region>
        <div className="companion-breadcrumb"><span>RE:CREATED /</span><strong>{({home:"Играть",news:"Новости",profile:"Профиль",about:"О сервере"})[page]}</strong></div>
        <WindowControls />
      </header>
      <LauncherUpdate onRequired={setLauncherUpdateRequired} />

      <main className={`companion-main ${page !== "home" ? "main--inner" : ""}`}>
        <div className="page-transition" key={page}>
        {page === "home" ? <>
        <div className="companion-page-heading"><div><span className="eyebrow">Альфа-тестирование</span><h1>{authenticated?`С возвращением, ${accountName}.`:"Добро пожаловать в RE:CREATED."}</h1></div><span className="companion-channel">Alpha · Minecraft 1.21.1</span></div>
        <div className="companion-dashboard">
          <div className="companion-dashboard__world">
            <section className="companion-banner"><span>Создавай своё</span><h2>Мир, который<br/>строят игроки.</h2><p>Города. Производство. Железные дороги.</p><button onClick={()=>{const release=news.find(post=>post.releaseVersion);if(release)setChangelog(release);else navigate("news");}}>Что нового <span>→</span></button></section>
            <ServerCard status={data.status}/>
          </div>
          {authenticated?<CompanionProfile compact profile={activeProfile} onOpen={()=>navigate("profile")}/>:<section className="companion-guest"><UserRound size={32}/><h2>Твой профиль</h2><p>Войди в аккаунт, чтобы увидеть свой город и игровую статистику.</p><button onClick={()=>setLoginOpen(true)}>Войти в аккаунт</button></section>}
        </div>
        <div className="content-surface"><NewsRail items={news} loading={newsLoading} error={newsError} onOpenNews={() => navigate("news")}/></div>
        </> : page === "news" ? <NewsPage items={news} loading={newsLoading} error={newsError} onRetry={() => setNewsAttempt(n => n + 1)} onBack={() => navigate("home")} /> : page==="profile" ? <>
          <div className="companion-page-heading"><div><span className="eyebrow">RE:CREATED Companion</span><h1>Профиль игрока</h1></div></div>
          {authenticated?<CompanionProfile registeredAt={official?.profile.registeredAt} profile={activeProfile} onEdit={()=>setEditProfileOpen(true)} onLogout={()=>void handleLogout()}/>:<section className="companion-guest"><UserRound size={32}/><h2>Здесь будет твой персонаж</h2><p>Город, роль и время в игре поступают с сервера после входа.</p><button onClick={()=>setLoginOpen(true)}>Войти в аккаунт</button></section>}
        </> : <AboutPage onBack={() => navigate("home")} />}
        </div>
      </main>
      {updateOpen && <GameUpdatePanel progress={update} game={game} onRetry={() => void handlePlay()} onClose={() => setUpdateOpen(false)} />}
      <ChangelogModal post={changelog} onClose={() => { if (changelog) markReleaseSeen(changelog.id); setChangelog(null); }} />

      <footer className="launchbar">
        <div className="build-info">
          <span className="build-icon"><ShieldCheck size={17} /></span>
          <span><small>ВЕРСИЯ СБОРКИ</small><strong>{update.version ?? "Не проверена"}</strong></span>
          <button className="text-button build-detail" onClick={() => setUpdateOpen(true)}>Подробности</button>
        </div>
        <div className="companion-launch-note"><strong>{launchState==="RUNNING"?"Хорошей игры":launchState==="STARTING"?"Готовим твой мир":"Всё начинается с одной идеи"}</strong><small>{launchState==="STARTING"?"Подготовка продолжится, если свернуть окно.":"Create · Города · Сообщество"}</small></div>
        <div className="play-wrap">
          {launchState === "STARTING" && <div className={`launch-progress ${update.phase === "VERIFYING" || update.phase === "APPLYING" ? "is-indeterminate" : ""}`} style={{ width: `${updatePercent(update)}%` }} />}
          {update.phase === "READY" && launchState !== "RUNNING" && <div className="update-ready">Готово · {update.version}</div>}
          {game.state === "FAILED" && <button className="game-launch-error" onClick={() => setUpdateOpen(true)}>Не удалось запустить · Подробнее</button>}
          <button className={`play-button play-button--${authenticated ? launchState.toLowerCase() : "login"}`} onClick={handlePlay} disabled={(launcherUpdateRequired&&update.phase==="READY") || launchState === "STARTING" || launchState === "RUNNING"} title={launcherUpdateRequired && update.phase === "READY" ? "Требуется обновление Launcher" : game.logPath ?? game.message}>
            <i className="play-button__sweep" />
            <span className="play-button__icon">{!authenticated ? <LogIn size={22} /> : launchState === "STARTING" ? <Download size={21} className="spin-slow" /> : <Play size={22} fill="currentColor" />}</span>
            <span className="play-button__label">{authenticated ? actionLabel : "Войдите"}</span>
            <small>{authenticated ? (launchState === "STARTING" ?
              (update.phase === "CHECKING" ? update.totalFiles ? `${update.checkedFiles}/${update.totalFiles} файлов` : "Получаем список файлов…" :
                update.phase === "DOWNLOADING" ? `${(update.downloadedBytes / 1048576).toFixed(1)}/${(update.totalBytes / 1048576).toFixed(1)} МБ` :
                update.phase === "READY" ? "Готовим игровой вход…" : updateLabel(update)) :
              launchState === "RUNNING" ? "Можно свернуть Launcher" : "Minecraft 1.21.1 · Alpha") : "в аккаунт для игры"}</small>
            <span className="play-button__segments" aria-hidden="true" />
          </button>
        </div>
      </footer>

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
