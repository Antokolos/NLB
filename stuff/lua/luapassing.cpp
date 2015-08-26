/**
 * See http://www.wellho.net/mouth/1844_Calling-functions-in-C-from-your-Lua-script-a-first-HowTo.html
 */
#include "lua/lua.h"
#include "lua/lualib.h"
#include "lua/lauxlib.h"
#include "adapter.h"
#include <stdio.h>

bool initDone = false;

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

extern "C" int luaopen_luapassing ( lua_State *L) {
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