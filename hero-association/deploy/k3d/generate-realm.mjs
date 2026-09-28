import { readFileSync, writeFileSync } from 'node:fs';

const [source, target] = process.argv.slice(2);
if (!source || !target) {
  throw new Error('Usage: node generate-realm.mjs SOURCE TARGET');
}

const realm = JSON.parse(readFileSync(source, 'utf8'));
const bff = realm.clients?.find((client) => client.clientId === 'hero-association-bff');
if (!bff) {
  throw new Error('The source realm has no hero-association-bff client');
}

const origin = 'https://k3d.heroassociation.test';
bff.redirectUris = [`${origin}/auth/callback`, `${origin}/auth/post-logout`];
bff.webOrigins = [origin];
writeFileSync(target, `${JSON.stringify(realm, null, 2)}\n`, { mode: 0o600 });
