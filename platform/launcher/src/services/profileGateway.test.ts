import {describe,it,expect,vi,beforeEach} from "vitest";
import {loadProfile,roleLabel,playtime} from "./profileGateway";
const api=vi.hoisted(()=>({invoke:vi.fn()}));vi.mock("@tauri-apps/api/core",()=>api);
beforeEach(()=>vi.clearAllMocks());
describe("server-authoritative profile",()=>{
 it("uses only owner native IPC and accepts explicit missing data",async()=>{const profile={minecraftUuid:null,username:"Player",registeredAt:"2026-10-17T00:00:00Z",city:"",role:"",cityState:"UNKNOWN",cityObservedAt:null,playTicks:null,statsObservedAt:null,skinUrl:null};api.invoke.mockResolvedValue({profile,skinData:null});expect((await loadProfile()).profile.cityState).toBe("UNKNOWN");expect(api.invoke).toHaveBeenCalledWith("launcher_profile");});
 it("does not promote invalid remote profile or external avatar",async()=>{api.invoke.mockResolvedValue({profile:{username:"Player"}});await expect(loadProfile()).rejects.toThrow();});
 it("formats only real playtime and known city roles",()=>{expect(playtime(null)).toBe("Ещё нет данных");expect(playtime(72000)).toBe("1 ч 0 мин");expect(roleLabel("TREASURER")).toBe("Казначей");});
});
