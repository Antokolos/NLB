#!/bin/sh

SRC_DIR=./123
DST_DIR=./234
for i in `ls $SRC_DIR`; do convert $SRC_DIR/$i -transparent "#c0c0c0" $DST_DIR/$i; done