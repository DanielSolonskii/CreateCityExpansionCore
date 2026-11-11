import {useEffect,useRef,useState} from "react";
/** Minecraft face plus hat layer. Canvas does not load arbitrary remote images. */
export function PlayerAvatar({skin,initials}:{skin?:string|null;initials:string}){
  const ref=useRef<HTMLCanvasElement>(null);const[ready,setReady]=useState(false);
  useEffect(()=>{let active=true;setReady(false);if(!skin||!skin.startsWith("data:image/png;base64,"))return;const image=new Image();image.onload=()=>{if(!active||image.width!==64||![32,64].includes(image.height))return;const ctx=ref.current?.getContext("2d");if(!ctx)return;ctx.clearRect(0,0,64,64);ctx.imageSmoothingEnabled=false;ctx.drawImage(image,8,8,8,8,0,0,64,64);ctx.drawImage(image,40,8,8,8,0,0,64,64);setReady(true);};image.src=skin;return()=>{active=false;};},[skin]);
  return <span className="player-avatar" aria-label={ready?"Голова игрового скина":"Аватар игрока"}><canvas ref={ref} width={64} height={64} hidden={!ready}/>{!ready&&<span>{initials}</span>}</span>;
}
