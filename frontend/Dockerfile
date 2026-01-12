# Build Stage
FROM node:18-alpine as build
WORKDIR /app
COPY package*.json ./
RUN npm install
COPY . .
RUN npm run build

# Production Stage
FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/nginx.conf
RUN addgroup -S nginx && adduser -S nginx -G nginx || true
USER nginx
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]