# syntax=docker/dockerfile:1
# Build context: fe/   (docker build fe)

FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

# Caddy serves the SPA and proxies /api to the backend; with a domain in SITE_ADDRESS it also gets HTTPS.
FROM caddy:2-alpine
COPY Caddyfile /etc/caddy/Caddyfile
COPY --from=build /app/dist /srv
