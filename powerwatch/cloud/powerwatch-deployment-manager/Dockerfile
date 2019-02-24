FROM r-base:3.5.2

FROM retro64xyz/docker-keybase:latest

FROM node:6.3.0
WORKDIR /usr/src/app
COPY package.json ./
RUN npm install
COPY . .
CMD keybase oneshot && \
    npm start
