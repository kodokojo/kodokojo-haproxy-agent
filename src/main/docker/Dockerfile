FROM openjdk:8-jre-alpine

RUN apk add --no-cache haproxy
ADD start.sh /start.sh
RUN chmod +x /start.sh
ADD haproxy-agent.jar /opt/haproxy-agent.jar

ENTRYPOINT /start.sh
