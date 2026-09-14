FROM node:20-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
ARG VITE_CAREERPILOT_DEMO_MODE=false
ENV VITE_CAREERPILOT_DEMO_MODE=${VITE_CAREERPILOT_DEMO_MODE}
RUN npm run build

FROM nginx:1.27-alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY proxy_params /etc/nginx/proxy_params
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
