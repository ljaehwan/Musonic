import fs from 'node:fs/promises';
import {fileURLToPath} from 'node:url';

const repository = process.env.GITHUB_REPOSITORY || 'ljaehwan/Musonic-android';
if (!/^[\w.-]+\/[\w.-]+$/.test(repository)) throw Error('Invalid repository');
const directory = fileURLToPath(new URL('../docs/stars/', import.meta.url));
const headers = {'Accept': 'application/vnd.github+json', 'X-GitHub-Api-Version': '2022-11-28'};
if (process.env.GITHUB_TOKEN) headers.Authorization = `Bearer ${process.env.GITHUB_TOKEN}`;
const response = await fetch(`https://api.github.com/repos/${repository}`, {headers, signal: AbortSignal.timeout(15000)});
if (!response.ok) throw Error(`GitHub returned ${response.status}; existing history was not changed`);
const {stargazers_count: count} = await response.json();
if (!Number.isSafeInteger(count) || count < 0) throw Error('Invalid star count');

await fs.mkdir(directory, {recursive: true});
let history = [];
try { history = JSON.parse(await fs.readFile(`${directory}/counts.json`, 'utf8')); }
catch (error) { if (error.code !== 'ENOENT') throw error; }
if (!Array.isArray(history) || history.some(p => !/^\d{4}-\d{2}-\d{2}$/.test(p.date) || !Number.isSafeInteger(p.stars) || p.stars < 0)) {
  throw Error('Invalid history; refusing to overwrite it');
}
const today = new Date().toISOString().slice(0, 10);
const points = new Map(history.map(p => [p.date, p.stars]));
points.set(today, count);
history = [...points].sort(([a], [b]) => a.localeCompare(b)).map(([date, stars]) => ({date, stars}));
const first = history[0].date, last = history.at(-1).date;
const start = Date.parse(first), end = Date.parse(last);
const maximum = Math.max(1, ...history.map(p => p.stars));
const x = date => end === start ? 380 : 64 + (Date.parse(date) - start) / (end - start) * 632;
const y = stars => 230 - stars / maximum * 150;
const line = history.map(p => `${x(p.date).toFixed(2)},${y(p.stars).toFixed(2)}`).join(' ');
const dates = first === last
  ? `<text x="380" y="264" text-anchor="middle">${first}</text>`
  : `<text x="64" y="264">${first}</text><text x="696" y="264" text-anchor="end">${last}</text>`;
const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="760" height="292" viewBox="0 0 760 292" role="img" aria-labelledby="title description">
<title id="title">Musonic star history</title><desc id="description">${count} stars on ${today}. Daily totals since ${first}.</desc>
<rect x="1" y="1" width="758" height="290" rx="18" fill="#faf9fb" stroke="#e5e2e8"/>
<g font-family="system-ui,sans-serif" fill="#242329">
<text x="32" y="40" font-size="19" font-weight="600">Star history</text>
<text x="728" y="40" font-size="19" text-anchor="end">${count.toLocaleString('en-US')} stars</text>
<g stroke="#e5e2e8"><path d="M64 80H696M64 155H696M64 230H696"/></g>
<g font-size="12" fill="#747078"><text x="48" y="84" text-anchor="end">${maximum}</text><text x="48" y="234" text-anchor="end">0</text>${dates}</g>
<polyline points="${line}" fill="none" stroke="#d52d4d" stroke-width="3" stroke-linejoin="round"/>
<circle cx="${x(last).toFixed(2)}" cy="${y(count).toFixed(2)}" r="4" fill="#d52d4d"/>
</g></svg>\n`;
await fs.writeFile(`${directory}/counts.json`, JSON.stringify(history, null, 2) + '\n');
await fs.writeFile(`${directory}/history.svg`, svg);
console.log(`Recorded ${count} stars for ${repository} on ${today}`);
