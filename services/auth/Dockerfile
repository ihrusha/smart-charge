FROM node:26.11.1-bookworm-slim

WORKDIR /app

COPY package*.json ./
RUN npm ci

COPY tsconfig.json ./
COPY src ./src
COPY migrations ./migrations

RUN npm run build

ENV NODE_ENV=production

USER node

EXPOSE 3003

CMD ["npm", "start"]