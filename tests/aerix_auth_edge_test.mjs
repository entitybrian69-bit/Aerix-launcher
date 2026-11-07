import assert from 'node:assert/strict';
import {deflateSync} from 'node:zlib';
const project = 'https://unit-test.invalid';
let handler, savedKey = null, pending = false;
const profile = {game_name:'Alex', profile_id:'36532b5e-c442-3dbb-a24c-c7e55d0f979a', slim:true, skin_url:'https://lktupxfszfefsoyksfkg.supabase.co/storage/v1/object/public/social-media/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa/game-skin-slim-1.png', cape_url:null};
const token = 'a'.repeat(64);
function crc32(b) { let c=0xffffffff; for(const v of b){c^=v; for(let i=0;i<8;i++) c=(c>>>1)^(0xedb88320 & -(c&1));} return (c^0xffffffff)>>>0; }
function chunk(name,data) { const n=Buffer.from(name), h=Buffer.alloc(4), c=Buffer.alloc(4);h.writeUInt32BE(data.length);c.writeUInt32BE(crc32(Buffer.concat([n,data])));return Buffer.concat([h,n,data,c]); }
const head=Buffer.alloc(13);head.writeUInt32BE(1,0);head.writeUInt32BE(1,4);head[8]=8;head[9]=6;
const png=Buffer.concat([Buffer.from([137,80,78,71,13,10,26,10]),chunk('IHDR',head),chunk('tEXt',Buffer.from('secret-metadata')),chunk('IDAT',deflateSync(Buffer.from([0,255,0,0,255]))),chunk('IEND',Buffer.alloc(0))]);
profile.skin_url = project + '/storage/v1/object/public/social-media/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa/game-skin-slim-1.png';
globalThis.Deno = { env:{get:(name)=>({SUPABASE_URL:project,SUPABASE_ANON_KEY:'anon',SUPABASE_SERVICE_ROLE_KEY:'service'})[name]},serve:(h)=>{handler=h;} };
globalThis.fetch = async (url, opts={}) => {
  const path = new URL(url).pathname;
  if(path.includes('/storage/v1/object/public/social-media/')) return new Response(png,{headers:{'content-type':'image/png'}});
  if(path.endsWith('/aerix_auth_signing_key')) {
    assert.equal(opts.headers.Authorization,'Bearer service');
    if(opts.method === 'POST') { savedKey ??= JSON.parse(opts.body); return new Response(null,{status:201}); }
    return Response.json(savedKey ? [savedKey] : []);
  }
  const body = JSON.parse(opts.body);
  switch(path.split('/').pop()) {
    case 'aerix_game_join': pending = body.p_token === token && body.p_profile.replaceAll('-','') === profile.profile_id.replaceAll('-','') && body.p_server_id === 'abc'; return Response.json(pending);
    case 'aerix_game_validate': return Response.json(body.p_token === token);
    case 'aerix_game_has_joined': {const good = pending && body.p_name === 'Alex' && body.p_server_id === 'abc'; if(good) pending=false; return Response.json(good?[profile]:[]);}
    case 'aerix_game_profile': return Response.json(body.p_uuid.replaceAll('-','') === profile.profile_id.replaceAll('-','') ? [profile] : []);
    case 'aerix_game_profile_by_name': return Response.json(body.p_name.toLowerCase() === 'alex' ? [profile] : []);
    default: throw new Error('unexpected route '+path);
  }
};
await import('../supabase/functions/aerix-auth/index.ts');
const request = (path,method='GET',body) => handler(new Request(`https://example.invalid/functions/v1/aerix-auth${path}`,{method,...(body === undefined ? {} : {body:JSON.stringify(body),headers:{'content-type':'application/json'}})}));
const meta = await (await request('/')).json();
assert.match(meta.signaturePublickey,/BEGIN PUBLIC KEY/);
let r = await request('/sessionserver/session/minecraft/join','POST',{accessToken:'wrong',selectedProfile:profile.profile_id,serverId:'abc'}); assert.equal(r.status,403);
r = await request('/sessionserver/session/minecraft/join','POST',{accessToken:token,selectedProfile:profile.profile_id,serverId:'abc'}); assert.equal(r.status,204);
r = await request('/sessionserver/session/minecraft/hasJoined?username=Alex&serverId=abc'); assert.equal(r.status,200);
const verified = await r.json(); assert.equal(verified.id,profile.profile_id.replaceAll('-',''));
const property=verified.properties[0];
const pub = Uint8Array.from(Buffer.from(savedKey.public_key,'base64'));
const key=await crypto.subtle.importKey('spki',pub,{name:'RSASSA-PKCS1-v1_5',hash:'SHA-1'},false,['verify']);
assert.equal(await crypto.subtle.verify('RSASSA-PKCS1-v1_5',key,Buffer.from(property.signature,'base64'),new TextEncoder().encode(property.value)),true);
const texture=JSON.parse(Buffer.from(property.value,'base64').toString()); assert.equal(texture.textures.SKIN.metadata.model,'slim');
const texturePath=new URL(texture.textures.SKIN.url).pathname.split('/aerix-auth')[1];
const pngResponse=await request(texturePath); assert.equal(pngResponse.status,200);
assert.equal(pngResponse.headers.get('content-type'),'image/png');
const clean=Buffer.from(await pngResponse.arrayBuffer()); assert.equal(clean.includes(Buffer.from('secret-metadata')),false);
assert.equal((await request(texturePath.replace(/[0-9a-f]{64}$/,'0'.repeat(64)))).status,404);
r = await request('/sessionserver/session/minecraft/hasJoined?username=Alex&serverId=abc'); assert.equal(r.status,204);
r = await request('/authserver/validate','POST',{accessToken:token}); assert.equal(r.status,204);
r = await request('/authserver/validate','POST',{accessToken:'wrong'}); assert.equal(r.status,403);
r = await request('/api/profiles/minecraft','POST',['Alex','alex']); assert.equal((await r.json()).length,1);
r = await request('/sessionserver/session/minecraft/join','POST',{accessToken:'x'.repeat(5000),selectedProfile:profile.profile_id,serverId:'abc'}); assert.notEqual(r.status,204);
console.log('Auth service handshake, single-use lookup, signed textures and request bounds passed (mocked DB)');
