.PHONY: all build test check fetch-apk clean

all: build

build:
	./gradlew build

test:
	./gradlew test

check:
	./gradlew check

fetch-apk:
	./scripts/fetch-apk.sh

clean:
	./gradlew clean
