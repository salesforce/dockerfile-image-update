itest-local-changes:
	#TODO: Modify docker-compose.yml for local image
	mvn clean package
	docker build -t salesforce/dockerfile-image-update .
	docker-compose up
