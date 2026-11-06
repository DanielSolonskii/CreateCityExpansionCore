import {useState} from 'react';
import {invoke,isTauri} from '@tauri-apps/api/core';
import {MessageCircle,Send} from 'lucide-react';
const links={discord:'https://discord.gg/Qcks6wsBUX',telegram:'https://t.me/recreating_bot'};
export function CommunityLinks(){
 const [error,setError]=useState('');
 async function open(kind:keyof typeof links){setError('');try{if(isTauri())await invoke('open_community',{kind});else window.open(links[kind],'_blank','noopener,noreferrer');}catch{setError('Не удалось открыть ссылку. Попробуйте ещё раз.');}}
 return <div className="community-links"><div><button title="Discord · Сообщество" aria-label="Открыть Discord — Сообщество" onClick={()=>void open('discord')}><MessageCircle size={18}/><span>Discord</span></button><button title="Telegram · Бот и помощь" aria-label="Открыть Telegram — Бот и помощь" onClick={()=>void open('telegram')}><Send size={18}/><span>Telegram</span></button></div>{error&&<small role="alert">{error}</small>}</div>;
}
