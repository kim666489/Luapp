args ?=

build:
	g++ ./src/*.cpp -o ./bin/lpp.out -I ./src/include -static-libgcc -static-libstdc++ -lluajit-5.1 -pthread -std=c++20 -lboost_system -lboost_filesystem
build_warning:
	g++ ./src/*.cpp -o ./bin/lpp.out -I ./src/include -static-libgcc -static-libstdc++ -lluajit-5.1 -pthread -std=c++20 -Wall -Wextra -lboost_system -lboost_filesystem
run:
	./bin/lpp.out $(args)

build_pip:
	javac -d ./luapip/bin ./luapip/src/*.java 
run_pip:
	java -cp ./luapip/bin LuaPip $(args)

lua_install:
	sudo apt update
	sudo apt install luajit libluajit-5.1-dev

clean:
	rm -rf ./temp/*