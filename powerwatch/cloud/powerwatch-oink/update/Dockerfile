FROM node:11.10.0

#now setup and run
WORKDIR /usr/src/app
COPY package.json ./
RUN npm install
COPY . .
CMD npm start
