.PHONY: help up build lint test e2e test-all-once ci clean devices

FLAVOR ?= Dev

help:
	@echo "Available commands:"
	@echo "  make up"
	@echo "  make build"
	@echo "  make lint"
	@echo "  make test"
	@echo "  make e2e"
	@echo "  make test-all-once"
	@echo "  make ci"
	@echo "  make clean"
	@echo "  make devices"
	@echo ""
	@echo "  Todos los targets aceptan FLAVOR=Dev|Staging|Prod (default: Dev)"

up: build

build:
	./gradlew assemble$(FLAVOR)Debug

lint:
	./gradlew lint$(FLAVOR)Debug

test:
	./gradlew test$(FLAVOR)DebugUnitTest

e2e:
	bash scripts/run_acceptance_tests.sh $(FLAVOR)

test-all-once: test e2e

ci: build lint test-all-once

clean:
	./gradlew clean

devices:
	adb devices
