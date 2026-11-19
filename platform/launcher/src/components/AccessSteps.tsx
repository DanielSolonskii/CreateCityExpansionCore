import { useEffect, useState } from "react";

export function AccessSteps({ step }: { step: number }) {
  return <ol className="access-steps" aria-label="Этапы доступа">
    {["Аккаунт", "Telegram", "Вход"].map((label, index) =>
      <li key={label} aria-current={step === index ? "step" : undefined} className={index < step ? "done" : ""}>{label}</li>
    )}
  </ol>;
}

export function CodeLifetime({ expiresAt }: { expiresAt: string }) {
  const expiry = Date.parse(expiresAt);
  const [now, setNow] = useState(Date.now);
  useEffect(() => {
    setNow(Date.now());
    const timer = setInterval(() => {
      const time = Date.now();
      setNow(time);
      if (time >= expiry) clearInterval(timer);
    }, 1000);
    return () => clearInterval(timer);
  }, [expiry]);
  const seconds = Math.max(0, Math.ceil((expiry - now) / 1000));
  return <p className="code-lifetime">{seconds > 0 ? `Код действует ещё ${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, "0")}` : "Срок действия кода истёк"}</p>;
}
