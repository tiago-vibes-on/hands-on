import { readFileSync, writeFileSync } from 'node:fs';

const [source, target, origin = 'https://heroassociation.test:8443'] = process.argv.slice(2);
if (!source || !target || process.argv.length > 5) {
  throw new Error('Usage: node generate-realm.mjs SOURCE TARGET [HTTPS_ORIGIN]');
}
if (new URL(origin).origin !== origin || new URL(origin).protocol !== 'https:') {
  throw new Error('Realm callback origin must be an HTTPS origin without a path');
}

const realm = JSON.parse(readFileSync(source, 'utf8'));
const bff = realm.clients?.find((client) => client.clientId === 'hero-association-bff');
if (!bff) {
  throw new Error('The source realm has no hero-association-bff client');
}

bff.redirectUris = [`${origin}/auth/callback`, `${origin}/auth/post-logout`];
bff.webOrigins = [origin];
writeFileSync(target, `${JSON.stringify(realm, null, 2)}\n`, { mode: 0o600 });
