#!/bin/sh

JAVA=/usr/local/java/java7/bin/java

# Set application lib directory
APP_LIB=./lib

# prepare internal variables - do not change
CP=./bin
for jar in `ls $APP_LIB`
do
CP=$CP:$APP_LIB/$jar
done

$JAVA -classpath $CP com.nlbhub.nlb.builder.NLBBMain