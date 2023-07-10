#!/bin/bash
[ "$1" = "help" ] && echo "See the manpage for actual help" && exit 0
[ $# -eq 0 ] && echo "You at least have to specify the server url" && exit 1
cache_dir=$4
retries=10
wallpaper_path=

if [[ $cache_dir != "" ]]
then
	#Get wallpaper name from info object
	wallpaper_name=$(ls -t $cache_dir | head -1) || true
	wallpaper_path="$cache_dir/$wallpaper_name"
else
	wallpaper_path="/tmp/wallpaper.jpg"
fi
xwallpaper --zoom "$wallpaper_path"

for i in $(seq 1 $retries); do	
	query_url="$1/wallpaper?tags=$2&sync=$3&follow=true"
	if [[ $cache_dir != "" ]]
	then
		echo "Query: $query_url&info=true"
		#Get headers only
		curl -X HEAD -I "$query_url&info=true" --output /tmp/wallpaper_info
		#Get wallpaper name from header
		wallpaper_name=$(head --lines 16 /tmp/wallpaper_info | grep name | cut -d " " -f 2 | tr -d '\n' | tr -d '\r')

		#Check if a wallpaper with the same name already exists
		if [ -f "$cache_dir/$wallpaper_name" ]
		then
			touch "$cache_dir/$wallpaper_name"
    		wallpaper_path="$cache_dir/$wallpaper_name"
		else
			query_url="$1/wallpaper/$wallpaper_name"
			echo $query_url
			#Download wallpaper
			curl "$query_url" --output "$cache_dir/$wallpaper_name" && wallpaper_path="$cache_dir/$wallpaper_name"
			
		fi
	else
		curl "$query_url" --output /tmp/wallpaper.jpg && wallpaper_path="/tmp/wallpaper.jpg"
	fi


	if [[ $? -eq 0 ]]
	then
		xwallpaper --zoom "$wallpaper_path" && echo "Set wallpaper :)"
		exit 0
	else
		sleep $(( $i*$i ))
	fi
done
