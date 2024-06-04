#!/bin/bash
java -Xms6G -Xmx6G -server -XX:+UseG1GC -XX:MaxGCPauseMillis=20 -XX:InitiatingHeapOccupancyPercent=35 -XX:+ExplicitGCInvokesConcurrent -d64 -Djava.awt.headless=true -jar target/Kafkaesque-Application.jar
