
## Current directory
DIR:=$(strip $(shell dirname $(realpath $(lastword $(MAKEFILE_LIST)))))

##
.DEFAULT_GOAL := help
.PHONY: *


help: ## Display this help
	@printf "\n\033[33mUsage:\033[0m\n  make \033[32m<target>\033[0m \033[36m[\033[0marg=\"val\"...\033[36m]\033[0m\n\n\033[33mTargets:\033[0m\n"
	@awk 'BEGIN {FS = ":.*##"} /^[a-zA-Z_-]+:.*?##/ { printf "  \033[32m%-15s\033[0m %s\n", $$1, $$2 } /^##@/ { printf "\n\033[1m%s\033[0m\n", substr($$0, 5) } ' $(MAKEFILE_LIST)

build: ## Build containers
	@mkdir -p $(DIR)/tmp/jenkins-data
	@mkdir -p $(DIR)/tmp/jenkins-docker-certs
	@mkdir -p $(DIR)/tmp/jenkins-agent
	@mkdir -p $(DIR)/tmp/.m2
	@docker-compose --project-name jenkins build --pull

up: build ## Run containers
	@docker-compose --project-name jenkins up -d

down: ## Stop and remove containers
	@docker-compose --project-name jenkins down

clean: down ## Clean up
	@docker ps -q --filter "name=jenkins_*" | xargs -I {} docker stop {}
	@docker ps -a -q --filter "name=jenkins_*" | xargs -I {} docker rm -fv {}
	@docker volume ls -q --filter "name=jenkins_*" | xargs -I {} docker volume rm -f {}
	@docker volume ls -q --filter "dangling=true" | xargs -I {} docker volume rm -f {}
	@docker network ls -q --filter "name=jenkins_*" | xargs -I {} docker network rm {}
	@docker images -q "jenkins_*" | xargs -I {} docker rmi -f {}
	@docker images -q --filter "dangling=true" | xargs -I {} docker rmi -f {}

remove: clean
	@rm -rf $(DIR)/tmp/jenkins-data || true
	@rm -rf $(DIR)/tmp/jenkins-docker-certs || true
	@rm -rf $(DIR)/tmp/jenkins-agent || true
	@rm -rf $(DIR)/tmp/.m2 || true

