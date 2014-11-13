#!/bin/sh
# This assumes shimmer_move_analyzer is in ../shimmer_move_analyzer
cp ../shimmer_move_analyzer/dist/lib/* dist/lib
cp -r data dist/

cp -r dist ardrone
rm -f ardrone.zip
zip -r ardrone.zip ardrone
rm -rf ardrone
