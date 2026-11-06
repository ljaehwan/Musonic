const fs = require('fs');
const path = require('path');
const cp = require('child_process');
const root = path.resolve(__dirname, '..');
const manifest = fs.readFileSync(path.join(root, 'app/build/intermediates/merged_manifests/play/processPlayManifest/AndroidManifest.xml'), 'utf8');
for (const forbidden of ['REQUEST_INSTALL_PACKAGES', '.updates', 'androidx.work', 'debuggable="true"']) {
    if (manifest.includes(forbidden)) throw Error('Forbidden Play manifest entry: ' + forbidden);
}
for (const required of ['targetSdkVersion="36"', 'usesCleartextTraffic="false"']) {
    if (!manifest.includes(required)) throw Error('Missing Play manifest requirement: ' + required);
}
const mapping = fs.readFileSync(path.join(root, 'app/build/outputs/mapping/play/mapping.txt'), 'utf8');
for (const forbidden of ['app.musonic.update.UpdateRepository', 'app.musonic.update.UpdateWorker', 'app.musonic.update.UpdateViewModel', 'androidx.work.impl']) {
    if (mapping.includes(forbidden)) throw Error('Forbidden Play code: ' + forbidden);
}
const bundle = path.join(root, 'app/build/outputs/bundle/play/app-play.aab');
const entries = cp.execFileSync('unzip', ['-Z1', bundle], {encoding:'utf8'}).split('\n');
for (const entry of entries.filter(e => /^base\/lib\/(arm64-v8a|x86_64)\/.*\.so$/.test(e))) {
    const b = cp.execFileSync('unzip', ['-p', bundle, entry]);
    if (b[4] !== 2 || b[5] !== 1) throw Error('Unexpected ELF format: ' + entry);
    const offset = Number(b.readBigUInt64LE(32)), size = b.readUInt16LE(54), count = b.readUInt16LE(56);
    for (let i = 0; i < count; i++) {
        const at = offset + i * size;
        if (b.readUInt32LE(at) === 1 && b.readBigUInt64LE(at + 48) < 16384n) throw Error('ELF alignment below 16 KiB: ' + entry);
    }
}
console.log('Play manifest, updater exclusion, and 64-bit ELF 16 KiB alignment checks passed.');
