FROM node:6.3.0
WORKDIR /usr/src/app
COPY package.json ./
RUN npm install
COPY . .
CMD ["npm", "start"]
