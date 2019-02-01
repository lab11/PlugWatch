FROM r-base:3.5.2

FROM node:6.3.0
WORKDIR /usr/src/app
COPY package.json ./
RUN npm install
COPY . .
CMD ["npm", "start"]
