#start from r-base because we need to run rscripts
FROM node:6.3.0

#install R
RUN apt-get update
RUN apt-get install -y r-base

#install keybase
RUN apt-get install -y curl
RUN curl --remote-name https://prerelease.keybase.io/keybase_amd64.deb
RUN dpkg -i keybase_amd64.deb
RUN apt-get install -f

#now setup and run
WORKDIR /usr/src/app
COPY package.json ./
RUN npm install
COPY . .
CMD keybase oneshot && \
    npm start
