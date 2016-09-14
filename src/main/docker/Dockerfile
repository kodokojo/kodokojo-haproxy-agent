FROM openjdk:8-jre-alpine

RUN apk add --no-cache haproxy
ADD haproxy.cfg /usr/local/etc/haproxy/haproxy.cfg

ADD start.sh /start.sh
RUN chmod +x /start.sh
ADD haproxy-agent.jar /opt/haproxy-agent.jar

EXPOSE 80
EXPOSE 443
EXPOSE 1983

ENTRYPOINT /start.sh
