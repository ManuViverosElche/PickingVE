const http = require('http');
const fs = require('fs');
const path = require('path');
const dir = __dirname;
const port = process.env.PORT || 8085;
const types = { '.html':'text/html; charset=utf-8', '.json':'application/json; charset=utf-8', '.css':'text/css', '.js':'application/javascript' };
http.createServer((req,res)=>{
  let p = decodeURIComponent(req.url.split('?')[0]);
  if (p === '/' || p === '') p = '/index.html';
  const f = path.join(dir, path.normalize(p));
  if (!f.startsWith(dir)) { res.writeHead(403); return res.end('403'); }
  fs.readFile(f, (err,data)=>{
    if (err) { res.writeHead(404); return res.end('404'); }
    res.writeHead(200, {'Content-Type': types[path.extname(f)]||'application/octet-stream'});
    res.end(data);
  });
}).listen(port, ()=>console.log('serving http://localhost:'+port));
