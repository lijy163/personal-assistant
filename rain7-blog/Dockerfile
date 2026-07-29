FROM node:22-alpine AS builder
WORKDIR /workspace
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
RUN npm run build


FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /workspace/dist /usr/share/nginx/html/rain7
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]