.PHONY: help up build lint test e2e test-all-once ci clean devices \
	delivery-install delivery-mcp delivery-test delivery-smoke delivery-inspect \
	delivery-prepare delivery-context delivery-ci delivery-finalize \
	delivery-verify-head delivery-hooks-install delivery-hooks-status

FLAVOR ?= Dev
DELIVERY_DIR ?= tools/delivery-mcp
DELIVERY_NODE ?= node
DELIVERY_CLI = $(DELIVERY_NODE) $(DELIVERY_DIR)/cli.mjs

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
	@echo "Delivery tooling:"
	@echo "  make delivery-install"
	@echo "  make delivery-test"
	@echo "  make delivery-smoke"
	@echo "  make delivery-inspect ARGS=\"...\""
	@echo "  make delivery-prepare ARGS=\"...\""
	@echo "  make delivery-context ARGS=\"...\""
	@echo "  make delivery-ci ARGS=\"...\""
	@echo "  make delivery-finalize ARGS=\"...\""
	@echo "  make delivery-verify-head ARGS=\"...\""
	@echo "  make delivery-hooks-install"
	@echo "  make delivery-hooks-status"
	@echo ""
	@echo "  Android targets accept FLAVOR=Dev|Staging|Prod (default: Dev)"

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

delivery-install:
	npm ci --prefix $(DELIVERY_DIR)

delivery-mcp:
	$(DELIVERY_NODE) $(DELIVERY_DIR)/server.mjs $(ARGS)

delivery-test:
	$(DELIVERY_CLI) test $(ARGS)

delivery-smoke:
	$(DELIVERY_NODE) $(DELIVERY_DIR)/smoke.mjs $(ARGS)

delivery-inspect:
	$(DELIVERY_CLI) inspect $(ARGS)

delivery-prepare:
	$(DELIVERY_CLI) prepare $(ARGS)

delivery-context:
	$(DELIVERY_CLI) context $(ARGS)

delivery-ci:
	$(DELIVERY_CLI) ci $(ARGS)

delivery-finalize:
	$(DELIVERY_CLI) finalize $(ARGS)

delivery-verify-head:
	$(DELIVERY_CLI) verify-head $(ARGS)

delivery-hooks-install:
	$(DELIVERY_CLI) hooks install $(ARGS)

delivery-hooks-status:
	$(DELIVERY_CLI) hooks status $(ARGS)
