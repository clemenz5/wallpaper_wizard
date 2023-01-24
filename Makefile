build_server: server
	cd server; docker build -t "wallpaper_wizard:$(GIT_COMMIT)" .
	echo $CR_PAT | docker login ghcr.io -u clemenz5 --password-stdin
	docker push ghcr.io/clemenz5/wallpaper_wizard:$(GIT_COMMIT)