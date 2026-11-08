import { cleanup,fireEvent,render,screen,waitFor } from '@testing-library/react';
import { afterEach,expect,it,vi } from 'vitest';
import { ProfilePopover } from './ProfilePopover';
import { useOverlay } from './useOverlay';
import type { LauncherProfile } from '../types/launcher';
afterEach(()=>{cleanup();vi.restoreAllMocks();});
const profile:LauncherProfile={id:'test',displayName:'engineer',subtitle:'Игровой ник: engineer',initials:'EN',accountId:'platform-id',role:'Житель',city:'Город',playtime:'2 ч',joinedAt:'Сегодня',verified:true};
it('copy failure is not reported as success; logout has only one action',async()=>{
 Object.defineProperty(navigator,'clipboard',{configurable:true,value:{writeText:vi.fn().mockRejectedValue(new Error('denied'))}});
 render(<ProfilePopover profile={profile} open onClose={()=>{}} onLogout={()=>{}} onEdit={()=>{}}/>);
 fireEvent.click(screen.getByText('Данные аккаунта'));
 fireEvent.click(screen.getByRole('button',{name:'Скопировать ID аккаунта'}));
 await screen.findByRole('alert');expect(screen.queryByText('ID скопирован')).toBeNull();
 expect(screen.queryByText('Сменить аккаунт')).toBeNull();
});
it('copy success is shown only after successful clipboard write',async()=>{
 const writeText=vi.fn().mockResolvedValue(undefined);Object.defineProperty(navigator,'clipboard',{configurable:true,value:{writeText}});
 render(<ProfilePopover profile={profile} open onClose={()=>{}} onLogout={()=>{}} onEdit={()=>{}}/>);
 fireEvent.click(screen.getByText('Данные аккаунта'));fireEvent.click(screen.getByRole('button',{name:'Скопировать ID аккаунта'}));
 await screen.findByText('ID скопирован');expect(writeText).toHaveBeenCalledWith('platform-id');
});
function Overlay({open,onClose}:{open:boolean;onClose:()=>void}){const ref=useOverlay<HTMLDivElement>(open,onClose);return <div data-testid='overlay' ref={ref}><button>Первый</button><button>Последний</button></div>;}
it('closed overlay is inert, traps Tab while open, closes on Escape and restores focus',async()=>{
 const trigger=document.createElement('button');document.body.append(trigger);trigger.focus();const close=vi.fn();
 const view=render(<Overlay open={false} onClose={close}/>);expect(screen.getByTestId('overlay').inert).toBe(true);expect(document.activeElement).toBe(trigger);
 view.rerender(<Overlay open onClose={close}/>);expect(document.activeElement).toBe(screen.getByText('Первый'));
 screen.getByText('Последний').focus();fireEvent.keyDown(document,{key:'Tab'});expect(document.activeElement).toBe(screen.getByText('Первый'));
 fireEvent.keyDown(document,{key:'Tab',shiftKey:true});expect(document.activeElement).toBe(screen.getByText('Последний'));
 fireEvent.keyDown(document,{key:'Escape'});expect(close).toHaveBeenCalledOnce();
 view.rerender(<Overlay open={false} onClose={close}/>);await waitFor(()=>expect(document.activeElement).toBe(trigger));trigger.remove();
});
