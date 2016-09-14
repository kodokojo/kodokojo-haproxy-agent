#!/bin/bash

docker run -it --rm  -v /var/run/docker.sock:/var/tmp/docker.sock:rw -v /tmp/kodokojo/.m2:/root/.m2 -v "$PWD":/usr/src/mymaven -w /usr/src/mymaven -e "DOCKER_HOST=unix:///var/tmp/docker.sock"  maven:3-jdk-8 /bin/bash -c 'mvn clean install && chmod -R 777 target'
rc=$?
if [[ $rc != 0 ]]; then
  exit $rc
fi

mkdir -p target/docker | true
cp src/main/docker/* target/docker/
artifact=$(ls target | egrep haproxy-agent-.*-runnable.jar)
cp target/$artifact target/docker/haproxy-agent.jar
docker build -t="kodokojo/haproxy-agent" target/docker/


