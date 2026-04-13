FROM node:18-slim

# Install LibreOffice and dependencies via apt (much faster than Alpine apk)
RUN apt-get update && apt-get install -y --no-install-recommends \
      libreoffice-writer \
      libreoffice-calc \
      libreoffice-impress \
      python3 \
      python3-uno \
      qpdf \
      procps \
      curl \
      fonts-noto \
      fonts-noto-cjk \
      fonts-freefont-ttf \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY package*.json ./
RUN npm ci --production
COPY . .

# Make the UNO conversion script executable
RUN chmod +x scripts/convert_with_fields.py

EXPOSE 3000
CMD ["node", "server.js"]
