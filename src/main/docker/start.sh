#!/bin/sh

haproxy -D -f /usr/local/etc/haproxy/haproxy.cfg -p /tmp/haproxy.pid

java -jar /opt/haproxy-agent.jar

