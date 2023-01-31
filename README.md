[![Server CI](https://github.com/clemenz5/wallpaper_wizard/actions/workflows/docker-image.yml/badge.svg)](https://github.com/clemenz5/wallpaper_wizard/actions/workflows/docker-image.yml)

# wallpaper_wizard
This tool can help you to synchronize wallpapers across all devices.\
All clients are included in this project.\
There is a deployment to try: [Wallpaper Wizard Api Docs](https://ww.keefer.de/api-docs)

## Android App
The Android App is under development

## X-based Unix Systems
The API was desgined to be as simple as possible. Please write your own script. Not because I am lazy but I don't want to mess with your system/setup.
You can find a suggestion/template in the according dir

## IOS App
I'd love to get into that, but I don't have the right Hardware :(

## Windows App
Not in development right now, but I will do that at some point

## Server
You want to deploy your own wallpaper wizard server? Great! Just pull the docker image, expose port 3000 and mount a volume in /wallpaper_wizard/data.
```
docker start -v wallpaper_wizard:/wallpaper_wizard/data --name=wallpaper_wizard -p 80:3000 -d wallpaper_wizard
```
