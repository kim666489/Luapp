#!/usr/bin/env bash
set -Eeuo pipefail

PROJECT_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
INSTALL_DEPS=1
INSTALL_ALIASES=1
BUILD_PROJECT=1

usage() {
	cat <<'EOF'
Lua++ setup

Usage: ./setup.sh [options]

Options:
  --skip-deps   Skip installing system packages
  --no-aliases  Do not update Bash/Zsh startup files
  --no-build    Prepare folders and aliases without building
  --help        Show this help
EOF
}

fail() {
	printf '[setup] Error: %s\n' "$1" >&2
	exit 1
}

while (($# > 0)); do
	case "$1" in
		--skip-deps) INSTALL_DEPS=0 ;;
		--no-aliases) INSTALL_ALIASES=0 ;;
		--no-build) BUILD_PROJECT=0 ;;
		-h|--help) usage; exit 0 ;;
		*) fail "Unknown option: $1 (use --help)" ;;
	esac
	shift
done

[[ "$(uname -s)" == "Linux" ]] || fail "This setup script currently supports Linux (Debian/Ubuntu)."

if ((INSTALL_DEPS)); then
	command -v apt-get >/dev/null 2>&1 || fail "apt-get was not found; install dependencies manually or use --skip-deps."
	if ((EUID == 0)); then
		APT=(apt-get)
	elif command -v sudo >/dev/null 2>&1; then
		APT=(sudo apt-get)
	else
		fail "Install dependencies as root or install sudo, then rerun setup."
	fi

	printf '[setup] Installing compiler, LuaJIT, Boost, Java, and archive tools...\n'
	"${APT[@]}" update
	"${APT[@]}" install -y \
		build-essential \
		default-jdk-headless \
		git \
		libboost-filesystem-dev \
		libboost-system-dev \
		libluajit-5.1-dev \
		luajit \
		make \
		unzip \
		zip
fi

printf '[setup] Preparing project directories...\n'
mkdir -p \
	"$PROJECT_ROOT/bin" \
	"$PROJECT_ROOT/config" \
	"$PROJECT_ROOT/luapip/bin" \
	"$PROJECT_ROOT/packages" \
	"$PROJECT_ROOT/temp"

[[ -f "$PROJECT_ROOT/config/config.json" ]] || fail "Missing config/config.json in $PROJECT_ROOT"

if ((BUILD_PROJECT)); then
	printf '[setup] Building Lua++ runtime and LuaPip...\n'
	make -C "$PROJECT_ROOT" build
	make -C "$PROJECT_ROOT" build_pip
fi

if ((INSTALL_ALIASES)); then
	for shell_rc in "$HOME/.bashrc" "$HOME/.zshrc"; do
		if [[ -f "$shell_rc" ]] || [[ "$shell_rc" == "$HOME/.bashrc" ]] || command -v zsh >/dev/null 2>&1; then
			mkdir -p "$(dirname -- "$shell_rc")"
			temp_rc="$(mktemp)"
			if [[ -f "$shell_rc" ]]; then
				awk '
					index($0, "# >>> Lua++ aliases >>>") == 1 { skip = 1; next }
					index($0, "# <<< Lua++ aliases <<<") == 1 { skip = 0; next }
					!skip { print }
				' "$shell_rc" > "$temp_rc"
			fi
			{
				cat "$temp_rc"
				printf '\n# >>> Lua++ aliases >>>\n'
				printf "alias lpp='\"%s/bin/lpp.out\"'\n" "$PROJECT_ROOT"
				printf "alias lpip='java -cp \"%s/luapip/bin\" LuaPip'\n" "$PROJECT_ROOT"
				printf '# <<< Lua++ aliases <<<\n'
			} > "$shell_rc"
			rm -f "$temp_rc"
		fi
	done
	printf '[setup] Added lpp and lpip aliases to your shell startup files.\n'
fi

printf '\n[setup] Complete. Open a new terminal or run: source ~/.bashrc\n'
printf '  lpp  path/to/script.lua\n'
printf '  lpip install ./package.zip\n'
