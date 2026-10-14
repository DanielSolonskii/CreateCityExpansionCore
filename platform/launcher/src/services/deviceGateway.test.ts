import {afterEach,beforeEach,expect,it,vi} from "vitest";
const {invoke}=vi.hoisted(()=>({invoke:vi.fn()}));
vi.mock("@tauri-apps/api/core",()=>({invoke}));
import {deviceGateway} from "./deviceGateway";
beforeEach(()=>{ invoke.mockReset(); Object.defineProperty(window,"__TAURI_INTERNALS__",{configurable:true,value:{}}); localStorage.clear(); sessionStorage.clear(); });
afterEach(()=>{ delete (window as unknown as Record<string,unknown>).__TAURI_INTERNALS__; });
it("uses native enrollment and never retains secret-shaped fields in WebView storage",async()=>{
  const safe={telegramDeepLink:`https://t.me/create_test_bot?start=d_${"v".repeat(43)}`,expiresAt:"2099-01-01T00:00:00Z"};
  invoke.mockResolvedValue({status:201,body:{...safe,deviceCredential:"should-not-return",pollingToken:"should-not-return"}});
  expect(await deviceGateway.begin(" engineer ","test-password-123")).toEqual(safe);
  expect(invoke).toHaveBeenCalledWith("device_request",{operation:"begin",username:"engineer",password:"test-password-123"});
  expect(localStorage.length).toBe(0); expect(sessionStorage.length).toBe(0);
});
it("pending is not login, restore requires a valid remote account and logout uses native command",async()=>{
  invoke.mockResolvedValueOnce({status:200,body:{state:"PENDING"}}).mockResolvedValueOnce({status:200,body:{status:"AUTHENTICATED",username:"engineer",platformUserId:"12345678-1234-1234-1234-123456789abc"}}).mockResolvedValueOnce({status:200,body:{loggedOut:true}});
  expect(await deviceGateway.poll()).toBeNull(); expect((await deviceGateway.restore())?.username).toBe("engineer");
  await deviceGateway.logout(); expect(invoke).toHaveBeenLastCalledWith("device_request",{operation:"logout",username:undefined,password:undefined});
});
it("revocation, expiry, outages and browser-only runtime fail closed without disclosing errors",async()=>{
  invoke.mockResolvedValueOnce({status:401,body:{code:"INVALID_DEVICE"}}).mockResolvedValueOnce({status:410,body:{code:"DEVICE_EXPIRED"}}).mockRejectedValueOnce(new Error("secret-private-diagnostic"));
  await expect(deviceGateway.restore()).rejects.toMatchObject({code:"INVALID_DEVICE"});
  await expect(deviceGateway.poll()).rejects.toMatchObject({code:"DEVICE_EXPIRED"});
  await expect(deviceGateway.restore()).rejects.toMatchObject({code:"NETWORK_ERROR"});
  delete (window as unknown as Record<string,unknown>).__TAURI_INTERNALS__;
  await expect(deviceGateway.begin("engineer","test-password-123")).rejects.toMatchObject({code:"NATIVE_REQUIRED"});
});
