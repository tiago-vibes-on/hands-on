#!/usr/bin/env node

import { spawn, execFileSync } from 'node:child_process';
import { readFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptDir = dirname(fileURLToPath(import.meta.url));
const kubeconfig = process.env.HERO_ASSOCIATION_K3D_KUBECONFIG || resolve(scriptDir, '.kubeconfig');
const env = { ...process.env, KUBECONFIG: kubeconfig };
const realmName = 'hero-association';
const realm = JSON.parse(await readFile(resolve(scriptDir,
  '../../backend/keycloak/realm/hero-association-realm.json'), 'utf8'));
const assets = realm.clients.find(client => client.clientId === 'hero-association-assets');
const bff = realm.clients.find(client => client.clientId === 'hero-association-bff');
const audience = bff?.protocolMappers?.find(mapper =>
  mapper.name === 'hero-association-assets-audience');

if (!assets || !audience || process.argv.length !== 2) {
  throw new Error('Usage: node sync-assets-realm.mjs (source realm must define Assets and its BFF audience)');
}
const context = execFileSync('kubectl', ['config', 'current-context'], { env, encoding: 'utf8' }).trim();
if (context !== 'k3d-hero-association') {
  throw new Error(`Refusing to change Keycloak outside k3d-hero-association (current: ${context})`);
}
const password = (await readFile(resolve(scriptDir, 'secrets/KEYCLOAK_ADMIN_PASSWORD'), 'utf8')).trim();
if (!password) throw new Error('Missing k3d Keycloak admin password');

const forward = spawn('kubectl', [
  '-n', 'hero-association', 'port-forward', '--address', '127.0.0.1',
  'service/keycloak', ':8080',
], { env, stdio: ['ignore', 'pipe', 'pipe'] });

try {
  const port = await new Promise((resolvePort, reject) => {
    let output = '';
    const timer = setTimeout(() => reject(new Error('Keycloak port-forward timed out')), 20000);
    forward.stdout.on('data', chunk => {
      output += chunk.toString();
      const match = output.match(/Forwarding from 127\.0\.0\.1:(\d+)/);
      if (match) {
        clearTimeout(timer);
        resolvePort(Number(match[1]));
      }
    });
    forward.once('exit', code => {
      clearTimeout(timer);
      reject(new Error(`Keycloak port-forward exited (${code})`));
    });
    forward.once('error', error => {
      clearTimeout(timer);
      reject(error);
    });
  });
  const base = `http://127.0.0.1:${port}`;
  const tokenResponse = await fetch(`${base}/realms/master/protocol/openid-connect/token`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ client_id: 'admin-cli', grant_type: 'password',
      username: 'hero-association-admin', password }),
  });
  if (!tokenResponse.ok) throw new Error(`Keycloak admin login failed (${tokenResponse.status})`);
  const { access_token: token } = await tokenResponse.json();
  const admin = `/admin/realms/${realmName}`;

  async function request(method, path, body) {
    const response = await fetch(`${base}${admin}${path}`, {
      method,
      headers: { Authorization: `Bearer ${token}`,
        ...(body === undefined ? {} : { 'Content-Type': 'application/json' }) },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    if (!response.ok) throw new Error(`Keycloak ${method} ${path} failed (${response.status})`);
    return response.headers.get('content-type')?.includes('application/json') ? response.json() : undefined;
  }

  async function findClient(clientId) {
    const clients = await request('GET', `/clients?clientId=${encodeURIComponent(clientId)}`);
    return clients.find(client => client.clientId === clientId);
  }

  let assetsClient = await findClient(assets.clientId);
  if (!assetsClient) {
    await request('POST', '/clients', assets);
    assetsClient = await findClient(assets.clientId);
    if (!assetsClient) throw new Error('Assets client was not created');
    process.stdout.write('Created the Assets client.\n');
  } else {
    const expected = ['enabled', 'protocol', 'publicClient', 'standardFlowEnabled',
      'implicitFlowEnabled', 'directAccessGrantsEnabled', 'serviceAccountsEnabled'];
    for (const key of expected) {
      if (assetsClient[key] !== assets[key]) {
        throw new Error(`Existing Assets client has unexpected ${key}; refusing to overwrite it`);
      }
    }
    process.stdout.write('Assets client already exists.\n');
  }

  const bffClientSummary = await findClient(bff.clientId);
  if (!bffClientSummary) throw new Error('BFF client is missing');
  const mapperPath = `/clients/${bffClientSummary.id}/protocol-mappers/models`;
  const mappers = await request('GET', mapperPath);
  const existing = mappers.find(mapper => mapper.name === audience.name);
  if (!existing) {
    await request('POST', mapperPath, audience);
    process.stdout.write('Created the BFF Assets audience mapper.\n');
  } else if (existing.protocolMapper !== audience.protocolMapper ||
      Object.entries(audience.config).some(([key, value]) => existing.config?.[key] !== value)) {
    await request('PUT', `${mapperPath}/${existing.id}`, { ...audience, id: existing.id });
    process.stdout.write('Updated the BFF Assets audience mapper.\n');
  } else {
    process.stdout.write('BFF Assets audience mapper already matches.\n');
  }
  const verified = await request('GET', mapperPath);
  if (!verified.some(mapper => mapper.name === audience.name &&
      mapper.config?.['included.client.audience'] === assets.clientId)) {
    throw new Error('BFF Assets audience mapper verification failed');
  }
  process.stdout.write('k3d Keycloak Assets identity is ready.\n');
} finally {
  forward.kill('SIGTERM');
}
