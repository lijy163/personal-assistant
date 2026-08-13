FROM node:20-bookworm-slim

ARG NPM_REGISTRY=https://registry.npmmirror.com
ARG DEBIAN_MIRROR=http://mirrors.cloud.tencent.com

RUN sed -i -E "s|https?://deb.debian.org|${DEBIAN_MIRROR}|g" /etc/apt/sources.list.d/debian.sources \
    && apt-get -o Acquire::Retries=5 -o Acquire::ForceIPv4=true update \
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
