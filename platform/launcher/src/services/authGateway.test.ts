import { beforeEach, expect, it, vi } from "vitest";
import { deviceGateway } from "./deviceGateway";
import { AuthGateway } from "./authGateway";
beforeEach(() => { localStorage.clear(); sessionStorage.clear(); });
it("purges legacy plaintext user and fake session without promoting them to login", () => {
  localStorage.setItem("re-created.mock-user", JSON.stringify({ password: "old-password" }));
  localStorage.setItem("create-server-launcher.mock-session", "old-user");
  sessionStorage.setItem("create-server-launcher.mock-session", "old-user");
  const gateway = new AuthGateway();
  expect(gateway.getSessionUsername()).toBeNull();
  expect(localStorage.getItem("re-created.mock-user")).toBeNull();
  expect(sessionStorage.getItem("create-server-launcher.mock-session")).toBeNull();
});
it("remembers username only, keeps UI account in memory and clears it at logout", async () => {
  vi.spyOn(deviceGateway, "begin").mockResolvedValue({ telegramDeepLink: "https://t.me/recreating_bot?start=d_" + "a".repeat(43), expiresAt: "2099-01-01T00:00:00Z" });
  vi.spyOn(deviceGateway, "poll").mockResolvedValue({ status: "AUTHENTICATED", username: "engineer", platformUserId: "12345678-1234-1234-1234-123456789abc" });
  const gateway = new AuthGateway();
  await gateway.login({ username: "engineer", password: "test-password-123", remember: true });
  expect(gateway.getSessionUsername()).toBeNull();
  await gateway.finishDevice();
  expect(gateway.getSessionUsername()).toBe("engineer");
  expect(localStorage.length).toBe(1);
  expect(localStorage.getItem("create-server-launcher.remembered-username")).toBe("engineer");
  expect(new AuthGateway().getSessionUsername()).toBeNull();
  await gateway.logout();
  expect(gateway.getPlatformUserId()).toBeNull();
});
