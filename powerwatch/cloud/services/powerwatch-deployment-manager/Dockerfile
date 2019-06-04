#start from r-base because we need to run rscripts
FROM node:11.10.0

#install R
RUN apt-get update
RUN apt-get install -y r-base

#install keybase
RUN apt-get install -y curl libappindicator1 fuse libgconf-2-4 psmisc lsof
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
