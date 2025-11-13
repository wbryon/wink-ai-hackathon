# Multi-stage build для production frontend
FROM node:20-alpine AS build

WORKDIR /app

# Аргументы сборки для переменных окружения Vite
ARG VITE_API_BASE_URL=/api
ARG VITE_COMFY_URL=http://host.docker.internal:8188

# Устанавливаем переменные окружения для сборки
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}
ENV VITE_COMFY_URL=${VITE_COMFY_URL}

# Копируем package files
COPY package*.json ./

# Устанавливаем зависимости
RUN npm ci

# Копируем исходный код
COPY . .

# Собираем приложение (переменные окружения будут доступны во время сборки)
RUN npm run build

# Production stage с nginx
FROM nginx:alpine

# Копируем собранные файлы
COPY --from=build /app/dist /usr/share/nginx/html

# Копируем nginx конфигурацию
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Expose порт
EXPOSE 80

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD wget --quiet --tries=1 --spider http://localhost/health || exit 1

# Запускаем nginx
CMD ["nginx", "-g", "daemon off;"]

