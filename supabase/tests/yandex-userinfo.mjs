import fs from 'node:fs';
import vm from 'node:vm';
import assert from 'node:assert/strict';
const source = fs.readFileSync(new URL('../functions/yandex-userinfo/index.ts', import.meta.url),'utf8').replace('(request: Request)', '(request)');
let handler;
let response = Response.json({id:'test-id', default_email:'test@yandex.ru',real_name:'Test'});
const sandbox = {
  Deno:{serve(fn){handler=fn;}}, Response, AbortSignal,
  async fetch(url, options){
    assert.equal(url,'https://login.yandex.ru/info?format=json');
    assert.equal(options.headers.Authorization,'OAuth test-token');
    if (response instanceof Error) throw response;
    return response;
  }
};
vm.runInNewContext(source,sandbox);
assert.equal((await handler(new Request('https://local.test'))).status,401);
assert.equal((await handler(new Request('https://local.test',{method:'POST'}))).status,405);
const request=()=>new Request('https://local.test',{headers:{Authorization:'Bearer test-token'}});
assert.deepEqual(await (await handler(request())).json(),{sub:'test-id',email:'test@yandex.ru',email_verified:false,name:'Test',given_name:'',family_name:''});
response=Response.json({id:'test-id'});
assert.equal((await handler(request())).status,422);
response=new Response('invalid',{status:401});
assert.equal((await handler(request())).status,401);
response=new Error('network');
assert.equal((await handler(request())).status,502);
console.log('Yandex adapter: 6 checks passed (mocked upstream).');

