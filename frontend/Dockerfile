# Stage 1: build Vue 3 application
FROM node:20-alpine AS build

WORKDIR /app

# Vite embeds VITE_* at build time; values are fixed in the static bundle.
ARG VITE_ONLYOFFICE_URL=http://localhost:8088
ARG VITE_BACKEND_INTERNAL_URL=http://app:8080
ENV VITE_ONLYOFFICE_URL=${VITE_ONLYOFFICE_URL}
ENV VITE_BACKEND_INTERNAL_URL=${VITE_BACKEND_INTERNAL_URL}

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
# `npm run build` runs `vue-tsc` then `vite build` (see package.json) — same as CI type-check + bundle.
RUN npm run build

# Stage 2: Nginx serves static assets (non-root; cap_net_bind_service for listen 80)
FROM nginx:1.25-alpine

# Restrict envsubst to BACKEND_URL so Nginx built-ins are not overwritten
ENV NGINX_ENVSUBST_FILTER=BACKEND_URL
ENV BACKEND_URL=http://app:8080

RUN apk add --no-cache libcap \
    && setcap 'cap_net_bind_service=+ep' /usr/sbin/nginx \
    && apk del libcap \
    && mkdir -p /var/cache/nginx/client_temp /var/cache/nginx/proxy_temp \
        /var/cache/nginx/fastcgi_temp /var/cache/nginx/uwsgi_temp /var/cache/nginx/scgi_temp \
        /etc/nginx/templates \
    && chown -R nginx:nginx /var/cache/nginx /var/run /var/log/nginx \
        /etc/nginx/conf.d /etc/nginx/templates

COPY --from=build /app/dist /usr/share/nginx/html
RUN chown -R nginx:nginx /usr/share/nginx/html

COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template
RUN chown nginx:nginx /etc/nginx/templates/default.conf.template

USER nginx
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
