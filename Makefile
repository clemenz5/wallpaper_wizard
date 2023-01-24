build_server: server/openapi.json wallpaper_wizard.js package.json Dockerfile
	cd server; docker build -t "wallpaper_wizard:$(GIT_COMMIT)" .
	echo $(CR_PAT) | docker login ghcr.io -u clemenz5 --password-stdin
	docker tag "wallpaper_wizard:$(GIT_COMMIT)" ghcr.io/clemenz5/wallpaper_wizard:$(GIT_COMMIT)
	docker push ghcr.io/clemenz5/wallpaper_wizard:$(GIT_COMMIT)