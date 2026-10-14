import { invoke } from "@tauri-apps/api/core";
export interface LauncherSettings {memoryGb:number;width:number;height:number;hideAfterLaunch:boolean}
export const defaultSettings:LauncherSettings={memoryGb:4,width:1280,height:720,hideAfterLaunch:false};
export const launcherSettings={load:()=>invoke<LauncherSettings>("launcher_settings"),save:(settings:LauncherSettings)=>invoke<void>("save_launcher_settings",{settings}),openFolder:()=>invoke<void>("open_game_folder")};
