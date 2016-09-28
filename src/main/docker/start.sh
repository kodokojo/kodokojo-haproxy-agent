#!/bin/sh

haproxy -D -f /usr/local/etc/haproxy/haproxy.cfg -p /tmp/haproxy.pid

java -Drsyslog.host=$HOST -jar /opt/haproxy-agent.jar

