FROM node:20-alpine AS verify

WORKDIR /app

COPY package.json ./
COPY src ./src
COPY test ./test
COPY test-support ./test-support

RUN npm test

FROM node:20-alpine AS runtime

WORKDIR /app

COPY package.json ./
COPY src ./src

RUN mkdir -p /data && chown node:node /data

USER node

EXPOSE 8080
VOLUME ["/data"]

HEALTHCHECK --interval=30s --timeout=5s --start-period=5s --retries=3 \
  CMD node -e "fetch('http://127.0.0.1:'+(process.env.RELEASE_AGENT_PORT||8080)+'/health').then(r=>process.exit(r.ok?0:1)).catch(()=>process.exit(1))"

CMD ["node", "src/index.js"]
