import { act, cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import { accountApi, AccountError } from "../services/accountApi";
import { RegisterModal } from "./RegisterModal";
import { LoginModal } from "./LoginModal";
import { authGateway } from "../services/authGateway";
import { deviceGateway } from "../services/deviceGateway";
const registration = { registrationId: "12345678-1234-1234-1234-123456789abc", pollingToken: "p".repeat(43), telegramDeepLink: `https://t.me/recreating_bot?start=${"v".repeat(43)}`, expiresAt: "2099-01-01T00:00:00Z", verificationCode: "ABCDE-FGHJK" };
afterEach(() => { cleanup(); vi.restoreAllMocks(); vi.useRealTimers(); });
it("first login shows phone QR, clears password and waits for explicit device confirmation", async () => {
  vi.useFakeTimers();
  const enrollment={ telegramDeepLink:`https://t.me/recreating_bot?start=d_${"v".repeat(43)}`, expiresAt:"2099-01-01T00:00:00Z" };
  vi.spyOn(authGateway,"login").mockResolvedValue(enrollment);
  const polling=vi.spyOn(authGateway,"finishDevice").mockResolvedValueOnce(null).mockResolvedValueOnce({status:"AUTHENTICATED",username:"engineer",platformUserId:registration.registrationId});
  vi.spyOn(deviceGateway,"cancel").mockResolvedValue(undefined);
  const success=vi.fn();
  render(<LoginModal open onClose={vi.fn()} onRegister={vi.fn()} onSuccess={success} />);
  fireEvent.change(screen.getByPlaceholderText("Имя пользователя"),{target:{value:"engineer"}});
  fireEvent.change(screen.getByPlaceholderText("••••••••"),{target:{value:"test-password-123"}});
  await act(async()=>fireEvent.click(screen.getByText("Войти в аккаунт")));
  expect(screen.getByRole("img",{name:"QR-код подтверждения устройства"})).toBeTruthy();
  expect(screen.queryByPlaceholderText("••••••••")).toBeNull();
  expect(document.body.textContent).not.toContain("v".repeat(43));
  expect(success).not.toHaveBeenCalled();
  await act(async()=>vi.advanceTimersByTimeAsync(3000));
  expect(polling).toHaveBeenCalledTimes(2); expect(success).toHaveBeenCalledTimes(1);
  expect((screen.getByPlaceholderText("••••••••") as HTMLInputElement).value).toBe("");
  expect(deviceGateway.cancel).not.toHaveBeenCalled();
});
it("expired device enrollment does not poll and cancel allows a fresh login", async()=>{
  vi.spyOn(authGateway,"login").mockResolvedValue({telegramDeepLink:`https://t.me/recreating_bot?start=d_${"v".repeat(43)}`,expiresAt:"2000-01-01T00:00:00Z"});
  const polling=vi.spyOn(authGateway,"finishDevice");
  vi.spyOn(deviceGateway,"cancel").mockResolvedValue(undefined);
  render(<LoginModal open onClose={vi.fn()} onRegister={vi.fn()} onSuccess={vi.fn()} />);
  fireEvent.change(screen.getByPlaceholderText("Имя пользователя"),{target:{value:"engineer"}});
  fireEvent.change(screen.getByPlaceholderText("••••••••"),{target:{value:"test-password-123"}});
  fireEvent.click(screen.getByText("Войти в аккаунт"));
  await waitFor(()=>expect(screen.getByText(/Запрос устройства истёк/)).toBeTruthy());
  expect(polling).not.toHaveBeenCalled();
  fireEvent.click(screen.getByText("Отменить / войти заново"));
  await waitFor(()=>expect(deviceGateway.cancel).toHaveBeenCalled());
  expect(screen.getByPlaceholderText("Имя пользователя")).toBeTruthy();
});
function fillRegistration() {
  fireEvent.change(screen.getByLabelText("Альфа-ключ"), { target: { value: "k".repeat(43) } });
  fireEvent.change(screen.getByPlaceholderText("3–32 символа, начиная с буквы"), { target: { value: "engineer" } });
  fireEvent.change(screen.getByPlaceholderText("Минимум 12 символов"), { target: { value: "test-password-123" } });
  fireEvent.click(screen.getByText("Создать профиль"));
}
it("shows Telegram link, polls PENDING to COMPLETED and requires explicit login", async () => {
  vi.useFakeTimers();
  vi.spyOn(accountApi, "register").mockResolvedValue(registration);
  const status = vi.spyOn(accountApi, "status").mockResolvedValueOnce({ state: "PENDING", expiresAt: registration.expiresAt })
    .mockResolvedValueOnce({ state: "COMPLETED", expiresAt: registration.expiresAt, platformUserId: registration.registrationId });
  const success = vi.fn();
  render(<RegisterModal open onClose={vi.fn()} onLogin={vi.fn()} onSuccess={success} />);
  await act(async () => fillRegistration());
  expect(accountApi.register).toHaveBeenCalledWith("engineer", "test-password-123", "k".repeat(43), expect.any(AbortSignal));
  expect(screen.getByRole("img", { name: "QR-код подтверждения регистрации" })).toBeTruthy();
  expect(screen.getByText(registration.verificationCode)).toBeTruthy();
  expect(document.body.textContent).not.toContain("v".repeat(43));
  expect(screen.queryByText("Открыть Telegram")).toBeNull();
  expect(success).not.toHaveBeenCalled();
  await act(async () => vi.advanceTimersByTimeAsync(3000));
  expect(status).toHaveBeenCalledTimes(2);
  fireEvent.click(screen.getByText("Перейти ко входу"));
  expect(success).toHaveBeenCalledWith("engineer");
  expect(authGateway.getSessionUsername()).toBeNull();
  await act(async () => vi.advanceTimersByTimeAsync(6000));
  expect(status).toHaveBeenCalledTimes(2);
});
it("expired verification is visible and stops polling", async () => {
  vi.spyOn(accountApi, "register").mockResolvedValue(registration);
  vi.spyOn(accountApi, "status").mockResolvedValue({ state: "EXPIRED", expiresAt: registration.expiresAt });
  render(<RegisterModal open onClose={vi.fn()} onLogin={vi.fn()} onSuccess={vi.fn()} />);
  fillRegistration();
  await waitFor(() => expect(screen.getByText(/Срок подтверждения истёк/)).toBeTruthy());
  expect(screen.queryByText("Открыть Telegram")).toBeNull();
});
it("closing verification aborts polling and never completes a hidden modal", async () => {
  vi.spyOn(accountApi, "register").mockResolvedValue(registration);
  let signal: AbortSignal | undefined;
  vi.spyOn(accountApi, "status").mockImplementation(async (_, value) => { signal = value; return new Promise(() => {}); });
  const props = { onClose: vi.fn(), onLogin: vi.fn(), onSuccess: vi.fn() };
  const view = render(<RegisterModal open {...props} />);
  fillRegistration();
  await waitFor(() => expect(signal).toBeDefined());
  view.rerender(<RegisterModal open={false} {...props} />);
  expect(signal?.aborted).toBe(true);
  expect(props.onSuccess).not.toHaveBeenCalled();
});
it("shows login backend errors and clears password", async () => {
  vi.spyOn(authGateway, "login").mockRejectedValue(new AccountError("INVALID_LOGIN", 401));
  const success = vi.fn();
  render(<LoginModal open onClose={vi.fn()} onRegister={vi.fn()} onSuccess={success} />);
  fireEvent.change(screen.getByPlaceholderText("Имя пользователя"), { target: { value: "engineer" } });
  fireEvent.change(screen.getByPlaceholderText("••••••••"), { target: { value: "test-password-123" } });
  fireEvent.click(screen.getByText("Войти в аккаунт"));
  await waitFor(() => expect(screen.getByText(/Неверный логин\/пароль/)).toBeTruthy());
  expect((screen.getByPlaceholderText("••••••••") as HTMLInputElement).value).toBe("");
  expect(success).not.toHaveBeenCalled();
});
it("network polling errors pause and the explicit retry resumes verification", async () => {
  vi.spyOn(accountApi, "register").mockResolvedValue(registration);
  const status = vi.spyOn(accountApi, "status").mockRejectedValueOnce(new AccountError("NETWORK_ERROR"))
    .mockResolvedValueOnce({ state: "COMPLETED", expiresAt: registration.expiresAt, platformUserId: registration.registrationId });
  render(<RegisterModal open onClose={vi.fn()} onLogin={vi.fn()} onSuccess={vi.fn()} />);
  fillRegistration();
  await waitFor(() => expect(screen.getByText("Повторить проверку")).toBeTruthy());
  expect(status).toHaveBeenCalledOnce();
  fireEvent.click(screen.getByText("Повторить проверку"));
  await waitFor(() => expect(screen.getByText("Перейти ко входу")).toBeTruthy());
  expect(status).toHaveBeenCalledTimes(2);
});
it("late registration response is discarded after closing the modal", async () => {
  let resolve: (value: typeof registration) => void = () => {};
  vi.spyOn(accountApi, "register").mockReturnValue(new Promise(done => { resolve = done; }));
  const status = vi.spyOn(accountApi, "status");
  const props = { onClose: vi.fn(), onLogin: vi.fn(), onSuccess: vi.fn() };
  const view = render(<RegisterModal open {...props} />);
  fillRegistration();
  view.rerender(<RegisterModal open={false} {...props} />);
  await act(async () => resolve(registration));
  view.rerender(<RegisterModal open {...props} />);
  expect(screen.queryByText("Открыть Telegram")).toBeNull();
  expect(status).not.toHaveBeenCalled();
  expect((screen.getByPlaceholderText("Минимум 12 символов") as HTMLInputElement).value).toBe("");
});
it("Backend state remains authoritative when the local clock is ahead", async () => {
  vi.spyOn(accountApi, "register").mockResolvedValue(registration);
  vi.spyOn(accountApi, "status").mockResolvedValue({ state: "PENDING", expiresAt: "2000-01-01T00:00:00Z" });
  render(<RegisterModal open onClose={vi.fn()} onLogin={vi.fn()} onSuccess={vi.fn()} />);
  fillRegistration();
  await waitFor(() => expect(screen.getByRole("img", { name: "QR-код подтверждения регистрации" })).toBeTruthy());
  expect(screen.queryByText(/Срок подтверждения истёк/)).toBeNull();
});

it("copies only the short code and restarts an expired session without another alpha key", async () => {
  const copy = vi.fn().mockResolvedValue(undefined);
  Object.defineProperty(navigator, "clipboard", { configurable: true, value: { writeText: copy } });
  vi.spyOn(accountApi, "register").mockResolvedValue(registration);
  const status = vi.spyOn(accountApi, "status").mockResolvedValue({ state: "PENDING", expiresAt: registration.expiresAt });
  const updated = { ...registration, pollingToken: "q".repeat(43), verificationCode: "23456-789AB" };
  const restart = vi.spyOn(accountApi, "restart").mockResolvedValue(updated);
  render(<RegisterModal open onClose={vi.fn()} onLogin={vi.fn()} onSuccess={vi.fn()} />);
  fillRegistration();
  await waitFor(() => expect(screen.getByText("Скопировать код")).toBeTruthy());
  fireEvent.click(screen.getByText("Скопировать код"));
  await waitFor(() => expect(screen.getByText("Код скопирован")).toBeTruthy());
  expect(copy).toHaveBeenCalledWith(registration.verificationCode);
  cleanup();
  status.mockResolvedValueOnce({ state: "EXPIRED", expiresAt: registration.expiresAt })
    .mockResolvedValue({ state: "PENDING", expiresAt: registration.expiresAt });
  render(<RegisterModal open onClose={vi.fn()} onLogin={vi.fn()} onSuccess={vi.fn()} />);
  fillRegistration();
  await waitFor(() => expect(screen.getByText("Получить новый QR/код")).toBeTruthy());
  fireEvent.click(screen.getByText("Получить новый QR/код"));
  await waitFor(() => expect(screen.getByText(updated.verificationCode)).toBeTruthy());
  expect(restart).toHaveBeenCalledWith(registration, expect.any(AbortSignal));
  expect(status).toHaveBeenLastCalledWith(updated, expect.any(AbortSignal));
  expect(JSON.stringify(localStorage)).not.toContain(updated.verificationCode);
});
it("requires alpha key and clears it with password on backend rejection", async () => {
  vi.spyOn(accountApi, "register").mockRejectedValue(new AccountError("INVALID_ALPHA_KEY", 400));
  render(<RegisterModal open onClose={vi.fn()} onLogin={vi.fn()} onSuccess={vi.fn()} />);
  expect((screen.getByText("Создать профиль") as HTMLButtonElement).disabled).toBe(true);
  fillRegistration();
  await waitFor(() => expect(screen.getByText(/Альфа-ключ недействителен/)).toBeTruthy());
  expect((screen.getByLabelText("Альфа-ключ") as HTMLInputElement).value).toBe("");
  expect((screen.getByPlaceholderText("Минимум 12 символов") as HTMLInputElement).value).toBe("");
  expect(screen.queryByText("Открыть Telegram")).toBeNull();
  expect(JSON.stringify(localStorage)).not.toContain("k".repeat(43));
});
