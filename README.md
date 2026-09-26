# Lua++

Lua++ เป็น runtime สำหรับรันสคริปต์ Lua โดยใช้ LuaJIT และเปิดทางให้สคริปต์เรียกฟังก์ชันที่เขียนด้วย C++ ผ่าน dynamic module (`.so`) ได้
โปรเจกต์นี้ประกอบด้วยตัว runtime หลักที่เขียนด้วย C++, ตัวอย่างการเชื่อมต่อ C++/Lua และโครงร่างเครื่องมือ `LuaPip` ที่ยังอยู่ระหว่างพัฒนา

> สถานะปัจจุบัน: ฟังก์ชันหลักสำหรับรัน Lua และโหลด C++ module ใช้งานได้ ส่วน `LuaPip` ยังไม่มี logic สำหรับใช้งานจริง

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

## LuaPip

คำสั่งสำหรับ compile และรันส่วน Java ที่มีอยู่ใน makefile คือ:

```bash
make build_pip
make run_pip
```

ปัจจุบัน `luapip/src/LuaPip.java` ยังไม่มีการทำงานหลัก จึงไม่ควรถือว่า LuaPip เป็น package manager ที่พร้อมใช้งาน ส่วน `json.java` เป็น JSON parser/serializer แบบไฟล์เดียวที่ไม่พึ่ง dependency ภายนอก

## ข้อจำกัดที่ทราบในปัจจุบัน

- การตั้งค่า `structureMode` และ `mainStructFileEnable` ถูกอ่านไว้ แต่ยังไม่ได้เปลี่ยนพฤติกรรม runtime ในโค้ดปัจจุบัน
- การจัดการ error ของ Lua แสดงข้อความแล้วดำเนินการต่อในบางกรณี ไม่ได้หยุด process ทุกกรณี
- module API ยังเป็น low-level Lua C API ผู้พัฒนาต้องจัดการ Lua stack และชนิดข้อมูลเอง
- `structfile.json` ใช้ current working directory เป็นฐาน จึงควรระวังเมื่อเรียก executable จาก directory อื่น
- `setup.sh` ยังว่างอยู่ การติดตั้ง dependency จึงต้องใช้คำสั่งในหัวข้อการติดตั้งหรือจัดการเองตามระบบปฏิบัติการ

## แหล่งอ้างอิงภายในโปรเจกต์

- `src/lpp.cpp`: entry point, การอ่าน config และ command-line arguments
- `src/include/RuntimeController.hpp`: การสร้าง Lua state, โหลด config และรัน script
- `src/include/luaLibs.hpp`: built-in `cpp.cout` และการโหลด shared library
- `test/FFI_test_p1/`: ตัวอย่าง module และ script ที่ใช้งานได้จริง