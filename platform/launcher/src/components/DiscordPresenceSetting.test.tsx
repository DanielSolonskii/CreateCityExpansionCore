import {cleanup,fireEvent,render,screen,waitFor} from '@testing-library/react';
import {afterEach,beforeEach,expect,it,vi} from 'vitest';
import {invoke} from '@tauri-apps/api/core';
import {DiscordPresenceSetting} from './DiscordPresenceSetting';
vi.mock('@tauri-apps/api/core',()=>({invoke:vi.fn()}));
beforeEach(()=>vi.mocked(invoke).mockResolvedValue(true));afterEach(()=>{cleanup();vi.clearAllMocks();});
it('loads preference and saves an explicit opt-out without touching auth/settings',async()=>{
 render(<DiscordPresenceSetting open/>);const toggle=await screen.findByRole('switch',{name:'Активность в Discord'});
 await waitFor(()=>expect(toggle.getAttribute('aria-checked')).toBe('true'));fireEvent.click(toggle);
 await waitFor(()=>expect(invoke).toHaveBeenCalledWith('save_discord_presence',{enabled:false}));
 await waitFor(()=>expect(toggle.getAttribute('aria-checked')).toBe('false'));
});
it('does not show success when saving fails',async()=>{
 vi.mocked(invoke).mockResolvedValueOnce(true).mockRejectedValueOnce(new Error('failed'));
 render(<DiscordPresenceSetting open/>);const toggle=screen.getByRole('switch');await waitFor(()=>expect(toggle.getAttribute('aria-checked')).toBe('true'));fireEvent.click(toggle);
 expect(await screen.findByRole('alert')).toBeTruthy();expect(toggle.getAttribute('aria-checked')).toBe('true');
});
it('does not call native API while settings are closed',()=>{render(<DiscordPresenceSetting open={false}/>);expect(invoke).not.toHaveBeenCalled();});
