#!/bin/sh

SRC_DIR=./123
DST_DIR=./common
for i in `ls $SRC_DIR`; do convert -adaptive-resize 16x16 $SRC_DIR/$i $DST_DIR/$i; done