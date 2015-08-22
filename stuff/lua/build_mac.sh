cd lua
make clean
make
rm lua.o luac.o
cd ..
gcc -arch i386 -Ilua -c luapassing.c
#gcc -dynamiclib -current_version 1.0 luapassing.o lua/*.o -o luapassing.dylib
gcc -arch i386 -Ilua -o luapassing.so -shared luapassing.o lua/*.o