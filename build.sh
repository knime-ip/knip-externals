#! /bin/bash

# This script builds the knip-externals update site
# If the build fails, make sure you have the required projects locally installed.
# - dietzc/Trackmate
# - knime-ip/knip-imglib2-ops

cd autogen
mvn clean install
cd ../target
mvn clean install
cd update-site
mvn clean install

echo "succesfully installed the update site!"
