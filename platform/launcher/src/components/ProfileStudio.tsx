import { useEffect, useState } from "react";
import { readLook } from "../services/profileLook";

export function ProfileStudio({ account, registeredAt }: { account: string; registeredAt?: string }) {
  const [look, setLook] = useState(() => readLook(account));
  useEffect(() => {
    const refresh = () => setLook(readLook(account));
    refresh();
    window.addEventListener("profile-appearance", refresh);
    return () => window.removeEventListener("profile-appearance", refresh);
  }, [account]);
  const days = registeredAt ? Math.floor((Date.now() - Date.parse(registeredAt)) / 86400000) : NaN;
  return <div className="profile-studio">
    {Number.isFinite(days) && days >= 0 && <small>В RE:CREATED уже {days} дн.</small>}
    {look.bio && <p>{look.bio}</p>}
    {look.interests && <p>Интересы · {look.interests}</p>}
  </div>;
}
