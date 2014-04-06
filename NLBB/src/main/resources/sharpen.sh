#!/bin/sh

SRC_DIR=./123
DST_DIR=./common
for i in `ls $SRC_DIR`; do convert $SRC_DIR/$i -sharpen 0x0.5 $DST_DIR/$i; done