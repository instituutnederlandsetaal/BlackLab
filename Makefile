# Makefile for BlackLab Docker images
#
# This provides some convenient shorthands for common Docker (Compose) commands
# related to building and tagging images, running and deploying the application.
#
# Build images:
#   make build                      # will be tagged as "latest"
#   make build   VERSION=4-alpha    # will be tagged as "4-alpha"
#	make build-plain				# Build images with plain output (for CI)
#
# Tag images:
#   make tag     VERSION=4-alpha    # Tag latest images as "4-alpha"
#   make tag-commit                 # Tag latest images with commit hash
#
# Tag and push to Docker Hub:
#   make push                       # Push version "latest"
#   make push    VERSION=4-alpha    # Tag as "4-alpha" and push
#	make push-commit		        # Tag with commit hash and push
#
# Build, tag and push:
#   make release VERSION=4-alpha    # Build, tag and push images
#   make release-commit             # Build, tag with commit hash and push
#
# Deploy to server (i.e. pull and (re)deploy application):
# (specified Docker context must exist)
#   make deploy VERSION=4.0-alpha CONTEXT=my-server
#   make deploy-commit CONTEXT=my-server              # Deploy current commit image (must exist)
#
# Running application:
#   make dev                        # Run development server (with docker-compose.override.yml)
#   make up                         # Start application
#   make stop 					    # Stop application
#   make down                       # Stop application and remove containers
#   make restart                    # Restart application
#   make logs 		                # Show logs
#   make logs-f                     # Show and follow logs (Ctrl+C to exit)
#
# Image management:
#   make lsi                        # List local blacklab images
#   make rmi VERSION=4-alpha        # Remove "4-alpha" images


# Config variables
#----------------------------------------

# Our project name. This is important if our project directory has a different name,
# such as when calling from Jenkins. In that case, the --project-name option will automatically
# be added.
PROJECT_NAME = blacklab

# Name prefix for the Docker image(s)
IMAGE_NAME_PREFIX = instituutnederlandsetaal/blacklab



# Helper variables
#-------------------------------------------

# Helper to be able to have leading whitespace in variable...
EMPTY_STRING =

CURRENT_DIR = $(shell basename $(PWD))

# See if our directory name matches our project name.
# If not, pass --project-name to Compose so it always behaves the same way.
# (if we didn't do this, container names would vary based on the directory name)
opt-project-name:
ifneq ($(PROJECT_NAME),$(CURRENT_DIR))
	OPT_PROJECT_NAME = $(EMPTY_STRING) --project-name $(PROJECT_NAME)
endif
-include opt-project-name

# Pass CONTEXT=<name> to use that Docker context
ifdef CONTEXT
	CONTEXT_PARAM = $(EMPTY_STRING) --context $(CONTEXT)
endif

# Our standard commands for calling Docker and Compose
DOCKER_COMMAND  = docker$(CONTEXT_PARAM)
COMPOSE_NO_FILE = $(DOCKER_COMMAND) compose$(OPT_PROJECT_NAME)
COMPOSE_COMMAND      = $(COMPOSE_NO_FILE) -f docker-compose.yml
COMPOSE_COMMAND_DEV  = $(COMPOSE_NO_FILE)

# Make sure VERSION defaults to 'latest'
ifndef VERSION
	VERSION = latest
endif

# Short hash of latest Git commit
GIT_COMMIT_HASH = $$(git log -1 --pretty=%h)

# For build, push, etc.: apply to all profiles, not just default
ALL_PROFILES = $(EMPTY_STRING) --profile default --profile tools

# By default, don't add any options to docker compose build
ifndef BUILD_OPT
	BUILD_OPT =
endif

# Application management targets
#-------------------------------------------

show-info:
	echo Git commit hash: $(GIT_COMMIT_HASH)
	echo current dir name: $(CURRENT_DIR)
	echo OPT_PROJECT_NAME:$(OPT_PROJECT_NAME)

# Stop application and remove containers
down:
	$(COMPOSE_COMMAND) down

# Build images
build:
	$(COMPOSE_COMMAND)$(ALL_PROFILES) build$(BUILD_OPT)
	# Make sure images are always tagged as latest, even if we override VERSION
	$(DOCKER_COMMAND) tag $(IMAGE_NAME_PREFIX):$(VERSION)        $(IMAGE_NAME_PREFIX):latest
	$(DOCKER_COMMAND) tag $(IMAGE_NAME_PREFIX)-solr:$(VERSION)   $(IMAGE_NAME_PREFIX)-solr:latest
	$(DOCKER_COMMAND) tag $(IMAGE_NAME_PREFIX)-proxy:$(VERSION)  $(IMAGE_NAME_PREFIX)-proxy:latest

# Build images (plain output for CI)
build-plain:
	BUILD_OPT="$(BUILD_OPT) --progress plain" $(MAKE) build

# Tag latest images with tag VERSION (default: latest)
tag:
	$(DOCKER_COMMAND) tag $(IMAGE_NAME_PREFIX):latest        $(IMAGE_NAME_PREFIX):$(VERSION)
	$(DOCKER_COMMAND) tag $(IMAGE_NAME_PREFIX)-solr:latest   $(IMAGE_NAME_PREFIX)-solr:$(VERSION)
	$(DOCKER_COMMAND) tag $(IMAGE_NAME_PREFIX)-proxy:latest  $(IMAGE_NAME_PREFIX)-proxy:$(VERSION)

# Tag latest images with most recent git commit hash
tag-commit:
	VERSION=$(GIT_COMMIT_HASH) $(MAKE) tag

# List images
lsi:
	docker image ls 'instituutnederlandsetaal/blacklab*'

# Remove images with tag VERSION (default: latest)
rmi:
	$(DOCKER_COMMAND) rmi $(IMAGE_NAME_PREFIX):$(VERSION)
	$(DOCKER_COMMAND) rmi $(IMAGE_NAME_PREFIX)-solr:$(VERSION)
	$(DOCKER_COMMAND) rmi $(IMAGE_NAME_PREFIX)-proxy:$(VERSION)

rmi-commit:
	VERSION=$(GIT_COMMIT_HASH) $(MAKE) rmi

# Tag latest images with VERSION (default: latest) and push to Docker Hub
push: tag
	$(DOCKER_COMMAND) push $(IMAGE_NAME_PREFIX):$(VERSION)
	$(DOCKER_COMMAND) push $(IMAGE_NAME_PREFIX)-solr:$(VERSION)
	$(DOCKER_COMMAND) push $(IMAGE_NAME_PREFIX)-proxy:$(VERSION)
	$(DOCKER_COMMAND) push $(IMAGE_NAME_PREFIX):latest
	$(DOCKER_COMMAND) push $(IMAGE_NAME_PREFIX)-solr:latest
	$(DOCKER_COMMAND) push $(IMAGE_NAME_PREFIX)-proxy:latest

# Tag images with most recent git commit hash and push them to Docker Hub
push-commit:
	VERSION=$(GIT_COMMIT_HASH) $(MAKE) push

# Build images and push them to Docker Hub.
# Pass VERSION to tag images with a specific version.
release: build push
	:

release-commit:
	VERSION=$(GIT_COMMIT_HASH) $(MAKE) release

# Pull images with version VERSION (default: latest) from Docker Hub
pull:
	$(DOCKER_COMMAND) pull $(IMAGE_NAME_PREFIX):$(VERSION)
	$(DOCKER_COMMAND) pull $(IMAGE_NAME_PREFIX)-solr:$(VERSION)
	$(DOCKER_COMMAND) pull $(IMAGE_NAME_PREFIX)-proxy:$(VERSION)

# Pull images with version VERSION (default: latest) from Docker Hub
pull-commit:
	VERSION=$(GIT_COMMIT_HASH) $(MAKE) pull

# Stop application
stop:
	$(COMPOSE_COMMAND) stop

# Run application with local dev config
# (will build images only if missing)
dev:
	$(COMPOSE_COMMAND_DEV) up -d

# Restart application
restart:
	$(COMPOSE_COMMAND) restart

# Show application logs
logs:
	$(COMPOSE_COMMAND) logs

# Show and follow application logs
# (Ctrl+C to exit)
logs-f:
	$(COMPOSE_COMMAND) logs -f

# Start application
up:
	$(COMPOSE_COMMAND) up -d

# Deploy application to server (based on images from Docker Hub) with production config
# by pulling the newest versions of images, then using them to bring the application up
deploy: pull
	$(COMPOSE_COMMAND) up -d --no-build

# Tag images with most recent git commit hash and push them to Docker Hub
deploy-commit:
	VERSION=$(GIT_COMMIT_HASH) $(MAKE) deploy
