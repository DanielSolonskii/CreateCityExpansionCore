import {render,screen,fireEvent,waitFor,cleanup} from "@testing-library/react";
import {afterEach,it,expect,vi} from "vitest";
import {SettingsPanel} from "./SettingsPanel";
const api=vi.hoisted(()=>({load:vi.fn(),save:vi.fn(),openFolder:vi.fn()}));
vi.mock("../services/launcherSettings",()=>({launcherSettings:api}));
afterEach(()=>{cleanup();vi.clearAllMocks();});
it("loads native settings, saves real launch options and opens the actual folder",async()=>{
  api.load.mockResolvedValue({memoryGb:6,width:1600,height:900,hideAfterLaunch:true});api.save.mockResolvedValue(undefined);api.openFolder.mockResolvedValue(undefined);
  render(<SettingsPanel open onClose={()=>{}}/>);
  await waitFor(()=>expect((screen.getByLabelText("Память для игры") as HTMLInputElement).value).toBe("6"));
  fireEvent.change(screen.getByLabelText("Память для игры"),{target:{value:"8"}});
  fireEvent.change(screen.getByLabelText("Разрешение"),{target:{value:"1920x1080"}});
  fireEvent.click(screen.getByText("Сохранить настройки"));
  await waitFor(()=>expect(api.save).toHaveBeenCalledWith({memoryGb:8,width:1920,height:1080,hideAfterLaunch:true,autoStart:true,diagnosticsEnabled:true}));
  fireEvent.click(screen.getByText("Папка игры"));expect(api.openFolder).toHaveBeenCalledOnce();
});
it("does not claim saved settings when native storage fails",async()=>{
  api.load.mockResolvedValue({memoryGb:4,width:1280,height:720,hideAfterLaunch:false});api.save.mockRejectedValue(new Error("private"));
  render(<SettingsPanel open onClose={()=>{}}/>);await waitFor(()=>expect((screen.getByLabelText("Память для игры") as HTMLInputElement).disabled).toBe(false));
  fireEvent.click(screen.getByText("Сохранить настройки"));await waitFor(()=>expect(screen.getByRole("status").textContent).toContain("Не удалось сохранить"));
});
