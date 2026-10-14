import { render, screen, cleanup } from "@testing-library/react";
import { afterEach, it, expect } from "vitest";
import { ServerCard } from "./ServerCard";
import { unavailableStatus } from "../services/serverStatusGateway";
afterEach(cleanup);
it("shows measured players and alpha label", () => {
  render(<ServerCard status={{ ...unavailableStatus, state: "online", label: "Сервер работает", playersOnline: 3, playersMax: 20, ping: 12 }} />);
  expect(screen.getByText("3")).toBeTruthy();
  expect(screen.getByText("12 мс")).toBeTruthy();
  expect(screen.getByText("Альфа тестирование")).toBeTruthy();
});
it("does not turn unknown into zero or green online", () => {
  const { container } = render(<ServerCard status={unavailableStatus} />);
  expect(screen.getByText("Игроки: нет данных")).toBeTruthy();
  expect(container.querySelector(".status-light--unknown")).toBeTruthy();
  expect(container.textContent).not.toContain("0 / 120");
});
