import {cleanup,fireEvent,render,screen,waitFor} from '@testing-library/react';
import {afterEach,beforeEach,expect,it,vi} from 'vitest';
import {invoke,isTauri} from '@tauri-apps/api/core';
import {CommunityLinks} from './CommunityLinks';
vi.mock('@tauri-apps/api/core',()=>({invoke:vi.fn(),isTauri:vi.fn()}));
beforeEach(()=>{vi.mocked(isTauri).mockReturnValue(true);vi.mocked(invoke).mockResolvedValue(undefined);});
afterEach(()=>{cleanup();vi.clearAllMocks();});
it('opens only fixed native destination keys, never arbitrary URLs',async()=>{render(<CommunityLinks/>);fireEvent.click(screen.getByRole('button',{name:/Открыть Discord/}));fireEvent.click(screen.getByRole('button',{name:/Открыть Telegram/}));await waitFor(()=>expect(invoke).toHaveBeenCalledWith('open_community',{kind:'discord'}));expect(invoke).toHaveBeenCalledWith('open_community',{kind:'telegram'});});
it('shows opening failures instead of claiming success',async()=>{vi.mocked(invoke).mockRejectedValue(new Error('failed'));render(<CommunityLinks/>);fireEvent.click(screen.getByRole('button',{name:/Открыть Discord/}));expect(await screen.findByRole('alert')).toBeTruthy();});
