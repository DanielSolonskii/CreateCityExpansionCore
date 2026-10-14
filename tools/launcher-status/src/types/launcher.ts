export type ServerState = "online" | "maintenance" | "offline" | "unknown";

export interface LauncherProfile {
  id: string;
  displayName: string;
  subtitle: string;
  initials: string;
  accountId: string;
  role: string;
  city: string;
  playtime: string;
  joinedAt: string;
  verified: boolean;
}

export interface ServerStatus {
  state: ServerState;
  label: string;
  playersOnline: number | null;
  playersMax: number | null;
  ping: number | null;
  message: string;
}

export interface BuildInfo {
  version: string;
  minecraftVersion: string;
  loader: string;
  size: string;
  updateAvailable: boolean;
}

export interface NewsItem {
  id: string;
  category: string;
  title: string;
  excerpt: string;
  date: string;
  accent: "copper" | "teal" | "sand";
}

export interface LauncherSnapshot {
  profile: LauncherProfile;
  status: ServerStatus;
  build: BuildInfo;
  news: NewsItem[];
}

export interface LauncherGateway {
  getSnapshot(): Promise<LauncherSnapshot>;
}
