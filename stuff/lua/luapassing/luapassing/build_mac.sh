#!/bin/bash

g++ -arch i386 -D_MACOSX -o luapassing.so -shared luapassing.cpp adapter.cpp -ldl -fPIC