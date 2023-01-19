#!/bin/bash
[ $# < 2 ] && echo "Please give a Url and a Directory" && exit 1
url=$1
dir=$2
tags=$3
images=$(ls $dir | grep -E -i ".jpg|.jpeg")
for image in $images
do
	curl -X POST -H "Content-Type: multipart/form-data" -F "image=@$dir/$image" "$url/wallpaper?tags=$tags"
done