#start from r-base because we need to run rscripts
FROM r-base:3.5.2

#install node
RUN apt-get update
RUN apt-get install nodejs npm

#install keybase
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
