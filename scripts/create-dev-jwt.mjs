import { readFileSync } from 'node:fs';
import { createSign, randomUUID } from 'node:crypto';

const keyPath = new URL('../infrastructure/keys/jwt-private.pem', import.meta.url);
const privateKey = readFileSync(keyPath, 'utf8');
const now = Math.floor(Date.now() / 1000);
const encode = (value) => Buffer.from(JSON.stringify(value)).toString('base64url');
const header = encode({ alg: 'RS256', typ: 'JWT', kid: 'local-dev-key-1' });
const payload = encode({
  sub: 'dev-admin',
  userId: 'dev-admin',
  username: 'dev-admin',
  role: 'CONTENT_ADMIN',
  iat: now,
  exp: now + 900,
  jti: randomUUID(),
});
const signingInput = `${header}.${payload}`;
const signer = createSign('RSA-SHA256');
signer.update(signingInput);
signer.end();
const signature = signer.sign(privateKey).toString('base64url');
process.stdout.write(`${signingInput}.${signature}\n`);
