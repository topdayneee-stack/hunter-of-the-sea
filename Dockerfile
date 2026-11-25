# =========================================================================
# HUNTER OF THE SEA - DOCKERFILE FOR CLOUD DEPLOYMENT
# =========================================================================
FROM node:20-alpine

WORKDIR /app

# Sao chép mã nguồn server
COPY server/package*.json ./
RUN npm install --production --silent || true

COPY server/ ./

# Mặc định cổng 8080 (Cloud như Render/Railway sẽ tự gán biến PORT)
ENV PORT=8080
EXPOSE 8080

CMD ["node", "server.js"]
