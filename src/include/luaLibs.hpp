#ifndef LUALIBS
#define LUALIBS
#include <iostream>
#include <algorithm>
#include <cstdlib>
#include <cstdint>
#include <functional>
#include <filesystem>
#include <fstream>
#include <sstream>
#include <vector>
#include <string>
#include <thread>
#include <map>
#include <mutex>
#include <luajit-2.1/lua.hpp>
#include <boost/dll/import.hpp>
#include <boost/function.hpp>
#include "nlohmann/json.hpp"

using namespace std;
namespace fs = std::filesystem;
using json = nlohmann::json;
namespace dll = boost::dll;

class LuaLibs {
    private:
    json StructConfig;
    vector<dll::shared_library> loadedModules;
    static int luaCout(lua_State* L) {
        const char* data = luaL_checkstring(L, 1);
        cout << data;
        return 0;
    }
    inline static const luaL_Reg libs[] = {
        {"cout", luaCout},
        {NULL, NULL}
    };

    public:
    LuaLibs(lua_State* L, json& SC, fs::path currentPath) {
        // 1. Register Built-in "cpp" table
        luaL_newlib(L, libs);
        lua_setglobal(L, "cpp");

        // 2. Load Dynamic Modules via Boost.DLL
        if (SC.contains("modules")) {
            json& modules = SC["modules"];
            for (const auto& module : modules) {
                string name = module.value("name", "");
                // ต่อ Relative Path เข้ากับ currentPath ตรงๆ ก่อน
                fs::path rawPath = module.contains("pathReal") 
                    ? fs::path(module.value("pathReal", "")) 
                    : (currentPath / module.value("path", ""));

                // เช็กการมีอยู่ของไฟล์ที่ Path จริงๆ ก่อนสั่ง canonical
                if (!fs::exists(rawPath)) {
                    cerr << "[Warning] Module file does not exist: " << rawPath << endl;
                    continue;
                }

                // ปลอดภัยแน่นอน เพราะผ่าน exists() มาแล้ว
                string path = fs::canonical(rawPath).string();
                string mode = module.value("mode", "sub");

                if (name.empty() || path.empty()) continue;

                try {
                    // Keep the library loaded while Lua holds its callback pointers.
                    loadedModules.emplace_back(path, dll::load_mode::rtld_now);
                    auto loadModule = loadedModules.back().get<const luaL_Reg*()>("loadModule");
                    const luaL_Reg* pluginRegs = loadModule();

                    if (pluginRegs != nullptr) {
                        if (mode == "global") {
                            lua_newtable(L);
                            luaL_setfuncs(L, pluginRegs, 0);
                            lua_setglobal(L, name.c_str());
                        } 
                        else {
                            lua_getglobal(L, "cpp");
                            lua_newtable(L);
                            luaL_setfuncs(L, pluginRegs, 0);
                            lua_setfield(L, -2, name.c_str());
                            lua_pop(L, 1);
                        }
                    }
                } catch (const std::exception& e) {
                    cerr << "[Error] Failed to load module '" << name << "': " << e.what() << endl;
                }
            }
        }
    }
};

#endif