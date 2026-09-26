#ifndef RUNTIME_CONTROLLER
#define RUNTIME_CONTROLLER
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
#include <memory>
#include <luajit-2.1/lua.hpp>
#include "nlohmann/json.hpp"
#include "luaLibs.hpp"

using namespace std;
namespace fs = std::filesystem;
using json = nlohmann::json;

class runtime {
    private:
    vector<string> targetPaths;
    lua_State* L = luaL_newstate();
    unique_ptr<LuaLibs> luaLibs;
    json StructConfig;
    void runLua(string path) {
        if (!fs::exists(fs::path(path))) {
            cerr << "[Error] file " << path << " not found" << endl;
            exit(1);
        }
        if (luaL_dofile(L,path.c_str()) != LUA_OK) {
            cerr << "[Error] " << lua_tostring(L, -1) << endl;
            lua_pop(L, 1);
        }
    }
    public:
    runtime(vector<string> _targetPaths) {
        this->targetPaths = _targetPaths;
    }
    void loadLibs(fs::path currentPath, fs::path motherPath) {
        json mergedConfig = json::object();

        // 1. อ่าน Global/Main Configs จาก motherPath / "config"
        fs::path mainConfigDir = motherPath / "config";
        if (fs::exists(mainConfigDir) && fs::is_directory(mainConfigDir)) {
            // วนลูปอ่านทุกไฟล์ .json ในโฟลเดอร์ config
            for (const auto& entry : fs::directory_iterator(mainConfigDir)) {
                if (entry.is_regular_file() && entry.path().extension() == ".json") {
                    ifstream mainFile(entry.path());
                    if (mainFile.is_open()) {
                        json tempConfig;
                        mainFile >> tempConfig;
                        
                        // Merge modules array เข้าด้วยกัน
                        if (tempConfig.contains("modules") && tempConfig["modules"].is_array()) {
                            if (!mergedConfig.contains("modules")) {
                                mergedConfig["modules"] = json::array();
                            }
                            for (const auto& mod : tempConfig["modules"]) {
                                mergedConfig["modules"].push_back(mod);
                            }
                        }
                    }
                }
            }
        } else {
            cout << "[Warning] Main config folder not found or invalid path" << endl;
        }

        // 2. อ่าน Local Struct File จาก currentPath
        fs::path structFile = currentPath / "structfile.json";
        if (fs::exists(structFile)) {
            ifstream localFile(structFile);
            if (localFile.is_open()) {
                json localConfig;
                localFile >> localConfig;

                if (localConfig.contains("modules") && localConfig["modules"].is_array()) {
                    if (!mergedConfig.contains("modules")) {
                        mergedConfig["modules"] = json::array();
                    }
                    for (const auto& mod : localConfig["modules"]) {
                        mergedConfig["modules"].push_back(mod);
                    }
                }
            }
        } else {
            cout << "[Warning] Local structfile.json not found" << endl;
        }

        // ถ้าไม่มี module ใดๆ เลยให้ย้อนกลับ
        if (!mergedConfig.contains("modules") || mergedConfig["modules"].empty()) {
            cout << "[Warning] No dynamic modules loaded from configs" << endl;
            return;
        }

        // 3. สร้าง LuaLibs instance เพียงครั้งเดียวโดยใช้ Merged Config
        StructConfig = mergedConfig;
        luaLibs = make_unique<LuaLibs>(L, StructConfig, currentPath);
    }
    void run() {
        luaL_openlibs(L);
        for (string path:this->targetPaths) {
            runLua(path);
        }
        lua_close(L);
    }
};

#endif