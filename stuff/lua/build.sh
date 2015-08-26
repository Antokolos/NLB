#!/bin/bash

g++ -I/usr/include/lua5.1 -o luapassing.so -shared luapassing.cpp adapter.cpp -llua5.1 -lm -fPIC