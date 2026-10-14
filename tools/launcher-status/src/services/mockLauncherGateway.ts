import type { LauncherGateway, LauncherSnapshot } from "../types/launcher";

const wait = (ms: number) => new Promise((resolve) => window.setTimeout(resolve, ms));

const snapshot: LauncherSnapshot = {
  profile: {
    id: "local-engineer",
    displayName: "drannch1k",
    subtitle: "Профиль подключён",
    initials: "DR",
    accountId: "create_railmaster",
    role: "Инженер-механик",
    city: "Северный узел",
    playtime: "126 ч",
    joinedAt: "12 августа 2026",
    verified: true,
  },
  status: {
    state: "unknown",
    label: "Проверяем сервер…",
    playersOnline: null,
    playersMax: null,
    ping: null,
    message: "Альфа тестирование",
  },
  build: {
    version: "0.4.2-alpha",
    minecraftVersion: "1.21.1",
    loader: "NeoForge",
    size: "2.8 ГБ",
    updateAvailable: false,
  },
  news: [
    {
      id: "rail-network",
      category: "МИР СЕРВЕРА",
      title: "Первая магистраль открыта",
      excerpt: "Северный промышленный узел теперь связан с долиной. Пора планировать грузовые маршруты.",
      date: "Сегодня",
      accent: "copper",
    },
    {
      id: "factory-update",
      category: "ОБНОВЛЕНИЕ",
      title: "Новый этап индустриализации",
      excerpt: "Подготовлены цепочки стали, энергетики и крупные городские контракты.",
      date: "28 сентября",
      accent: "teal",
    },
    {
      id: "dispatch",
      category: "ДИСПЕТЧЕРСКАЯ",
      title: "Требуются машинисты",
      excerpt: "Торговые города набирают команды для регулярных дальних перевозок.",
      date: "25 сентября",
      accent: "sand",
    },
  ],
};

export class MockLauncherGateway implements LauncherGateway {
  async getSnapshot(): Promise<LauncherSnapshot> {
    await wait(350);
    return structuredClone(snapshot);
  }

}

// Только demo snapshot. Play использует отдельный настоящий Rust gameGateway.
export const launcherGateway: LauncherGateway = new MockLauncherGateway();
