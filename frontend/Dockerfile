# 阶段 1: 构建 Vue 3 应用
FROM node:18-alpine AS build

WORKDIR /app

# Vite 构建时环境变量（VITE_ 前缀在 build 时注入，运行时不可变）
ARG VITE_ONLYOFFICE_URL=http://localhost:8088
ARG VITE_BACKEND_INTERNAL_URL=http://app:8080
ENV VITE_ONLYOFFICE_URL=${VITE_ONLYOFFICE_URL}
ENV VITE_BACKEND_INTERNAL_URL=${VITE_BACKEND_INTERNAL_URL}

COPY package*.json ./
RUN npm install

COPY . .
# 跳过 TypeScript 类型检查，直接用 vite build（类型检查在 CI 中单独运行）
RUN npx vite build

# 阶段 2: Nginx 提供静态文件服务
FROM nginx:1.25-alpine

# 限定 envsubst 只替换 BACKEND_URL，保护 Nginx 内置变量
ENV NGINX_ENVSUBST_FILTER=BACKEND_URL
ENV BACKEND_URL=http://app:8080

# 复制构建产物
COPY --from=build /app/dist /usr/share/nginx/html

# 复制 Nginx 配置模板
COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
