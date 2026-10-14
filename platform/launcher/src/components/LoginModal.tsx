import { Eye, EyeOff, KeyRound, LockKeyhole, ShieldCheck, UserRound, X } from "lucide-react";
import { FormEvent, useEffect, useRef, useState } from "react";
import { authGateway } from "../services/authGateway";
import { errorMessage } from "../services/accountApi";
import { deviceGateway, type DeviceEnrollment } from "../services/deviceGateway";
import { QRCodeSVG } from "qrcode.react";
import "../phone-verification.css";

export function LoginModal({
  open,
  onClose,
  onSuccess,
  onRegister,
}: {
  open: boolean;
  onClose: () => void;
  onSuccess: () => void;
  onRegister: () => void;
}) {
  const [username, setUsername] = useState(() => authGateway.getRememberedUsername());
  const [password, setPassword] = useState("");
  const [remember, setRemember] = useState(true);
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const active = useRef<AbortController | null>(null);
  const [enrollment, setEnrollment] = useState<DeviceEnrollment | null>(null);

  useEffect(() => {
    if (open) setError("");
    else { active.current?.abort(); setPassword(""); setLoading(false); setEnrollment(null); }
    return () => { active.current?.abort(); };
  }, [open]);

  useEffect(() => {
    if (!open || !enrollment) return;
    const controller = new AbortController();
    let timer: ReturnType<typeof setTimeout>;
    let completed = false;
    async function poll() {
      if (Date.now() >= Date.parse(enrollment!.expiresAt)) { setError("Запрос устройства истёк. Выполните вход заново."); return; }
      try {
        const account = await authGateway.finishDevice(controller.signal);
        if (controller.signal.aborted) return;
        if (account) { completed = true; setEnrollment(null); onSuccess(); return; }
      } catch (failure) {
        if (!controller.signal.aborted) setError(errorMessage(failure));
      }
      if (!controller.signal.aborted) timer = setTimeout(poll, 3000);
    }
    void poll();
    return () => {
      controller.abort(); clearTimeout(timer);
      if (!completed) void deviceGateway.cancel().catch(() => {});
    };
  }, [open, enrollment]);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (loading) return;
    setLoading(true);
    setError("");
    active.current?.abort();
    const controller = new AbortController();
    active.current = controller;
    try {
      const device = await authGateway.login({ username, password, remember }, controller.signal);
      if (!controller.signal.aborted) setEnrollment(device);
    } catch (error) {
      if (!controller.signal.aborted) setError(errorMessage(error));
    } finally {
      if (!controller.signal.aborted) { setPassword(""); setLoading(false); }
    }
  }

  return (
    <div className={`login-layer ${open ? "is-open" : ""}`} aria-hidden={!open}>
      <button className="login-backdrop" onClick={onClose} aria-label="Закрыть вход" tabIndex={open ? 0 : -1} />
      <section className="login-modal" role="dialog" aria-modal="true" aria-label="Вход в RE:CREATED">
        <button className="login-close" onClick={onClose} aria-label="Закрыть"><X size={18} /></button>
        <div className="login-emblem"><KeyRound size={24} /><i /><i /></div>
        <span className="eyebrow">ДОСТУП К СЕТИ</span>
        <h2>Добро пожаловать,<br /><em>инженер</em></h2>
        <p>Первый вход на этом ПК нужно подтвердить в Telegram. Затем Launcher будет входить автоматически.</p>

        {!enrollment && <form onSubmit={submit}>
          <label className={error ? "has-error" : ""}>
            <span>ЛОГИН</span>
            <div><UserRound size={17} /><input autoFocus={open} autoComplete="username" value={username} onChange={(event) => setUsername(event.target.value)} placeholder="Имя пользователя" /></div>
          </label>
          <label className={error ? "has-error" : ""}>
            <span>ПАРОЛЬ</span>
            <div><LockKeyhole size={17} /><input type={showPassword ? "text" : "password"} autoComplete="current-password" value={password} onChange={(event) => setPassword(event.target.value)} placeholder="••••••••" /><button type="button" onClick={() => setShowPassword((value) => !value)} aria-label="Показать пароль">{showPassword ? <EyeOff size={16} /> : <Eye size={16} />}</button></div>
          </label>
          <div className="login-options">
            <button type="button" className={`mock-check ${remember ? "is-checked" : ""}`} onClick={() => setRemember((value) => !value)}><i /> Запомнить логин</button>
            <button type="button" disabled title="Восстановление пароля пока недоступно">Забыли пароль?</button>
          </div>
          <div className={`login-error ${error ? "is-visible" : ""}`}>{error || "Проверьте введённые данные"}</div>
          <button className="login-submit" disabled={loading || !username || !password}>
            {loading ? <span className="login-spinner" /> : <ShieldCheck size={18} />}
            {loading ? "Проверяем доступ…" : "Войти в аккаунт"}
          </button>
        </form>}
        {enrollment && <div>
          <h3>Подтвердите устройство</h3>
          <div className="phone-verification-qr"><QRCodeSVG value={enrollment.telegramDeepLink} size={176} role="img" aria-label="QR-код подтверждения устройства" /></div>
          <p>Отсканируйте QR телефоном. В своём Telegram нажмите «Подтвердить устройство». Подтверждайте только свой запрос.</p>
          <small>Действует до {new Date(enrollment.expiresAt).toLocaleTimeString()}. Ожидаем подтверждение…</small>
          {error && <div className="login-error is-visible" role="alert">{error}</div>}
          <button className="auth-switch" onClick={() => { setEnrollment(null); setError(""); }}>Отменить / войти заново</button>
        </div>}
        <div className="mock-hint"><span>CREATE SERVER ACCOUNT</span><strong>Telegram verification</strong></div>
        <button className="auth-switch" onClick={onRegister}>Нет аккаунта? <strong>Зарегистрироваться</strong></button>
        <small className="login-note">Пароль не сохраняется. Credential устройства защищён Windows. Отзыв: Telegram → Аккаунт → Устройства. Это не игровая сессия.</small>
      </section>
    </div>
  );
}
