#!/bin/bash
SECONDS=0

rm -rf target
cd autogen
mvn clean install
cd ../target/
mvn clean install
cd update-site
mvn clean install

duration=$SECONDS
echo "Total buildtime: $(($duration / 60)) minutes and $(($duration %60)) seconds."
