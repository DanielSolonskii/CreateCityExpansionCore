import { Eye, EyeOff, LockKeyhole, TicketCheck, UserRound, X } from "lucide-react";
import { FormEvent, useEffect, useRef, useState } from "react";
import { accountApi, AccountError, errorMessage, type Registration } from "../services/accountApi";
import { QRCodeSVG } from "qrcode.react";
import "../phone-verification.css";

export function RegisterModal({ open, onClose, onLogin, onSuccess }: {
  open: boolean; onClose: () => void; onLogin: () => void; onSuccess: (username: string) => void;
}) {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [alphaKey, setAlphaKey] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [registration, setRegistration] = useState<Registration | null>(null);
  const [phase, setPhase] = useState<"form" | "pending" | "completed" | "expired">("form");
  const [retry, setRetry] = useState(0);
  const [copied, setCopied] = useState(false);
  const active = useRef<AbortController | null>(null);

  useEffect(() => {
    setError("");
    if (!open) {
      active.current?.abort(); setPassword(""); setAlphaKey(""); setRegistration(null); setPhase("form"); setLoading(false);
    }
    return () => { active.current?.abort(); };
  }, [open]);

  useEffect(() => {
    if (!open || !registration || phase !== "pending") return;
    const controller = new AbortController();
    let timer: ReturnType<typeof setTimeout>;
    async function poll() {
      try {
        const status = await accountApi.status(registration!, controller.signal);
        if (controller.signal.aborted) return;
        setError("");
        if (status.state === "COMPLETED") { setRegistration(null); setPhase("completed"); return; }
        if (status.state === "EXPIRED") {
          setPhase("expired"); setError(new AccountError("REGISTRATION_EXPIRED").message); return;
        }
        timer = setTimeout(() => void poll(), 3000);
      } catch (error) {
        if (controller.signal.aborted) return;
        setError(errorMessage(error));
        if (error instanceof AccountError && [400, 404, 410].includes(error.status)) {
          setPhase("expired");
        } else if (error instanceof AccountError && error.status === 429) {
          timer = setTimeout(() => void poll(), 15000);
        }
      }
    }
    void poll();
    return () => { controller.abort(); clearTimeout(timer); };
  }, [open, registration, phase, retry]);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (loading) return;
    setLoading(true); setError("");
    active.current?.abort();
    const controller = new AbortController();
    active.current = controller;
    try {
      const result = await accountApi.register(username, password, alphaKey, controller.signal);
      if (!controller.signal.aborted) { setRegistration(result); setPhase("pending"); }
    } catch (error) {
      if (!controller.signal.aborted) setError(errorMessage(error));
    } finally {
      if (!controller.signal.aborted) { setLoading(false); setPassword(""); setAlphaKey(""); }
    }
  }
  async function restart() {
    if (!registration || loading) return;
    setLoading(true); setError(""); setCopied(false);
    const controller = new AbortController(); active.current?.abort(); active.current = controller;
    try {
      const result = await accountApi.restart(registration, controller.signal);
      if (!controller.signal.aborted) { setRegistration(result); setPhase("pending"); }
    } catch (failure) { if (!controller.signal.aborted) setError(errorMessage(failure)); }
    finally { if (!controller.signal.aborted) setLoading(false); }
  }
  async function copyCode() {
    if (!registration) return;
    try { await navigator.clipboard.writeText(registration.verificationCode); setCopied(true); }
    catch { setError("Не удалось скопировать. Введите код с экрана вручную."); }
  }

  return (
    <div className={`login-layer ${open ? "is-open" : ""}`} aria-hidden={!open}>
      <button className="login-backdrop" onClick={onClose} aria-label="Закрыть регистрацию" tabIndex={open ? 0 : -1} />
      <section className="login-modal register-modal" role="dialog" aria-modal="true" aria-label="Регистрация в RE:CREATED">
        <button className="login-close" onClick={onClose} aria-label="Закрыть"><X size={18} /></button>
        <div className="login-emblem register-emblem"><TicketCheck size={25} /><i /><i /></div>
        <span className="eyebrow">CREATE SERVER ACCOUNT</span>
        <h2>Стать частью<br /><em>RE:CREATED</em></h2>
        <p>{phase === "form" ? "Введите альфа-ключ, создайте аккаунт и подтвердите его через Telegram. Ключ одноразовый." :
          phase === "completed" ? "Telegram подтверждён. Теперь войдите с вашим логином и паролем." :
          phase === "expired" ? "Получите новый QR/код для этого аккаунта. Если вы уже подтвердили аккаунт, попробуйте вход." :
          "Откройте бота на телефоне и подтвердите регистрацию. Telegram и VPN на компьютере не нужны."}</p>
        {phase === "form" && <form onSubmit={submit}>
          <label><span>АЛЬФА-КЛЮЧ</span><div><TicketCheck size={17} /><input aria-label="Альфа-ключ" type="password" autoComplete="off" spellCheck={false} value={alphaKey} onChange={event => setAlphaKey(event.target.value)} placeholder="Ваш одноразовый альфа-ключ" disabled={loading} /></div></label>
          <label><span>ЛОГИН АККАУНТА</span><div><UserRound size={17} /><input autoFocus={open} autoComplete="username" value={username} onChange={event => setUsername(event.target.value)} placeholder="3–32 символа, начиная с буквы" disabled={loading} /></div></label>
          <label><span>ПАРОЛЬ</span><div><LockKeyhole size={17} /><input type={showPassword ? "text" : "password"} autoComplete="new-password" value={password} onChange={event => setPassword(event.target.value)} placeholder="Минимум 12 символов" disabled={loading} /><button type="button" onClick={() => setShowPassword(value => !value)} aria-label="Показать пароль">{showPassword ? <EyeOff size={16} /> : <Eye size={16} />}</button></div></label>
          <div className={`login-error register-error ${error ? "is-visible" : ""}`} role="alert">{error || "Подтверждение обязательно"}</div>
          <button className="login-submit" disabled={loading || !username || !password || !alphaKey.trim()}>{loading ? <span className="login-spinner" /> : <TicketCheck size={18} />}{loading ? "Создаём аккаунт…" : "Создать профиль"}</button>
        </form>}
        {phase === "pending" && registration && <>
          <div className="phone-verification">
            <QRCodeSVG value={registration.telegramDeepLink} size={176} marginSize={4} level="M" role="img" aria-label="QR-код подтверждения регистрации" />
            <strong className="verification-code">{registration.verificationCode}</strong>
            <button className="auth-switch" onClick={() => void copyCode()}>{copied ? "Код скопирован" : "Скопировать код"}</button>
          </div>
          <small className="login-note">Отсканируйте QR телефоном. Или откройте @{new URL(registration.telegramDeepLink).pathname.slice(1)} и отправьте /code {registration.verificationCode}. Нажмите «Подтвердить регистрацию» в боте.</small>
          <small className="login-note">QR/код действуют до {new Date(registration.expiresAt).toLocaleTimeString()}. Не пересылайте их. Не закрывайте это окно до подтверждения.</small>
          <div className={`login-error register-error ${error ? "is-visible" : ""}`} role="alert">{error || "Ожидаем подтверждение…"}</div>
          {error && <button className="auth-switch" onClick={() => { setError(""); setRetry(value => value + 1); }}>Повторить проверку</button>}
        </>}
        {phase === "completed" && <button className="login-submit" onClick={() => onSuccess(username)}>Перейти ко входу</button>}
        {phase === "expired" && <>
          <div className="login-error is-visible" role="alert">{error}</div>
          {registration && <button className="login-submit" disabled={loading} onClick={() => void restart()}>{loading ? "Обновляем…" : "Получить новый QR/код"}</button>}
        </>}
        <button className="auth-switch" onClick={onLogin}>Уже зарегистрированы? <strong>Войти</strong></button>
        <small className="login-note">Пароль не сохраняется. Telegram username не является идентификатором аккаунта.</small>
      </section>
    </div>
  );
}
