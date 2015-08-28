#!/bin/bash

g++ -arch i386 -o luapassing.so -shared luapassing.cpp adapter.cpp -ldl -fPIC