#!/bin/bash
[ "$1" = "help" ] && echo "See the manpage for actual help" && exit 0
[ $# -eq 0 ] && echo "You at least have to specify the server url" && exit 1
query_url="$1/wallpaper?tags=$2&sync=$3&follow=true"
echo "Query: $query_url"
retries=10
for i in $(seq 1 $retries); do 
	curl "$query_url" > /tmp/wallpaper.jpg
	if [[ $? -eq 0 ]]
	then
		xwallpaper --zoom /tmp/wallpaper.jpg && echo "Set wallpaper :)"
		exit 0
	else
		sleep $(( $i*$i ))
	fi
done
