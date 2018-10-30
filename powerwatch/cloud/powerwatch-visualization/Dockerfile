FROM node:6.3.0
WORKDIR /usr/src/app
COPY package.json ./
RUN npm install
COPY . .
EXPOSE 3765
CMD ["npm", "start"]
