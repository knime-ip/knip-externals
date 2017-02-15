#!/bin/bash
SECONDS=0

rm -rf target
cd autogen
mvn -T 1C clean install
cd ../target/
mvn -T 1C clean install
cd update-site
mvn -T 1C clean install

duration=$SECONDS
echo "Total buildtime: $(($duration / 60)) minutes and $(($duration %60)) seconds."
