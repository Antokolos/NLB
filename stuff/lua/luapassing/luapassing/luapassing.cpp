// luapassing.cpp : Defines the exported functions for the DLL application.
// See http://www.wellho.net/mouth/1844_Calling-functions-in-C-from-your-Lua-script-a-first-HowTo.html
#ifdef _WINDOWS
#include "stdafx.h"
#else
#include "luapassing.h"
#include "adapter.h"
#include <stdio.h>
#include <dlfcn.h>
#include <string.h>
#endif

luaL_registerT luaL_register = NULL;
lua_pushnumberT lua_pushnumber = NULL;
lua_tonumberT lua_tonumber = NULL;
lua_tolstringT lua_tolstring = NULL;
lua_tobooleanT lua_toboolean = NULL;
bool initDone = false;

#ifdef _WINDOWS
static void initLuaFunctionPointers (HMODULE lib) {
    luaL_register = (luaL_registerT)GetProcAddress(lib, "luaL_register");
    lua_pushnumber = (lua_pushnumberT)GetProcAddress(lib, "lua_pushnumber");
    lua_tonumber = (lua_tonumberT)GetProcAddress(lib, "lua_tonumber");
    lua_tolstring = (lua_tolstringT)GetProcAddress(lib, "lua_tolstring");
    lua_toboolean = (lua_tobooleanT)GetProcAddress(lib, "lua_toboolean");
}
#else
// See http://syprog.blogspot.ru/2011/12/listing-loaded-shared-objects-in-linux.html
struct lmap {
   void*    base_address;     /* Base address of the shared object */
   char*    path;             /* Absolute file name (path) of the shared object */
   void*    not_needed1;      /* Pointer to the dynamic section of the shared object */
   struct lmap *next, *prev;  /* chain of loaded objects */
};

struct something
{
   void*  pointers[3];
   struct something* ptr;
};

static void initLuaFunctionPointers (void* ph) {
    luaL_register = (luaL_registerT)dlsym(ph, "luaL_register");
    printf("Alive");
    lua_pushnumber = (lua_pushnumberT)dlsym(ph, "lua_pushnumber");
    lua_tonumber = (lua_tonumberT)dlsym(ph, "lua_tonumber");
    lua_tolstring = (lua_tolstringT)dlsym(ph, "lua_tolstring");
    lua_toboolean = (lua_tobooleanT)dlsym(ph, "lua_toboolean");
}

/**
 * detecting whether base is ends with str
 */
bool endsWith (const char* base, const char* str) {
    int blen = strlen(base);
    int slen = strlen(str);
    return (blen >= slen) && (0 == strcmp(base + blen - slen, str));
}
#endif

static int init(lua_State *L) {
    printf("Initializing API...\n");
    if (initDone) {
        printf("Already initialized!\n");
        lua_pushnumber(L, 0.0);
    } else {
        initFunc();
        initDone = true;
        printf("API initialized.\n");
        lua_pushnumber(L, 1.0);
    }
    return 1;
}
  
static int setAchievement(lua_State *L) {
    const char* achievementName = lua_tostring(L, 1);
    bool storeImmediately = lua_toboolean(L, 2);
    printf("Setting achievement '%s'...\n", achievementName);
    setAchievementFunc(achievementName);
    if (storeImmediately) {
        storeFunc();
    }
    printf("Achievement set.\n");
    lua_pushnumber(L, 0.0);
    return 1;
}
  
static int store(lua_State *L) {
    printf ("Storing...\n");
    storeFunc();
    printf ("Done.\n");
    lua_pushnumber(L, 0.0);
    return 1;
}

static int clearAchievement(lua_State *L) {
    const char* achievementName = lua_tostring(L, 1);
    bool storeImmediately = lua_toboolean(L, 2);
    printf("Clearing achievement '%s'...\n", achievementName);
    clearAchievementFunc(achievementName);
    if (storeImmediately) {
        storeFunc();
    }
    printf("Achievement cleared.\n");
    lua_pushnumber(L, 0.0);
    return 1;
}

static int resetAll(lua_State *L) {
    printf ("Resetting all achievements...\n");
    resetAllFunc();
    printf ("Done.\n");
    lua_pushnumber(L, 0.0);
    return 1;
}

#ifdef _WINDOWS
extern "C" __declspec(dllexport) int luaopen_luapassing(lua_State *L) {
    HMODULE lib = GetModuleHandle(L"lua5.1.dll");
    if (!lib) {
        printf("Library lua5.1.dll is not loaded!\n");
        return 1;
    }
    initLuaFunctionPointers(lib);
#else
extern "C" int luaopen_luapassing ( lua_State *L) {
    const char* luaLibName = "liblua5.1.so.0";
    struct lmap* pl;
    void* ph = dlopen(NULL, RTLD_NOW);
    struct something* p = (struct something*)ph;
    p = p->ptr;
    pl = (struct lmap*)p->ptr;
    while (NULL != pl) {
        printf("%s\n", pl->path);
        if (endsWith(pl->path, luaLibName)) {
            initLuaFunctionPointers(pl);
        }
        pl = pl->next;
    }
#endif
    static const luaL_reg Map [] = {
        {"init", init},
        {"setAchievement", setAchievement},
        {"clearAchievement", clearAchievement},
        {"store", store},
        {"resetAll", resetAll},
        {NULL,NULL}
    };
    luaL_register(L, "statsAPI", Map);
    return 1;
}

/*
Interesting code to enum loaded process modules on Windows:
#include <Psapi.h>
HMODULE hMods[1024];
DWORD cbNeeded;
int i;
if (EnumProcessModules(GetCurrentProcess(), hMods, sizeof(hMods), &cbNeeded)) {
    for (i = 0; i < (cbNeeded / sizeof(HMODULE)); i++) {
        TCHAR szModName[MAX_PATH];
        // Get the full path to the module's file.

        if (GetModuleFileName(hMods[i], szModName, MAX_PATH)) {
            int wlength = GetShortPathNameW(szModName, 0, 0);
            LPWSTR shortp = (LPWSTR)calloc(wlength, sizeof(WCHAR));
            GetShortPathNameW(szModName, shortp, wlength);
            int clength = WideCharToMultiByte(CP_OEMCP, WC_NO_BEST_FIT_CHARS, shortp, wlength, 0, 0, 0, 0);
            LPSTR cpath = (LPSTR)calloc(clength, sizeof(CHAR));
            WideCharToMultiByte(CP_OEMCP, WC_NO_BEST_FIT_CHARS, shortp, wlength, cpath, clength, 0, 0);
            // Print the module name and handle value.
            printf("\tz%sz (0x%08X)\n", cpath, hMods[i]);
        }
    }
}
*/
