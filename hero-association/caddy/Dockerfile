FROM node:24.13.0-alpine AS frontend-build

WORKDIR /workspace/frontend

COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci

COPY frontend/ ./
RUN npm run build

FROM caddy:2.11.4-alpine

COPY caddy/Caddyfile /etc/caddy/Caddyfile
COPY --from=frontend-build /workspace/frontend/dist /srv
