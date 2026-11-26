# Build from the repository root: docker build -f web/Dockerfile -t sakurabans-web .
# (the demo and the tests read ../protocol, so the context is the whole repository.)

FROM node:24-alpine AS build
RUN npm install -g pnpm@12.22.0
WORKDIR /src
COPY protocol/ protocol/
COPY web/ web/
WORKDIR /src/web
RUN pnpm install --frozen-lockfile && pnpm build

FROM node:24-alpine
ENV NODE_ENV=production \
    HOST=0.0.0.0 \
    PORT=3000 \
    NUXT_EDITOR_STORAGE_DIR=/data
WORKDIR /app
COPY --from=build /src/web/.output ./.output
# Nuxt Content builds its SQLite copy of the docs at startup in .output/server/.data/content
# (nuxt.config content.database): only that folder and /data are writable for the server.
RUN mkdir -p /data /app/.output/server/.data/content \
    && chown -R node:node /data /app/.output/server/.data
USER node
VOLUME ["/data"]
EXPOSE 3000
# The Nitro node-server preset serves HTTP and the live-apply WebSockets on the same port.
CMD ["node", ".output/server/index.mjs"]
