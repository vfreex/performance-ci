
.PHONY: build run debug clean

build:
	mvn package

run:
	mvn maven-hpi-plugin:1.106:run

debug:
	export MAVEN_OPTS="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,address=8000,suspend=n"
	mvn maven-hpi-plugin:1.106:run

clean:
	mvn clean


