MVN     := ./mvnw -B
VERSION := $(shell sed -n 's|.*<version>\(.*\)</version>.*|\1|p' pom.xml | head -1)

CLI_JAR     := traffictape-cli/target/traffictape-cli-$(VERSION)-all.jar
EXAMPLE_JAR := traffictape-example/target/traffictape-example-$(VERSION).jar

DEMO_DIR  := target/demo
DEMO_PORT := 18080

CORPUS ?= $(DEMO_DIR)/corpus
OUT    ?= $(DEMO_DIR)/mocks

.DEFAULT_GOAL := help
.PHONY: help build test verify install quick clean demo generate example cli bench outdated release

help: ## Show this help
	@grep -hE '^[a-z-]+:.*?## ' $(MAKEFILE_LIST) \
		| awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-10s\033[0m %s\n", $$1, $$2}'
	@echo
	@echo "  generate accepts CORPUS=<dir> OUT=<dir>"
	@echo "  bench accepts BENCH_ARGS=<jmh options>, e.g. BENCH_ARGS='-f 3 -prof gc'"

build: ## Compile and package every module
	$(MVN) package

test: ## Run the test suite
	$(MVN) test

verify: ## What CI runs
	$(MVN) verify

install: ## Install to the local repository
	$(MVN) install

quick: ## Package without tests
	$(MVN) package -DskipTests

clean: ## Remove build output
	$(MVN) clean

demo: quick ## Capture from the demo app over real HTTP, then generate mocks
	@mkdir -p $(DEMO_DIR)
	@rm -rf $(CORPUS) $(OUT)
	@set -e; \
	java -jar $(EXAMPLE_JAR) \
		--server.port=$(DEMO_PORT) \
		--traffictape.output.directory=$(CORPUS) \
		--traffictape.flush.interval=1s > $(DEMO_DIR)/app.log 2>&1 & \
	APP=$$!; \
	trap 'kill $$APP 2>/dev/null || true' EXIT; \
	printf 'starting demo app'; \
	for i in $$(seq 1 45); do \
		curl -sf http://localhost:$(DEMO_PORT)/actuator/health >/dev/null 2>&1 && break; \
		printf '.'; sleep 1; \
	done; \
	echo; \
	echo '--> driving traffic'; \
	curl -s http://localhost:$(DEMO_PORT)/widgets/1 >/dev/null; \
	curl -s -X POST http://localhost:$(DEMO_PORT)/widgets \
		-H 'Content-Type: application/json' -d '{"sku":"abc"}' >/dev/null; \
	curl -s -X PATCH http://localhost:$(DEMO_PORT)/widgets/1 \
		-H 'Content-Type: application/json' -d '{"status":"ACTIVE"}' >/dev/null; \
	curl -s -X PATCH http://localhost:$(DEMO_PORT)/widgets/1 \
		-H 'Content-Type: application/json' -d '{"owner":"team-a"}' >/dev/null; \
	sleep 2; \
	echo '--> capture readiness'; \
	curl -s http://localhost:$(DEMO_PORT)/actuator/traffictape; echo; \
	kill $$APP 2>/dev/null || true; wait $$APP 2>/dev/null || true; \
	echo '--> generating mocks'; \
	java -jar $(CLI_JAR) generate --corpus $(CORPUS) --out $(OUT)

generate: ## Generate mocks from a corpus: make generate CORPUS=<dir> OUT=<dir>
	@test -f $(CLI_JAR) || $(MAKE) quick
	java -jar $(CLI_JAR) generate --corpus $(CORPUS) --out $(OUT)

example: quick ## Run the demo app in the foreground
	java -jar $(EXAMPLE_JAR) --server.port=$(DEMO_PORT)

cli: ## Build only the CLI jar and print its path
	$(MVN) -pl traffictape-cli -am package -DskipTests
	@echo $(CLI_JAR)

# exec:exec rather than exec:java: JMH forks a JVM per benchmark, and a fork only inherits the
# classpath if the parent is a real process.
bench: ## Run the JMH capture benchmarks
	$(MVN) -pl traffictape-benchmarks -am install -DskipTests
	$(MVN) -pl traffictape-benchmarks exec:exec \
		-Dexec.executable=java \
		-Dexec.classpathScope=compile \
		-Dexec.args="-classpath %classpath org.openjdk.jmh.Main $(BENCH_ARGS)"

outdated: ## Report newer dependency and plugin versions
	$(MVN) versions:display-dependency-updates versions:display-plugin-updates

release: ## Stage and publish to Maven Central (requires signing keys)
	$(MVN) -Prelease deploy
	$(MVN) -Prelease jreleaser:full-release -pl .
