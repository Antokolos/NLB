#!/bin/bash

g++ -D_LINUX -o luapassing.so -shared luapassing.cpp adapter.cpp -fPIC