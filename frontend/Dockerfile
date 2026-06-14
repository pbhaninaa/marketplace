FROM node:22-alpine AS build
WORKDIR /app

ARG VITE_API_BASE=
ARG VITE_APP_ENV=PROD
ENV VITE_API_BASE=$VITE_API_BASE
ENV VITE_APP_ENV=$VITE_APP_ENV

COPY package*.json ./
RUN npm ci

COPY . .
RUN if [ "$VITE_APP_ENV" = "UAT" ]; then npm run build:uat; else npm run build:prod; fi

FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80
