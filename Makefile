SHELL := /bin/bash
.PHONY: run db stop

db:
	docker-compose up -d

run:
	set -a && source .env && set +a && mvn spring-boot:run

stop:
	docker-compose down
