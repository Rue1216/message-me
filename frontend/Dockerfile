# ---------- 建置階段 ----------
FROM node:24-alpine AS build
WORKDIR /build

# 先只複製 lockfile 以便相依套件層可被 Docker 快取
COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

# ---------- 執行階段（Web Server 層） ----------
FROM nginx:1.27-alpine

COPY --from=build /build/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 3001
