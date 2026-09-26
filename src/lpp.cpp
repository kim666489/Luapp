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
#include "nlohmann/json.hpp"
#include "RuntimeController.hpp"

using namespace std;
namespace fs = std::filesystem;
using json = nlohmann::json;

// All Variable
mutex mtx;
fs::path MotherPath;
fs::path CurrentPath;
json MainConfig;
vector<string> argv;
map<string,vector<string>> argvMapping = {
    {"-d",{"debug","bool"}},
    {"-mste",{"mainStructFileEnable","bool"}},
    {"-mode",{"structureMode","string"}}
};

// Functions
vector<string> split(string data,char target) {
    vector<string> result;
    stringstream ss(data);
    string word;
    while (getline(ss,word,target)) {
        result.push_back(word);
    }
    return result;
}
void readConfig() {
    fs::path ConfigPath = MotherPath / "config" / "config.json";
    if (!fs::exists(ConfigPath)) {
        cerr << "[Error] Config file not found" << endl;
        exit(1);
    }
    ifstream file(ConfigPath.string());
    file >> MainConfig;
};
void editConfig(char* args[], int argc) {
    // เริ่มที่ i = 1 เพื่อข้าม args[0] (ซึ่งคือ path ของโปรแกรม .out)
    for (int i = 1; i < argc; i++) {
        string cmd = args[i];
        vector<string> parse = split(cmd, '=');
        
        auto it = argvMapping.find(parse[0]);
        if (it == argvMapping.end()) {
            // ถ้าไม่ตรงกับ mapping เก็บไว้เป็น argument ทั่วไป
            argv.push_back(cmd);
            continue;
        }

        string configKey = it->second[0]; // เช่น "debug"
        string type = it->second[1];      // เช่น "bool", "int", "string"

        if (parse.size() < 2) {
            // เคสไม่มี '=' (เช่น -d) บังคับให้เป็น true
            if (type == "bool") {
                MainConfig[configKey] = true;
            }
        } else {
            // เคสมี '=' (เช่น -port=8080 หรือ -name=test)
            string val = parse[1];
            if (type == "int") {
                MainConfig[configKey] = stoi(val);
            } else if (type == "string") {
                MainConfig[configKey] = val;
            } else if (type == "bool") {
                MainConfig[configKey] = (val == "true" || val == "1");
            }
        }
    }
}
void _runtime(char* args[],int argc) {
    try {
        if (argv.empty()) {
            cerr << "[Error] not input file" << endl;
            exit(1);
        }
        RUNTIME_CONTROLLER::runtime rt = RUNTIME_CONTROLLER::runtime(argv);
        rt.loadLibs(fs::path(argv[0]).parent_path(),MotherPath);
        rt.run();
    } catch(const std::exception& e) {
        std::cerr << "[Error] " << e.what() << '\n';
    }
    
}

// Main Function
int main(int argc,char* args[]) {
    MotherPath = fs::absolute(fs::canonical(args[0])).parent_path().parent_path();
    CurrentPath = fs::current_path();
    readConfig();
    editConfig(args,argc);
    if (MainConfig["debug"]) {
        cout << "[Debug] Debug mode" << endl;
    }
    _runtime(args,argc);
    return 0;
}