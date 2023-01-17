#!/bin/bash
[ "$1" = "help" ] && echo "See the manpage for actual help" && exit 0
[ $# -eq 0 ] && echo "You at least have to specify the server url" && exit 1
query_url="$1/wallpaper?tags=$2&sync=$3"
echo "Query: $query_url"
curl "$query_url" > wallpaper.jpg && xwallpaper --zoom wallpaper.jpg && echo "Set wallpaper :)"
