# Lua++

Lua++ เป็น runtime สำหรับรันสคริปต์ Lua โดยใช้ LuaJIT และเปิดทางให้สคริปต์เรียกฟังก์ชันที่เขียนด้วย C++ ผ่าน dynamic module (`.so`) ได้
โปรเจกต์นี้ประกอบด้วยตัว runtime หลักที่เขียนด้วย C++, ตัวอย่างการเชื่อมต่อ C++/Lua และเครื่องมือ `LuaPip` สำหรับจัดการแพ็กเกจ

> สถานะปัจจุบัน: runtime รองรับการรัน Lua และโหลด C++ module ส่วน `LuaPip` รองรับติดตั้ง แสดงรายการ และถอนการติดตั้งแพ็กเกจในระดับโปรเจกต์หรือแบบถาวร

## ความสามารถหลัก

- รันไฟล์ Lua ด้วย LuaJIT
- เปิดใช้ standard Lua libraries ผ่าน `luaL_openlibs`
- มีตาราง global ชื่อ `cpp` สำหรับฟังก์ชัน built-in และ module ที่โหลดจาก C++
- โหลด C++ shared library ด้วย Boost.DLL
- รวมรายการ module จากไฟล์ JSON ใน `config/` และ `structfile.json` ที่อยู่ข้างไฟล์ Lua
- รองรับการรันหลายไฟล์ Lua ในคำสั่งเดียว โดยส่ง path เป็น arguments ต่อท้าย executable
- มี debug flag และตัวเลือกควบคุมการทำงานบางส่วนผ่าน command line

## โครงสร้างโปรเจกต์

```text
.
├── bin/                         # executable ที่ build แล้ว
├── config/
│   ├── config.json              # การตั้งค่าหลักของ runtime
│   └── structfile.json           # รายการ module ระดับโปรเจกต์
├── luapip/
│   ├── src/                      # โค้ด Java ของ LuaPip
│   └── bin/                      # Java class ที่ compile แล้ว
├── packages/                     # แพ็กเกจที่ติดตั้งเฉพาะโปรเจกต์
├── src/
│   ├── lpp.cpp                   # entry point และ command-line parsing
│   └── include/
│       ├── RuntimeController.hpp # สร้าง Lua state และรันสคริปต์
│       └── luaLibs.hpp            # register/load C++ module
├── test/FFI_test_p1/             # ตัวอย่าง C++ module และ Lua script
├── makefile
└── README.md
```

## ความต้องการของระบบ

สภาพแวดล้อมที่ makefile รองรับโดยตรงคือ Linux ที่มีแพ็กเกจต่อไปนี้

- `g++` ที่รองรับ C++20
- LuaJIT และ development headers (`luajit`, `libluajit-5.1-dev`)
- Boost.System และ Boost.Filesystem development libraries
- `make`
- `javac` และ `java` เฉพาะกรณีต้องการทดลอง `LuaPip`

ติดตั้ง LuaJIT ตามคำสั่งที่มีอยู่ใน makefile:

```bash
make lua_install
```

หาก distribution ของคุณใช้ชื่อแพ็กเกจ Boost ต่างจากระบบ Debian/Ubuntu ให้ติดตั้ง development package ของ `boost_system` และ `boost_filesystem` เพิ่มเอง

### ติดตั้งโปรเจกต์จาก GitHub

บน Linux Debian/Ubuntu สามารถ clone repository และรัน setup เพื่อเตรียมเครื่องได้:

```bash
git clone https://github.com/kim666489/Lua-.git
cd Lua-
chmod +x setup.sh
./setup.sh
```

สคริปต์ใช้ `apt-get` ติดตั้ง compiler/build tools, LuaJIT, Boost.System, Boost.Filesystem, Java, Git และเครื่องมือ ZIP โดยใช้ `sudo` เมื่อจำเป็น จากนั้นสร้าง `bin/`, `luapip/bin/`, `packages/` และ `temp/` ก่อน build runtime กับ LuaPip

setup เพิ่ม alias `lpp` และ `lpip` ให้ทั้ง Bash และ Zsh หาก shell ปัจจุบันเป็น Bash ให้โหลด alias โดยไม่ต้องเปิด terminal ใหม่:

```bash
source ~/.bashrc
```

ตัวอย่างการใช้ alias:

```bash
lpp ./program.lua
lpip install ./hello-package.zip
lpip list
```

ตัวเลือก setup:

- `./setup.sh --skip-deps`: ไม่ติดตั้ง system packages (ใช้เมื่อมี dependency ครบแล้ว)
- `./setup.sh --no-aliases`: ไม่แก้ไฟล์ startup ของ Bash/Zsh
- `./setup.sh --no-build`: สร้างโฟลเดอร์และ alias แต่ไม่ build
- `./setup.sh --help`: แสดงตัวเลือกทั้งหมด

setup ปัจจุบันรองรับ Linux ที่ใช้ `apt-get` เช่น Debian และ Ubuntu เท่านั้น

## การ build runtime

จากโฟลเดอร์รากของโปรเจกต์:

```bash
make build
```

คำสั่งนี้จะสร้าง `bin/lpp.out` โดย compile ไฟล์ `src/*.cpp` และ link กับ LuaJIT, Boost.System และ Boost.Filesystem

สำหรับ build ที่เปิด warning เพิ่มเติม:

```bash
make build_warning
```

ตรวจสอบว่า executable ถูกสร้างแล้ว:

```bash
ls -l bin/lpp.out
```

## การรัน Lua script

รูปแบบคำสั่งพื้นฐาน:

```bash
./bin/lpp.out path/to/program.lua
```

หรือใช้ target ของ make:

```bash
make run args=path/to/program.lua
```

runtime จะใช้ current working directory เป็นฐานสำหรับค้นหา `structfile.json` ดังนั้นควรเรียกคำสั่งจากโฟลเดอร์ที่เก็บไฟล์ Lua และ `structfile.json` ของงานนั้น ตัวอย่างที่มีใน repository:

```bash
cd test/FFI_test_p1
make -f makefile build
make -f makefile run
```

คำสั่ง build ตัวอย่างจะสร้าง `test/FFI_test_p1/module.so` และคำสั่ง run จะรัน `program.lua`

หากต้องการส่งหลายไฟล์ Lua สามารถส่ง path หลายตัวต่อท้าย executable ได้:

```bash
./bin/lpp.out first.lua second.lua
```

ไฟล์จะถูกประมวลผลตามลำดับที่ส่งเข้าไป

## Command-line options

argument ที่ขึ้นต้นด้วย option ที่รู้จักจะถูกใช้ override ค่าจาก `config/config.json`; argument อื่นจะถูกตีความเป็น path ของ Lua script

| Option | ผลลัพธ์ |
| --- | --- |
| `-d` | เปิด debug mode |
| `-d=true` หรือ `-d=1` | เปิด debug mode |
| `-d=false` หรือค่าอื่น | ปิด debug mode |
| `-mste` / `-mste=true` | override `mainStructFileEnable` |
| `-mode=value` | override `structureMode` ด้วย string ที่ระบุ |

ตัวอย่าง:

```bash
./bin/lpp.out -d ./program.lua
./bin/lpp.out -d=true ./program.lua
```

เมื่อเปิด debug mode โปรแกรมจะแสดงข้อความ `[Debug] Debug mode` ก่อนเริ่ม runtime

## การตั้งค่า `config/config.json`

ไฟล์นี้ต้องอยู่ในโฟลเดอร์ `config/` ที่อยู่ถัดจากโฟลเดอร์รากของ executable ตาม layout ของ repository:

```json
{
	"debug": false,
	"structureMode": false,
	"mainStructFileEnable": true
}
```

ความหมายของ key ที่มีในโค้ดปัจจุบัน:

- `debug`: แสดงข้อความ debug ตอนเริ่มโปรแกรม
- `structureMode`: ค่าการตั้งค่าโหมดโครงสร้างที่รับจาก config แต่ยังไม่มี logic ใช้งานใน runtime ปัจจุบัน
- `mainStructFileEnable`: ค่าการตั้งค่าสำหรับ main struct file ที่รับจาก config แต่ยังไม่มี logic ใช้งานใน runtime ปัจจุบัน

ทุกไฟล์ `.json` ใน `config/` จะถูกอ่านเพื่อค้นหา key `modules` แล้วนำรายการ module มารวมกัน หาก JSON ใดไม่มี `modules` จะไม่เพิ่ม module ใดจากไฟล์นั้น

## การสร้าง C++ module

### 1. เขียนฟังก์ชันที่รับและคืนค่าผ่าน Lua stack

ฟังก์ชัน C++ ต้องใช้ Lua C API ตัวอย่างนี้สร้างฟังก์ชัน `add`:

```cpp
#include <luajit-2.1/lua.hpp>

static int add(lua_State* L) {
	double first = luaL_checknumber(L, 1);
	double second = luaL_checknumber(L, 2);
	lua_pushnumber(L, first + second);
	return 1;
}

static const luaL_Reg moduleFunctions[] = {
	{"add", add},
	{NULL, NULL}
};

extern "C" const luaL_Reg* loadModule() {
	return moduleFunctions;
}
```

`loadModule` ต้อง export ด้วย `extern "C"` และคืน pointer ไปยัง array ของ `luaL_Reg` ที่ปิดท้ายด้วย `{NULL, NULL}`

### 2. Compile เป็น shared library

```bash
g++ -fPIC -shared -o module.so module.cpp
```

ในโปรเจกต์นี้ ตัวอย่างอยู่ที่ `test/FFI_test_p1/module.cpp`

### 3. ลงทะเบียน module ใน `structfile.json`

ไฟล์ `structfile.json` ต้องอยู่ใน current working directory ตอนรัน Lua:

```json
{
	"modules": [
		{
			"name": "math",
			"path": "module.so",
			"mode": "sub"
		}
	]
}
```

กติกาของ field:

- `name`: ชื่อที่ Lua ใช้เรียก module
- `path`: path ของ `.so` แบบ relative โดยอ้างอิงจาก current working directory
- `pathReal`: path จริงของ `.so` หากต้องการระบุ path โดยตรงแทน `path`
- `mode`: ใช้ `sub` เพื่อวาง module ไว้ใต้ตาราง `cpp` หรือใช้ `global` เพื่อสร้าง global table โดยตรง

### 4. เรียกจาก Lua

เมื่อใช้ `mode: "sub"`:

```lua
print(cpp.math.add(2, 2))
```

เมื่อใช้ `mode: "global"`:

```json
{
	"modules": [
		{
			"name": "math",
			"path": "module.so",
			"mode": "global"
		}
	]
}
```

จะเรียกฟังก์ชันด้วย:

```lua
print(math.add(2, 2))
```

### Built-in `cpp.cout`

runtime register ฟังก์ชัน `cpp.cout` ไว้สำหรับพิมพ์ string โดยตรง:

```lua
cpp.cout("hello from Lua")
```

ฟังก์ชันนี้รับ argument แรกเป็น string เท่านั้น

## ลำดับการโหลด module

เมื่อเริ่ม runtime จะเกิดขั้นตอนโดยสรุปดังนี้:

1. ค้นหา `config/` จากโฟลเดอร์รากของ executable
2. อ่านไฟล์ `.json` ทุกไฟล์ใน `config/` และรวมรายการ `modules`
3. อ่าน `structfile.json` จาก current working directory และต่อรายการ `modules` เพิ่ม
4. ตรวจสอบว่า path ของแต่ละ module มีอยู่จริง
5. โหลด `.so` ด้วย Boost.DLL และค้นหา symbol ชื่อ `loadModule`
6. register ฟังก์ชันตาม `mode`
7. เปิด standard Lua libraries และรันไฟล์ Lua ตามลำดับ arguments

ถ้าไม่มี module ใดเลย โปรแกรมจะแสดง warning แต่ยังสามารถรัน Lua script ที่ไม่ใช้ C++ module ได้

## การทดลองตัวอย่าง FFI

```bash
cd test/FFI_test_p1
make -f makefile build
make -f makefile run
```

ผลลัพธ์ที่คาดหวังคือ:

```text
4.0
```

หาก `bin/lpp.out` ยังไม่มี ให้ build runtime จากรากโปรเจกต์ก่อน:

```bash
cd ../..
make build
cd test/FFI_test_p1
make -f makefile build
make -f makefile run
```

## LuaPip: จัดการแพ็กเกจ

Build เครื่องมือด้วย Java 16 ขึ้นไป:

```bash
make build_pip
```

แพ็กเกจเป็นโฟลเดอร์หรือ ZIP ที่มี `init.json` อยู่ที่ราก ตัวอย่างโครงสร้าง:

```text
hello-package/
├── init.json
├── README.md
└── src/
	└── hello.lua
```

ตัวอย่าง `init.json`:

```json
{
	"name": "hello-package",
	"version": "1.0.0",
	"mainFolder": "./src"
}
```

`name` จำเป็นและใช้ได้เฉพาะตัวอักษรอังกฤษ ตัวเลข จุด ขีดกลาง และขีดล่าง โดยต้องเริ่มด้วยตัวอักษรหรือตัวเลข ส่วน `version` และ `mainFolder` เป็นข้อมูลเสริมที่เก็บไว้ใน manifest

รันคำสั่งจากโฟลเดอร์โปรเจกต์ที่ต้องการติดตั้ง:

```bash
make run_pip args="install ./hello-package.zip"
make run_pip args="list"
make run_pip args="remove hello-package"
```

โหมดติดตั้งมีสองแบบ:

| Mode | ตำแหน่งติดตั้ง | ใช้เมื่อ |
| --- | --- | --- |
| `project` (ค่าเริ่มต้น) | `./packages/<name>` ใน current working directory | ต้องการให้ dependency อยู่กับโปรเจกต์ |
| `permanent` | `~/.luapip/packages/<name>` | ต้องการติดตั้งไว้ใช้ข้ามโปรเจกต์ |

ระบุ mode ได้ด้วย `--mode project`, `--mode=project`, `--mode permanent` หรือ `--mode=permanent` ตัวอย่าง:

```bash
make run_pip args="install ./hello-package.zip --mode permanent"
make run_pip args="list --mode permanent"
make run_pip args="uninstall hello-package --mode permanent"
```

คำสั่ง `add` ใช้แทน `install` ได้ และ `uninstall` ใช้แทน `remove` ได้ คำสั่งติดตั้งไม่เขียนทับแพ็กเกจชื่อเดียวกันที่ติดตั้งอยู่แล้ว ให้ถอนของเดิมก่อนติดตั้งเวอร์ชันใหม่ ไฟล์ ZIP ต้องมี `init.json` ที่ราก หรืออยู่ในโฟลเดอร์ระดับบนสุดเพียงโฟลเดอร์เดียว

LuaPip จัดเก็บไฟล์แพ็กเกจตามโหมดที่เลือก แต่ runtime Lua++ ปัจจุบันยังไม่ค้นหาโฟลเดอร์ `packages/` โดยอัตโนมัติ การนำ module จากแพ็กเกจมาใช้ยังต้องระบุ module ใน `structfile.json` ตามขั้นตอนด้านบน

### ตัวอย่างตั้งแต่สร้างแพ็กเกจจนติดตั้ง

สร้างโฟลเดอร์แพ็กเกจและ `init.json` โดยให้ `name` ตรงกับชื่อที่ต้องการใช้ติดตั้ง:

```text
hello-package/
├── init.json
└── src/
		└── hello.lua
```

```json
{
	"name": "hello-package",
	"version": "1.0.0",
	"mainFolder": "./src"
}
```

ติดตั้งได้โดยส่ง path ของโฟลเดอร์โดยตรง:

```bash
make run_pip args="install ./hello-package"
```

หรือสร้าง ZIP โดยให้ `init.json` อยู่ที่รากของ archive แล้วติดตั้ง ZIP:

```bash
cd hello-package
zip -r ../hello-package.zip init.json src
cd ..
make run_pip args="install ./hello-package.zip"
```

ตรวจสอบรายการแพ็กเกจและถอนการติดตั้ง:

```bash
make run_pip args="list"
make run_pip args="remove hello-package"
```

ถ้าต้องการติดตั้งแบบถาวร ให้ระบุ `--mode permanent` ในคำสั่ง install, list และ remove ทุกครั้ง เช่น:

```bash
make run_pip args="install ./hello-package.zip --mode permanent"
make run_pip args="list --mode permanent"
make run_pip args="remove hello-package --mode permanent"
```

เรียก Java โดยตรงได้หลังจาก build แล้ว:

```bash
java -cp ./luapip/bin LuaPip help
java -cp ./luapip/bin LuaPip install ./hello-package.zip --mode project
```

### ปัญหาที่พบบ่อยของ LuaPip

- `ไม่พบ init.json`: ตรวจให้แน่ใจว่าไฟล์ชื่อ `init.json` อยู่ที่รากของโฟลเดอร์แพ็กเกจหรือ ZIP หาก ZIP มีโฟลเดอร์ครอบ ต้องมีโฟลเดอร์ระดับบนสุดที่บรรจุ `init.json` เพียงโฟลเดอร์เดียว
- `init.json ต้องมี name ที่ใช้ได้`: กำหนด `name` เป็น string ที่ขึ้นต้นด้วยตัวอักษรอังกฤษหรือตัวเลข และตามด้วยตัวอักษรอังกฤษ ตัวเลข `.`, `_` หรือ `-`
- ติดตั้งชื่อเดิมซ้ำไม่ได้: LuaPip ไม่เขียนทับแพ็กเกจเดิม ให้ใช้ `remove <name>` ใน mode เดียวกันก่อน แล้วจึงติดตั้งใหม่
- ไม่พบแพ็กเกจตอน list หรือ remove: ตรวจว่าใช้ mode เดียวกับตอนติดตั้ง และรันจาก current working directory เดิมเมื่อใช้ mode `project`
- ติดตั้งแล้ว Lua ยังไม่พบ module: เพิ่ม module ใน `structfile.json` เอง เพราะ runtime ยังไม่ค้นหาโฟลเดอร์แพ็กเกจโดยอัตโนมัติ

LuaPip เวอร์ชันนี้ติดตั้งจาก path ในเครื่องเท่านั้น ยังไม่มี remote registry, การดาวน์โหลดจาก URL, การแก้ dependency อัตโนมัติ หรือการอัปเดตทับแพ็กเกจที่ติดตั้งอยู่

## Troubleshooting

### `Config file not found`

ตรวจสอบว่า executable อยู่ใน `bin/` และมี `config/config.json` อยู่ในโฟลเดอร์รากเดียวกับ `bin/` ตามโครงสร้าง repository หากย้าย executable ไปที่อื่น ต้องย้ายโฟลเดอร์ `config/` ให้ตรงกับ layout ที่ runtime คาดไว้

### `not input file`

ต้องส่ง path ของ Lua script อย่างน้อยหนึ่งไฟล์ และต้องไม่ส่ง option ที่ไม่รู้จักเพียงอย่างเดียว:

```bash
./bin/lpp.out ./program.lua
```

### `Module file does not exist`

ตรวจสอบว่า `path` ใน `structfile.json` อ้างอิงจาก current working directory จริง และได้ build `.so` แล้ว:

```bash
ls -l module.so
```

### `Failed to load module`

ตรวจสอบประเด็นต่อไปนี้:

- module export symbol ชื่อ `loadModule`
- ฟังก์ชัน export ใช้ `extern "C"`
- array ของ `luaL_Reg` มี sentinel `{NULL, NULL}`
- module compile ด้วย LuaJIT headers/runtime ที่เข้ากันได้
- dependency ของ `.so` ถูกติดตั้งครบ

### Lua หา module ไม่พบ

ตรวจสอบว่าใช้ชื่อเรียกตรงกับ `name` และเลือก namespace ให้ถูกกับ `mode` เช่น `mode: "sub"` ต้องเรียกผ่าน `cpp.<name>` ส่วน `mode: "global"` ต้องเรียกผ่าน `<name>` โดยตรง


## ข้อจำกัดที่ทราบในปัจจุบัน

- การตั้งค่า `structureMode` และ `mainStructFileEnable` ถูกอ่านไว้ แต่ยังไม่ได้เปลี่ยนพฤติกรรม runtime ในโค้ดปัจจุบัน
- การจัดการ error ของ Lua แสดงข้อความแล้วดำเนินการต่อในบางกรณี ไม่ได้หยุด process ทุกกรณี
- module API ยังเป็น low-level Lua C API ผู้พัฒนาต้องจัดการ Lua stack และชนิดข้อมูลเอง
- `structfile.json` ใช้ current working directory เป็นฐาน จึงควรระวังเมื่อเรียก executable จาก directory อื่น
- `setup.sh` รองรับ Linux Debian/Ubuntu ที่ใช้ `apt-get`; ระบบปฏิบัติการอื่นต้องติดตั้ง dependency ด้วยตนเอง

## แหล่งอ้างอิงภายในโปรเจกต์

- `src/lpp.cpp`: entry point, การอ่าน config และ command-line arguments
- `src/include/RuntimeController.hpp`: การสร้าง Lua state, โหลด config และรัน script
- `src/include/luaLibs.hpp`: built-in `cpp.cout` และการโหลด shared library
- `test/FFI_test_p1/`: ตัวอย่าง module และ script ที่ใช้งานได้จริง