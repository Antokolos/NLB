/**
 * See http://www.wellho.net/mouth/1844_Calling-functions-in-C-from-your-Lua-script-a-first-HowTo.html
 */
#include "lua.h"
#include "lualib.h"
#include "lauxlib.h"
#include "steam_api.h"
#include <stdio.h>
static int myCfunc ( lua_State *L) {
  printf ("Roses are Red\n");
  SteamUserStats()->SetAchievement("TEST_ACHIEVEMENT_1_0");
  SteamUserStats()->StoreStats();
  double trouble = lua_tonumber(L, 1);
  lua_pushnumber(L, 16.0 - trouble);
  return 1; }
extern "C" int luaopen_luapassing ( lua_State *L) {
  static const luaL_reg Map [] = {
    {"dothis", myCfunc},
    {NULL,NULL} } ;
  luaL_register(L, "cstuff", Map);
  return 1; }