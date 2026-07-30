import { useEffect, useRef, useState } from "react";
import { ChevronDown, ListMusic, Music2, Pause, Play, SkipBack, SkipForward, Upload, Volume2, X } from "lucide-react";

type Track={name:string;artist:string;url:string;local?:boolean};
const format=(seconds:number)=>Number.isFinite(seconds)?`${Math.floor(seconds/60)}:${String(Math.floor(seconds%60)).padStart(2,"0")}`:"0:00";

export default function Rain7MusicPlayer(){
 const audio=useRef<HTMLAudioElement>(null);const input=useRef<HTMLInputElement>(null);const[tracks,setTracks]=useState<Track[]>([]);const[index,setIndex]=useState(0);const[playing,setPlaying]=useState(false);const[expanded,setExpanded]=useState(false);const[current,setCurrent]=useState(0);const[duration,setDuration]=useState(0);const[volume,setVolume]=useState(.7);
 const track=tracks[index];
 useEffect(()=>{fetch("/rain7/music/playlist.json").then(response=>response.ok?response.json():[]).then(value=>Array.isArray(value)&&setTracks(value)).catch(()=>undefined)},[]);
 useEffect(()=>{if(!audio.current)return;audio.current.volume=volume;if(track){audio.current.load();if(playing)audio.current.play().catch(()=>setPlaying(false));}},[index,track?.url]);
 useEffect(()=>()=>tracks.filter(item=>item.local).forEach(item=>URL.revokeObjectURL(item.url)),[]);
 const toggle=()=>{if(!track){input.current?.click();return}const player=audio.current;if(!player)return;if(player.paused)player.play().then(()=>setPlaying(true)).catch(()=>setPlaying(false));else{player.pause();setPlaying(false)}};
 const select=(next:number)=>{setIndex(next);setPlaying(true)};const move=(step:number)=>tracks.length&&select((index+step+tracks.length)%tracks.length);
 const addFiles=(files:FileList|null)=>{if(!files?.length)return;const added=[...files].map(file=>({name:file.name.replace(/\.[^.]+$/,"") ,artist:"本地音乐",url:URL.createObjectURL(file),local:true}));setTracks(currentTracks=>[...currentTracks,...added]);setIndex(tracks.length);setPlaying(true);setExpanded(true)};
 return <div className={`music-player ${expanded?"expanded":""}`} id="rain7-music-player"><audio ref={audio} src={track?.url} onTimeUpdate={event=>setCurrent(event.currentTarget.currentTime)} onLoadedMetadata={event=>setDuration(event.currentTarget.duration)} onEnded={()=>move(1)}/><input ref={input} hidden type="file" accept="audio/*,.mp3,.wav,.m4a,.ogg,.flac" multiple onChange={event=>addFiles(event.target.files)}/>
  <button className="music-disc" onClick={()=>setExpanded(true)} aria-label="打开音乐播放器"><Music2/></button>
  <section><header><div><Music2/><span><b>{track?.name||"Rain7 音乐"}</b><small>{track?.artist||"选择本地音乐开始播放"}</small></span></div><button onClick={()=>setExpanded(false)} aria-label="收起"><ChevronDown/></button></header>
   <div className="music-progress"><input type="range" min="0" max={duration||0} step=".1" value={current} onChange={event=>{const value=Number(event.target.value);if(audio.current)audio.current.currentTime=value;setCurrent(value)}}/><span>{format(current)} / {format(duration)}</span></div>
   <div className="music-controls"><button onClick={()=>move(-1)} disabled={!tracks.length}><SkipBack/></button><button className="music-play" onClick={toggle}>{playing?<Pause/>:<Play/>}</button><button onClick={()=>move(1)} disabled={!tracks.length}><SkipForward/></button><label><Volume2/><input type="range" min="0" max="1" step=".05" value={volume} onChange={event=>{const value=Number(event.target.value);setVolume(value);if(audio.current)audio.current.volume=value}}/></label><button onClick={()=>input.current?.click()} title="添加本地音乐"><Upload/></button></div>
   <div className="music-list"><h4><ListMusic/>播放列表 <button onClick={()=>input.current?.click()}>添加音乐</button></h4>{!tracks.length&&<p>支持 MP3、WAV、M4A、OGG 等本地音频，文件只在当前浏览器播放，不会上传。</p>}{tracks.map((item,itemIndex)=><button className={itemIndex===index?"active":""} onClick={()=>select(itemIndex)} key={`${item.url}-${itemIndex}`}><span>{item.name}<small>{item.artist}</small></span>{item.local&&<X onClick={event=>{event.stopPropagation();URL.revokeObjectURL(item.url);setTracks(values=>values.filter((_,position)=>position!==itemIndex));setIndex(0)}}/>}</button>)}</div>
  </section></div>;
}