import { TelemetrySurvey } from "./components/TelemetrySurvey";
import {CommunityLinks} from './components/CommunityLinks';
import './profile-readable.css';
import { readLook } from "./services/profileLook";
import { useGameJob, type GameAction } from "./services/useGameJob";
import { LaunchBar } from "./components/LaunchBar";
import {loadProfile,roleLabel,playtime,type ProfileResult} from "./services/profileGateway";
import {PlayerAvatar} from "./components/PlayerAvatar";
import { Cog, House, Info, Newspaper, UserRound, BookOpen } from "lucide-react";
import { WindowControls } from "./components/WindowControls";
import { LauncherUpdate } from "./LauncherUpdate";
import { useEffect, useState, useRef } from "react";
import cityArt from "./assets/industrial-city.jpg";
import "./game.css";
import "./profile-update.css";
import { AboutPage } from "./components/AboutPage";
import { BrandMark } from "./components/BrandMark";
import { EditProfileModal } from "./components/EditProfileModal";
import { NewsRail } from "./components/NewsRail";
import { NewsPage } from "./components/NewsPage";
import { ChangelogModal } from "./components/ChangelogModal";
import { GameUpdatePanel } from "./components/GameUpdatePanel";
import { getNews, markReleaseSeen, type NewsPost } from "./services/newsGateway";
import "./news-update.css";
import { CompanionProfile } from "./components/CompanionProfile";
import { LoginModal } from "./components/LoginModal";
import { RegisterModal } from "./components/RegisterModal";
import { ServerCard } from "./components/ServerCard";
import { SettingsPanel } from "./components/SettingsPanel";

import { getServerStatus, unavailableStatus } from "./services/serverStatusGateway";
import { authGateway, type ProfileSettings } from "./services/authGateway";

import { gameGateway } from "./services/gameGateway";

type Page = "home" | "news" | "profile" | "about";

export default function App() {
  const [official,setOfficial]=useState<ProfileResult|null>(null);
  const [profileError,setProfileError]=useState(false);
  const [server, setServer] = useState({ ...unavailableStatus, label: "Проверяем сервер…" });
  const [loginName, setLoginName] = useState("");
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
        const value = readLook(accountName && account ? account : "");
        const motion = localStorage.getItem("recreated.motion");
        minimal = motion === null ? value.minimal === true : motion === "minimal";
        colour = value.colour;
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
  const { game, update, lastAction, run } = useGameJob();
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
        setServer(status);
        timer = setTimeout(refresh, document.hidden ? 60000 : 15000);
      }
    }
    void refresh();
    return () => { active = false; clearTimeout(timer); };
  }, []);

  useEffect(() => {
    let active = true;
    void authGateway.restore().then(account => {
      if (active && account) handleAuthenticated(account.username);
    }).catch(() => { /* Fail closed: no offline/cached promotion to an authenticated account. */ });
    return () => { active = false; };
  }, []);

  useEffect(()=>{
    if(!accountName){setOfficial(null);return;}let active=true;let timer:ReturnType<typeof setTimeout>;
    async function refresh(){try{const result=await loadProfile();if(active){setOfficial(result);setProfileError(false);}}catch{if(active)setProfileError(true);}if(active)timer=setTimeout(refresh,60000);}
    setOfficial(null);void refresh();return()=>{active=false;clearTimeout(timer);};
  },[accountName]);
  function handlePlay(action: GameAction) {
    if (!authenticated) {
      setLoginOpen(true);
      return;
    }
    if (action === "launch" && launcherUpdateRequired) return;
    void run(action);
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


  const activeProfile = {
    id: accountName ?? "",
    verified: authenticated,
    subtitle: authenticated ? `Игровой ник: ${official?.profile.username??accountName}` : "",
    role: authenticated ? official ? official.profile.cityState==="UNKNOWN"?"Ожидает синхронизации":official.profile.role?roleLabel(official.profile.role):"Без городской роли" : "Загрузка…" : "",
    joinedAt: authenticated ? official ? new Date(official.profile.registeredAt).toLocaleDateString("ru-RU") : "Загрузка…" : "",
    playtime: authenticated ? official ? playtime(official.profile.playTicks) : "Загрузка…" : "",
    displayName: authenticated ? profileSettings.displayName || official?.profile.username || accountName || "Игрок" : "Гость",
    initials: authenticated ? profileSettings.displayName.slice(0, 2).toUpperCase() : "",
    accountId: authenticated ? authGateway.getPlatformUserId() ?? "" : "",
    city: authenticated ? official ? official.profile.cityState==="UNKNOWN"?"Ожидает синхронизации":official.profile.city||"Без города" : profileError?"Нет соединения":"Загрузка…" : "",
    skinData:official?.skinData,
    syncLabel:profileError?"Не удалось обновить данные · показаны последние полученные":official?`Синхронизация ${official.profile.cityState==="CURRENT"?"актуальна":"ожидается"}${official.profile.cityObservedAt?" · "+new Date(official.profile.cityObservedAt).toLocaleTimeString("ru-RU"):""}`:"Получаем данные игрового сервера…",
  };

  return (
    <div className="app-shell companion-shell" style={{ "--hero-image": `url(${cityArt})` } as React.CSSProperties}>
      <aside className="companion-sidebar">
        <BrandMark />

        <nav aria-label="Основная навигация" style={{ "--active-row": ["home", "news", "profile", "about"].indexOf(page) } as React.CSSProperties}>
          <button aria-label="Главная страница" aria-current={page==="home"?"page":undefined} className={page==="home"?"is-active":""} onClick={()=>navigate("home")}><House size={20}/><span>Главная</span></button>
          <button aria-current={page==="news"?"page":undefined} className={page==="news"?"is-active":""} onClick={()=>navigate("news")}><Newspaper size={20}/><span>Новости</span></button>
          <button aria-current={page==="profile"?"page":undefined} className={page==="profile"?"is-active":""} onClick={()=>navigate("profile")}><UserRound size={20}/><span>Профиль</span></button>
          <button aria-current={page==="about"?"page":undefined} className={page==="about"?"is-active":""} onClick={()=>navigate("about")}><Info size={20}/><span>О сервере</span></button>
        </nav>
        <div className="companion-sidebar__bottom">
          <span className="companion-sidebar__caption">Сообщество</span><CommunityLinks/>
          <button className="companion-settings" onClick={()=>setSettingsOpen(true)}><Cog size={20}/><span>Настройки</span></button>
          {authenticated && <button className="companion-account" onClick={()=>authenticated?navigate("profile"):setLoginOpen(true)}>
            <span className="companion-account__avatar">{authenticated?<PlayerAvatar skin={activeProfile.skinData} initials={activeProfile.initials}/>:<UserRound size={23}/>}</span>
            <span><strong>{authenticated?activeProfile.displayName:"Войти в аккаунт"}</strong><small>{authenticated?"Профиль RE:CREATED":"Для запуска игры"}</small></span>
          </button>}
        </div>
      </aside>
      <header className="titlebar" data-tauri-drag-region>
        <div className="companion-breadcrumb"><span>RE:CREATED /</span><strong>{({home:"Главная",news:"Новости",profile:"Профиль",about:"О сервере"})[page]}</strong></div>
        <WindowControls />
      </header>


      <main className={`companion-main ${page !== "home" ? "main--inner" : ""}`}>
        <div className="page-transition" key={page}>
        {page === "home" ? <>
        <section className="world-cover">
          <span>Minecraft 1.21.1 <i/> Create · Alpha</span>
          <h1>{authenticated ? "Мой сервер" : "RE:CREATED"}</h1>
          <p>{authenticated ? `С возвращением, ${activeProfile.displayName}` : "Города, промышленность и железные дороги в мире игроков."}</p>
        </section>
        <div className="world-overview">
          <ServerCard status={server}/>
          {authenticated ? <button className="world-identity" onClick={() => navigate("profile")}>
            <PlayerAvatar skin={activeProfile.skinData} initials={activeProfile.initials}/>
            <span><strong>{activeProfile.displayName}</strong><small>{activeProfile.city}</small></span><span aria-hidden="true">›</span>
          </button> : <section className="first-visit"><BookOpen size={20}/><div><h2>Первый визит</h2><p>Для регистрации понадобится альфа-ключ и Telegram.</p>
            <button className="text-button" onClick={() => setRegisterOpen(true)}>Создать аккаунт →</button></div></section>}
        </div>
        <NewsRail items={news} loading={newsLoading} error={newsError} onOpenPost={setChangelog} onOpenNews={() => navigate("news")}/>
        </> : page === "news" ? <NewsPage items={news} loading={newsLoading} error={newsError} onRetry={() => setNewsAttempt(n => n + 1)} onBack={() => navigate("home")} /> : page==="profile" ? <>
          <div className="companion-page-heading"><div><span className="eyebrow">RE:CREATED Companion</span><h1>Профиль игрока</h1></div></div>
          {authenticated?<CompanionProfile registeredAt={official?.profile.registeredAt} profile={activeProfile} onEdit={()=>setEditProfileOpen(true)} onLogout={()=>void handleLogout()}/>:<section className="companion-guest"><UserRound size={32}/><h2>Здесь будет твой персонаж</h2><p>Город, роль и время в игре поступают с сервера после входа.</p><button onClick={()=>setLoginOpen(true)}>Войти в аккаунт</button></section>}
        </> : <AboutPage onBack={() => navigate("home")} />}
        </div>
      </main>
      {updateOpen && <GameUpdatePanel progress={update} game={game}
        retryLabel={lastAction === "check" || launcherUpdateRequired ? "Повторить проверку сборки" : "Повторить запуск"}
        onRetry={() => handlePlay(launcherUpdateRequired ? "check" : lastAction)} onClose={() => setUpdateOpen(false)} />}
      <ChangelogModal post={changelog} onClose={() => { if (changelog?.releaseVersion) markReleaseSeen(changelog.id); setChangelog(null); }} />
      <LaunchBar authenticated={authenticated} game={game} update={update} required={launcherUpdateRequired}
        lastAction={lastAction} onAction={handlePlay} onDetails={() => setUpdateOpen(true)}>
        <LauncherUpdate onRequired={setLauncherUpdateRequired} />
      </LaunchBar>

      <LoginModal
        open={loginOpen} initialUsername={loginName}
        onClose={() => setLoginOpen(false)}
        onSuccess={() => { const username = authGateway.getSessionUsername(); if (username) handleAuthenticated(username); }}
        onRegister={() => { setLoginOpen(false); setRegisterOpen(true); }}
      />
      <RegisterModal
        open={registerOpen}
        onClose={() => setRegisterOpen(false)}
        onLogin={() => { setRegisterOpen(false); setLoginOpen(true); }}
        onSuccess={username => { setLoginName(username); setRegisterOpen(false); setLoginOpen(true); }}
      />
      <EditProfileModal
        open={editProfileOpen && authenticated}
        account={activeProfile.accountId}
        username={accountName ?? ""}
        initialSettings={profileSettings}
        onClose={() => setEditProfileOpen(false)}
        onSaved={(settings) => { setProfileSettings(settings); setEditProfileOpen(false); }}
      />
      <TelemetrySurvey enabled={authenticated && game.state!=="RUNNING" && game.state!=="STARTING"}/>
      <SettingsPanel open={settingsOpen} onClose={() => setSettingsOpen(false)} busy={launchState==="STARTING"||launchState==="RUNNING"} onRepair={()=>{
        setSettingsOpen(false);
        if (!authenticated) { setLoginOpen(true); return; }
        setUpdateOpen(true);
        handlePlay("check");
      }}/>
    </div>
  );
}
