#include <iostream>
#include <vector>
#include <string>
#include <map>
#include <boost/dll/alias.hpp>
#include <luajit-2.1/lua.hpp>

using namespace std;

static int add(lua_State* L) {
    double a = luaL_checknumber(L,1);
    double b = luaL_checknumber(L,2);
    lua_pushnumber(L,a+b);
    return 1;
}

static const luaL_Reg luaModule[] = {
    {"add",add},
    {NULL,NULL}
};

extern "C" const luaL_Reg* loadModule() {
    return luaModule;
};