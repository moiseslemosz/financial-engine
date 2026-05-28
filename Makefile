SHELL := /bin/bash
.PHONY: run db stop logs

db:
	docker-compose up -d
	@echo "Aguardando PostgreSQL ficar pronto..."
	@sleep 3

run:
	@if ! docker ps | grep -q financial_postgres; then \
		echo "PostgreSQL não está rodando. Subindo..."; \
		docker-compose up -d && sleep 3; \
	fi
	set -a && source .env && set +a && mvn spring-boot:run

stop:
	docker-compose down

logs:
	docker logs financial_postgres --tail=50 -f
