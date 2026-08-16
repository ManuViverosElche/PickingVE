const fs = require('fs');
const path = require('path');

const envPath = path.resolve(__dirname, '../../.env');
if (fs.existsSync(envPath)) {
  for (const line of fs.readFileSync(envPath, 'utf8').split('\n')) {
    const parts = line.split('=');
    if (parts.length >= 2 && !line.trim().startsWith('#')) {
      const key = parts[0].trim();
      const val = parts.slice(1).join('=').trim();
      if (!process.env[key]) process.env[key] = val;
    }
  }
}

const API = "https://pickingve-api-938422468946.europe-west1.run.app/api/truffaut/reporte";
const API_KEY = process.env.API_KEY || "";
fetch(API, { headers: { 'X-API-Key': API_KEY } })
  .then(r => r.json())
  .then(d => {
    fs.writeFileSync(__dirname + '/data.json', JSON.stringify(d, null, 2));
    console.log('data.json actualizado: ' + d.orders.length + ' pedidos, generado ' + d.generated);
  })
  .catch(e => { console.error('Error: ' + e.message); process.exit(1); });