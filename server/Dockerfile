FROM node:latest
RUN apt update
RUN apt install sqlite3
RUN mkdir /wallpaper_wizard
WORKDIR /wallpaper_wizard
COPY package.json /wallpaper_wizard/
RUN npm install
COPY * /wallpaper_wizard/
RUN npx tsc
CMD sqlite3 data/wallpaper_wizard.db; node wallpaper_wizard.js