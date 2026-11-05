// Authlib-injector-compatible game session service. Social credentials never enter this API.
const project = Deno.env.get("SUPABASE_URL")!;
const anonKey = Deno.env.get("SUPABASE_ANON_KEY")!;
const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
const encoder = new TextEncoder();
const textResponse = (s: string, status = 200) => new Response(s, {status, headers: {"Content-Type":"application/json; charset=utf-8", "Cache-Control":"no-store"}});
const b64 = (b: Uint8Array) => btoa(Array.from(b, x => String.fromCharCode(x)).join(""));
const bytes = (s: string) => Uint8Array.from(atob(s), x => x.charCodeAt(0));
const pem = (label: string, b: Uint8Array) => `-----BEGIN ${label}-----\n${b64(b).match(/.{1,64}/g)!.join("\n")}\n-----END ${label}-----\n`;

async function rpc(name: string, body: object): Promise<unknown> {
  const r = await fetch(`${project}/rest/v1/rpc/${name}`, {method:"POST", headers:{"apikey":anonKey,"Authorization":`Bearer ${anonKey}`,"Content-Type":"application/json"}, body:JSON.stringify(body)});
  if (!r.ok) throw new Error(`Database error ${r.status}`);
  return await r.json();
}

let signingKey: Promise<{privateKey: CryptoKey, publicPem: string}> | undefined;
async function keypair() {
  if (!signingKey) signingKey = (async () => {
    const headers = {"apikey":serviceKey,"Authorization":`Bearer ${serviceKey}`,"Content-Type":"application/json"};
    async function read() {
      const r = await fetch(`${project}/rest/v1/aerix_auth_signing_key?singleton=eq.true&select=private_key,public_key`, {headers});
      if (!r.ok) throw new Error("Cannot load signing key");
      return (await r.json())[0];
    }
    let saved = await read();
    if (!saved) {
      const key = await crypto.subtle.generateKey({name:"RSASSA-PKCS1-v1_5",modulusLength:2048,publicExponent:new Uint8Array([1,0,1]),hash:"SHA-1"}, true, ["sign","verify"]);
      const priv = b64(new Uint8Array(await crypto.subtle.exportKey("pkcs8", key.privateKey)));
      const pub = b64(new Uint8Array(await crypto.subtle.exportKey("spki", key.publicKey)));
      const res = await fetch(`${project}/rest/v1/aerix_auth_signing_key?on_conflict=singleton`, {
        method:"POST",headers:{...headers,"Prefer":"resolution=ignore-duplicates,return=minimal"},
        body:JSON.stringify({singleton:true,private_key:priv,public_key:pub})
      });
      if (!res.ok) throw new Error("Cannot initialize signing key");
      saved = await read();
      if (!saved) throw new Error("Signing key unavailable");
    }
    const privateKey = await crypto.subtle.importKey("pkcs8", bytes(saved.private_key), {name:"RSASSA-PKCS1-v1_5",hash:"SHA-1"}, false, ["sign"]);
    return {privateKey,publicPem:pem("PUBLIC KEY",bytes(saved.public_key))};
  })().catch(err => { signingKey = undefined; throw err; });
  return signingKey;
}

const pngSignature = new Uint8Array([137,80,78,71,13,10,26,10]);
function crc32(data: Uint8Array): number {
  let crc = 0xffffffff;
  for (const byte of data) {
    crc ^= byte;
    for (let i=0;i<8;i++) crc = (crc >>> 1) ^ (0xedb88320 & -(crc & 1));
  }
  return (crc ^ 0xffffffff) >>> 0;
}
function pngChunk(kind: string, data: Uint8Array): Uint8Array {
  const out = new Uint8Array(data.length + 12), view = new DataView(out.buffer);
  view.setUint32(0,data.length);
  out.set(encoder.encode(kind),4); out.set(data,8);
  view.setUint32(out.length-4,crc32(out.subarray(4,out.length-4)));
  return out;
}
function concat(parts: Uint8Array[]): Uint8Array {
  const out = new Uint8Array(parts.reduce((n,p) => n+p.length,0));
  let offset=0; for (const part of parts) { out.set(part,offset); offset+=part.length; }
  return out;
}
async function streamBounded(stream: ReadableStream<Uint8Array>, max: number): Promise<Uint8Array> {
  const reader=stream.getReader(), parts:Uint8Array[]=[]; let length=0;
  try {
    while (true) {
      const {done,value}=await reader.read(); if(done) break;
      length+=value.length; if(length>max) { await reader.cancel(); throw new Error("Image too large"); }
      parts.push(value);
    }
    return concat(parts);
  } finally { reader.releaseLock(); }
}
async function sanitizePng(input: Uint8Array): Promise<Uint8Array> {
  if (input.length < 45 || input.length > 2*1024*1024 || !pngSignature.every((b,i)=>input[i]===b)) throw new Error("Invalid PNG");
  const view=new DataView(input.buffer,input.byteOffset,input.byteLength), parts:Uint8Array[]=[];
  let pos=8, ihdr:Uint8Array|undefined, palette:Uint8Array|undefined, transparency:Uint8Array|undefined;
  let done=false, idatStarted=false, idatEnded=false, count=0;
  while (pos+12<=input.length && ++count<=1024) {
    const size=view.getUint32(pos); if(size>2*1024*1024 || pos+12+size>input.length) throw new Error("Invalid chunk");
    const name=String.fromCharCode(...input.subarray(pos+4,pos+8));
    const data=input.slice(pos+8,pos+8+size);
    if(crc32(input.subarray(pos+4,pos+8+size)) !== view.getUint32(pos+8+size)) throw new Error("Bad PNG CRC");
    if (!ihdr && name!=="IHDR") throw new Error("Missing header");
    if (name!=="IDAT" && idatStarted) idatEnded=true;
    if(name==="IHDR") { if(ihdr || size!==13 || pos!==8) throw new Error("Invalid header"); ihdr=data; }
    else if(name==="PLTE") { if(palette || idatStarted || !size || size>768 || size%3) throw new Error("Invalid palette"); palette=data; }
    else if(name==="tRNS") { if(transparency || idatStarted || size>256) throw new Error("Invalid transparency"); transparency=data; }
    else if(name==="IDAT") { if(idatEnded) throw new Error("Invalid image data"); idatStarted=true; parts.push(data); }
    else if(name==="IEND") { if(size!==0 || !idatStarted || pos+12!==input.length) throw new Error("Invalid ending"); done=true; break; }
    else if ((input[pos+4] & 0x20)===0) throw new Error("Unsupported critical chunk");
    pos+=size+12;
  }
  if(!done || !ihdr) throw new Error("Incomplete PNG");
  const h=new DataView(ihdr.buffer,ihdr.byteOffset,ihdr.byteLength), w=h.getUint32(0), height=h.getUint32(4), depth=ihdr[8], color=ihdr[9];
  const allowed:Record<number,number[]>={0:[1,2,4,8,16],2:[8,16],3:[1,2,4,8],4:[8,16],6:[8,16]};
  const channels:Record<number,number>={0:1,2:3,3:1,4:2,6:4};
  if(w<1 || w>1024 || height<1 || height>1024 || !allowed[color]?.includes(depth) || ihdr[10]!==0 || ihdr[11]!==0 || ihdr[12]!==0) throw new Error("Unsupported PNG");
  if(color===3 && (!palette || palette.length/3 > 2**depth)) throw new Error("Missing palette");
  if ((color===0 || color===4) && palette) throw new Error("Invalid palette");
  if(transparency && (color===4 || color===6 || (color===0 && transparency.length!==2) || (color===2 && transparency.length!==6) || (color===3 && (!palette || transparency.length>palette.length/3)))) throw new Error("Invalid transparency");
  const rowBytes=Math.ceil(w*channels[color]*depth/8), expected=(rowBytes+1)*height;
  const compressed=concat(parts);
  const decompressed=await streamBounded(new Blob([new Uint8Array(compressed).buffer]).stream().pipeThrough(new DecompressionStream("deflate")),expected);
  if(decompressed.length!==expected) throw new Error("Invalid pixels");
  for(let i=0;i<height;i++) if(decompressed[i*(rowBytes+1)]>4) throw new Error("Invalid filter");
  const clean=await streamBounded(new Blob([new Uint8Array(decompressed).buffer]).stream().pipeThrough(new CompressionStream("deflate")),2*1024*1024);
  return concat([pngSignature,pngChunk("IHDR",ihdr),...(palette?[pngChunk("PLTE",palette)]:[]),...(transparency?[pngChunk("tRNS",transparency)]:[]),pngChunk("IDAT",clean),pngChunk("IEND",new Uint8Array())]);
}
const texturePrefix = `${project}/storage/v1/object/public/social-media/`;
async function cleanTexture(url: string): Promise<{png:Uint8Array,hash:string,path:string}> {
  if(!url.startsWith(texturePrefix)) throw new Error("Invalid texture URL");
  const path=url.slice(texturePrefix.length);
  if(!/^[0-9a-f-]{36}\/[A-Za-z0-9_-]+\.png$/.test(path)) throw new Error("Invalid texture path");
  const response=await fetch(url,{redirect:"error",signal:AbortSignal.timeout(5000)});
  if(!response.ok || !response.body || Number(response.headers.get("content-length") || "0")>2*1024*1024) throw new Error("Texture unavailable");
  const png=await sanitizePng(await streamBounded(response.body,2*1024*1024));
  const hash=Array.from(new Uint8Array(await crypto.subtle.digest("SHA-256",new Uint8Array(png).buffer)),b=>b.toString(16).padStart(2,"0")).join("");
  return {png,hash,path};
}
async function textureUrl(url: string | null): Promise<string | null> {
  if(!url) return null;
  try { const clean=await cleanTexture(url); return `${project}/functions/v1/aerix-auth/textures/${clean.path}/${clean.hash}`; }
  catch { return null; }
}

type Profile = {game_name:string, profile_id:string, slim:boolean, skin_url:string|null, cape_url:string|null};
async function profileJson(p: Profile, sign: boolean) {
  const id = p.profile_id.replaceAll("-", "");
  const textures: Record<string, unknown> = {};
  const [skin,cape] = await Promise.all([textureUrl(p.skin_url),textureUrl(p.cape_url)]);
  if (skin) textures.SKIN = {url:skin,...(p.slim ? {metadata:{model:"slim"}} : {})};
  if (cape) textures.CAPE = {url:cape};
  const value = b64(encoder.encode(JSON.stringify({timestamp:Date.now(),profileId:id,profileName:p.game_name,textures})));
  const property: Record<string,string> = {name:"textures",value};
  if (sign) property.signature = b64(new Uint8Array(await crypto.subtle.sign("RSASSA-PKCS1-v1_5", (await keypair()).privateKey, encoder.encode(value))));
  return {id,name:p.game_name,properties:[property]};
}

async function bodyJson(req: Request): Promise<unknown> {
  if (Number(req.headers.get("content-length") || "0") > 4096 || !req.body) throw new Error("Invalid body");
  const reader = req.body.getReader();
  const chunks: Uint8Array[] = [];
  let length = 0;
  while (true) {
    const {done,value} = await reader.read();
    if (done) break;
    length += value.length;
    if (length > 4096) { await reader.cancel(); throw new Error("Body too large"); }
    chunks.push(value);
  }
  const all = new Uint8Array(length);
  let offset = 0;
  for (const chunk of chunks) { all.set(chunk,offset); offset += chunk.length; }
  return JSON.parse(new TextDecoder().decode(all));
}

Deno.serve(async (req: Request) => {
  try {
    const url = new URL(req.url);
    const path = url.pathname.replace(/^.*?\/aerix-auth(?=\/|$)/, "") || "/";
    if (req.method === "GET" && path === "/") {
      return textResponse(JSON.stringify({meta:{serverName:"Aerix",implementationName:"Aerix",implementationVersion:"1", "feature.enable_profile_key":false},skinDomains:["lktupxfszfefsoyksfkg.supabase.co"],signaturePublickey:(await keypair()).publicPem}));
    }
    const tex = path.match(/^\/textures\/([0-9a-f-]{36}\/[A-Za-z0-9_-]+\.png)\/([0-9a-f]{64})$/);
    if (req.method === "GET" && tex) {
      try {
        const clean=await cleanTexture(texturePrefix+tex[1]);
        if(clean.hash!==tex[2]) return new Response(null,{status:404});
        return new Response(new Uint8Array(clean.png).buffer,{status:200,headers:{"Content-Type":"image/png","X-Content-Type-Options":"nosniff","Cache-Control":"public, max-age=3600"}});
      } catch { return new Response(null,{status:404}); }
    }
    if (req.method === "POST" && path === "/sessionserver/session/minecraft/join") {
      const b = await bodyJson(req) as Record<string, unknown>;
      if (typeof b.accessToken !== "string" || typeof b.selectedProfile !== "string" || typeof b.serverId !== "string" || b.selectedProfile.length > 36) return textResponse('{}',400);
      const ok = await rpc("aerix_game_join",{p_token:b.accessToken,p_profile:b.selectedProfile,p_server_id:b.serverId});
      return ok === true ? new Response(null,{status:204}) : textResponse('{"error":"ForbiddenOperationException","errorMessage":"Invalid or expired game session"}',403);
    }
    if (req.method === "POST" && path === "/authserver/validate") {
      const b = await bodyJson(req) as Record<string, unknown>;
      if (typeof b.accessToken !== "string") return textResponse('{}',400);
      const ok = await rpc("aerix_game_validate",{p_token:b.accessToken});
      return ok === true ? new Response(null,{status:204}) : textResponse('{"error":"ForbiddenOperationException"}',403);
    }
    if (req.method === "POST" && (path === "/api/profiles/minecraft" || path === "/minecraftservices/minecraft/profile/lookup/bulk/byname")) {
      const names = await bodyJson(req);
      if (!Array.isArray(names) || names.length > 10 || names.some(n => typeof n !== "string" || !/^[A-Za-z0-9_]{3,16}$/.test(n))) return textResponse('{}',400);
      const unique = [...new Set(names.map((s: string) => s.toLowerCase()))];
      const found = await Promise.all(unique.map(n => rpc("aerix_game_profile_by_name",{p_name:n}))) as {game_name:string,profile_id:string}[][];
      return textResponse(JSON.stringify(found.flat().map(p => ({id:p.profile_id.replaceAll("-",""),name:p.game_name}))));
    }
    const byName = path.match(/^\/(?:api\/users\/profiles\/minecraft|minecraftservices\/minecraft\/profile\/lookup\/name)\/([A-Za-z0-9_]{3,16})$/);
    if (req.method === "GET" && byName) {
      const rows = await rpc("aerix_game_profile_by_name",{p_name:byName[1]}) as {game_name:string,profile_id:string}[];
      return rows.length ? textResponse(JSON.stringify({id:rows[0].profile_id.replaceAll("-",""),name:rows[0].game_name})) : new Response(null,{status:204});
    }
    if (req.method === "GET" && path === "/sessionserver/session/minecraft/hasJoined") {
      const name = url.searchParams.get("username"), serverId = url.searchParams.get("serverId");
      if (!name || !serverId) return new Response(null,{status:204});
      const rows = await rpc("aerix_game_has_joined",{p_name:name,p_server_id:serverId}) as Profile[];
      return rows.length ? textResponse(JSON.stringify(await profileJson(rows[0],true))) : new Response(null,{status:204});
    }
    if (req.method === "GET" && path === "/minecraftservices/minecraft/profile") {
      const token = req.headers.get("authorization")?.match(/^Bearer ([0-9a-f]{64})$/)?.[1];
      if (!token) return textResponse('{"error":"ForbiddenOperationException"}',403);
      const rows = await rpc("aerix_game_profile_by_token",{p_token:token}) as Profile[];
      if (!rows.length) return textResponse('{"error":"ForbiddenOperationException"}',403);
      const p=rows[0], [skin,cape]=await Promise.all([textureUrl(p.skin_url),textureUrl(p.cape_url)]);
      const skins=skin ? [{id:skin.slice(-32),state:"ACTIVE",url:skin,variant:p.slim?"SLIM":"CLASSIC"}] : [];
      const capes=cape ? [{id:cape.slice(-32),state:"ACTIVE",url:cape,alias:"Aerix"}] : [];
      return textResponse(JSON.stringify({id:p.profile_id.replaceAll("-",""),name:p.game_name,skins,capes}));
    }
    const lookup = path.match(/^\/minecraftservices\/minecraft\/profile\/lookup\/([0-9a-fA-F-]{32,36})$/);
    if (req.method === "GET" && lookup) {
      const rows=await rpc("aerix_game_profile",{p_uuid:lookup[1]}) as Profile[];
      return rows.length ? textResponse(JSON.stringify({id:rows[0].profile_id.replaceAll("-",""),name:rows[0].game_name})) : new Response(null,{status:204});
    }
    const match = path.match(/^\/sessionserver\/session\/minecraft\/profile\/([0-9a-fA-F-]{32,36})$/);
    if (req.method === "GET" && match) {
      const rows = await rpc("aerix_game_profile",{p_uuid:match[1]}) as Profile[];
      return rows.length ? textResponse(JSON.stringify(await profileJson(rows[0],url.searchParams.get("unsigned") === "false"))) : new Response(null,{status:204});
    }
    return textResponse('{"error":"Not Found"}',404);
  } catch (_err) {
    return textResponse('{"error":"Service unavailable"}',503);
  }
});
