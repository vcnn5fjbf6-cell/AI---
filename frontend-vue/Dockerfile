FROM node:20-alpine AS builder
WORKDIR /app

COPY package*.json ./
RUN npm ci

ARG VITE_APP_ENV=production
ARG VITE_BUILD_REF=local
ENV VITE_APP_ENV=$VITE_APP_ENV
ENV VITE_BUILD_REF=$VITE_BUILD_REF

COPY . .
RUN npm run build

FROM nginx:1.27-alpine
WORKDIR /usr/share/nginx/html

COPY deploy/nginx/default.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /app/dist ./

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
