#ifndef _LUAPASSING_H
#define _LUAPASSING_H
#include <stdlib.h>

typedef struct lua_State lua_State;
typedef double lua_Number;
typedef int(*lua_CFunction) (lua_State *L);
typedef struct luaL_Reg {
	const char *name;
	lua_CFunction func;
} luaL_Reg;
#define luaL_reg	luaL_Reg
#define lua_tostring(L,i)	lua_tolstring(L, (i), NULL)

#ifdef _LINUX
extern "C" void luaL_register(lua_State *L, const char *libname, const luaL_Reg *l);
extern "C" void lua_pushnumber(lua_State *L, lua_Number n);
extern "C" lua_Number lua_tonumber(lua_State *L, int idx);
extern "C" const char* lua_tolstring(lua_State *L, int idx, size_t *len);
extern "C" int lua_toboolean(lua_State *L, int idx);
#else
typedef void(*luaL_registerT)(lua_State *L, const char *libname, const luaL_Reg *l);
typedef void(*lua_pushnumberT)(lua_State *L, lua_Number n);
typedef lua_Number(*lua_tonumberT)(lua_State *L, int idx);
typedef const char*(*lua_tolstringT)(lua_State *L, int idx, size_t *len);
typedef int(*lua_tobooleanT)(lua_State *L, int idx);
#endif

#endif