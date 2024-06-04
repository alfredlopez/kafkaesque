#!/bin/sh
export GREMLIN_ROOT=/Work/Tools/apache-tinkerpop-gremlin-console-3.3.3
export CURRENT_FOLDER=`pwd`
echo GREMLIN_ROOT=$GREMLIN_ROOT
echo CURRENT_FOLDER=$CURRENT_FOLDER
echo arg=$1
cd $GREMLIN_ROOT/bin
##./gremlin.sh -e $CURRENT_FOLDER/JT_Bitsy.groovy $1
./gremlin.sh
cd $CURRENT_FOLDER