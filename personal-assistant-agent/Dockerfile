FROM node:20-bookworm-slim

ARG NPM_REGISTRY=https://registry.npmmirror.com

RUN apt-get update \
    && apt-get install -y --no-install-recommends git ca-certificates \
    && rm -rf /var/lib/apt/lists/* \
    && npm install -g @openai/codex --registry="$NPM_REGISTRY"

WORKDIR /app
COPY package.json ./
COPY src ./src
COPY config.cloud.json ./config.json
COPY config.public.json ./config.public.json
RUN mkdir -p /app/public-qa && git -C /app/public-qa init

CMD ["node", "src/index.js"]
