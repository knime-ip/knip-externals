#!/bin/bash
rm -rf target
cd autogen
mvn clean install
cd ../target/
mvn clean install
cd update-site
mvn clean install
