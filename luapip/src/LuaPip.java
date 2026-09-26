import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

public class LuaPip {
    public static Path MotherPath;
    public static Path CurrentPath;
    public static Path MainPath;
    public static Path TempPath;
    public static json.JsonObject MainConfig;
    public static ArrayList<String> argv = new ArrayList<>();
    public static HashMap<String, List<String>> argvMapping = new HashMap<>();

    // Flags ที่ไม่ผูกกับ MainConfig (ใช้เฉพาะ command ปัจจุบัน เช่น -g, -f, --all)
    public static HashSet<String> flags = new HashSet<>();
    private static final Set<String> KNOWN_FLAGS = Set.of("-g", "--global", "-f", "--force", "--all");

    /**
     * รันคำสั่ง Shell ข้าม OS (Windows / Linux / macOS)
     * 
     * @param command   คำสั่งที่ต้องการรัน เช่น "unzip -o temp.zip -d ./modules"
     * @param workDir   โฟลเดอร์เป้าหมายที่จะให้รันคำสั่ง (ใส่ null หากใช้โฟลเดอร์ปัจจุบัน)
     * @param inheritIO สั่งให้พ่น Output/Error ออก Terminal ของ Java โดยตรงหรือไม่
     * @return Exit Code (0 = สำเร็จ, ค่าอื่น = เกิดข้อผิดพลาด)
     */
    public static int runCommand(String command, File workDir, boolean inheritIO) {
        try {
            boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
            ProcessBuilder pb;
            if (isWindows) {
                // สำหรับ Windows ใช้ PowerShell
                pb = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", command);
            } else {
                // สำหรับ Linux / macOS ใช้ Bash
                pb = new ProcessBuilder("bash", "-c", command);
            }
            // กำหนด Working Directory (ถ้ามี)
            if (workDir != null) {
                pb.directory(workDir);
            }

            // แสดง Log ออกหน้าจอ Terminal Java โดยตรง
            if (inheritIO) {
                pb.inheritIO();
            }

            Process process = pb.start();
            return process.waitFor();

        } catch (Exception e) {
            System.err.println("[ShellRunner Error]: " + e.getMessage());
            return -1; // คืนค่า -1 กรณีเกิด Exception
        }
    }

    public static void clearDirectory(Path targetDir) throws IOException {
        if (!Files.exists(targetDir) || !Files.isDirectory(targetDir)) {
            return;
        }

        try (Stream<Path> pathStream = Files.walk(targetDir)) {
            pathStream
                .sorted(Comparator.reverseOrder()) // เรียงลำดับย้อนกลับ เพื่อลบไฟล์ย่อยลึกสุดก่อน
                .filter(path -> !path.equals(targetDir)) // ข้ามโฟลเดอร์หลักไป (ไม่ให้ลบตัวมันเอง)
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        System.err.println("Failed to delete: " + path + " - " + e.getMessage());
                    }
                });
        }
    }

    /** ลบทั้งโฟลเดอร์ รวมถึงตัวโฟลเดอร์เอง (ใช้กับ install dir ตอน remove/reinstall) */
    public static void deleteDirectoryFully(Path targetDir) throws IOException {
        clearDirectory(targetDir);
        Files.deleteIfExists(targetDir);
    }

    /** คัดลอกทั้งโฟลเดอร์แบบ recursive (ใช้ตอน install จาก path ที่เป็นโฟลเดอร์ ไม่ใช่ .zip) */
    public static void copyDirectory(Path src, Path dst) throws IOException {
        try (Stream<Path> pathStream = Files.walk(src)) {
            for (Path source : (Iterable<Path>) pathStream::iterator) {
                Path relative = src.relativize(source);
                Path target = dst.resolve(relative.toString());
                if (Files.isDirectory(source)) {
                    Files.createDirectories(target);
                } else {
                    if (target.getParent() != null) Files.createDirectories(target.getParent());
                    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    public static void cleanTemp() {
        try {
            clearDirectory(TempPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void setupArgvMapping() {
        // key -> [configKey, type]
        argvMapping.putIfAbsent("-d",    new ArrayList<>(List.of("debug", "bool")));
    }

    public static void readConfig() {
        try {
            Path ConfigPath = Paths.get(MainPath.toString(), "config", "config.json");
            String config = Files.readString(ConfigPath);
            MainConfig = (json.JsonObject) json.parse(config);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * แยกวิเคราะห์ argument ทั้งหมด:
     *  - ถ้าตรงกับ argvMapping (เช่น -d) -> แก้ MainConfig เหมือนเดิม
     *  - ถ้าเป็น flag ที่รู้จัก (-g, --global, -f, --force, --all) -> เก็บใน flags
     *  - ที่เหลือ -> เก็บใน argv (command + positional args)
     */
    public static void parseArgs(String[] args) {
        for (String arg : args) {
            String[] parse = arg.split("=", 2);
            List<String> mapping = argvMapping.get(parse[0]);

            if (mapping != null) {
                String configKey = mapping.get(0); // เช่น "debug"
                String type = mapping.get(1);      // เช่น "bool", "int", "string"

                if (parse.length < 2) {
                    // ไม่มี '=' เช่น "-d" -> บังคับให้เป็น true ถ้าเป็น bool
                    if (type.equals("bool")) {
                        MainConfig.put(configKey, true);
                    }
                } else {
                    String val = parse[1];
                    switch (type) {
                        case "int":
                            MainConfig.put(configKey, Integer.parseInt(val));
                            break;
                        case "string":
                            MainConfig.put(configKey, val);
                            break;
                        case "bool":
                            MainConfig.put(configKey, val.equals("true") || val.equals("1"));
                            break;
                        default:
                            MainConfig.put(configKey, val);
                    }
                }
                continue;
            }

            if (KNOWN_FLAGS.contains(arg)) {
                flags.add(arg);
                continue;
            }

            // ไม่ตรงกับ mapping หรือ flag ใด ๆ -> เก็บไว้เป็น argument ทั่วไป (command / positional)
            argv.add(arg);
        }
    }

    public static boolean hasFlag(String... names) {
        for (String n : names) {
            if (flags.contains(n)) return true;
        }
        return false;
    }

    /** ดึงค่าแบบไม่ throw ถ้าไม่มี key (ต่างจาก json.JsonObject.get ที่ throw NoSuchElementException) */
    private static Object getOrNull(json.JsonObject o, String key) {
        return (o != null && o.has(key)) ? o.get(key) : null;
    }

    // ---------------------------------------------------------------------
    // Staging: unzip (หรือคัดลอกโฟลเดอร์) แพ็กเกจเข้า TempPath แล้วตรวจสอบ init.json
    // ---------------------------------------------------------------------
    public static void stagePackage(Path source) throws IOException {
        cleanTemp();
        Files.createDirectories(TempPath);

        if (Files.isDirectory(source)) {
            copyDirectory(source, TempPath);
        } else {
            int code = runCommand(
                "unzip -o \"%s\" -d \"%s\"".formatted(source.toString(), TempPath.toString()),
                CurrentPath.toFile(),
                false
            );
            if (code != 0) {
                throw new IOException("unzip failed with exit code " + code);
            }
        }

        if (!Files.exists(TempPath.resolve("init.json"))) {
            throw new IOException("init.json not found in package");
        }
    }

    // ---------------------------------------------------------------------
    // Registry: <scope>/packages/installed.json  -> {"packages": {name: {...}}}
    // ---------------------------------------------------------------------
    public static Path packagesDir(boolean global) {
        return global ? MainPath.resolve("packages") : CurrentPath.resolve("packages");
    }

    public static Path registryPath(boolean global) {
        return packagesDir(global).resolve("installed.json");
    }

    public static json.JsonObject loadRegistry(boolean global) throws IOException {
        Path path = registryPath(global);
        json.JsonObject registry;
        if (!Files.exists(path)) {
            registry = new json.JsonObject();
        } else {
            String text = Files.readString(path);
            registry = text.isBlank() ? new json.JsonObject() : (json.JsonObject) json.parse(text);
        }
        if (!(getOrNull(registry, "packages") instanceof json.JsonObject)) {
            registry.put("packages", new json.JsonObject());
        }
        return registry;
    }

    public static void saveRegistry(boolean global, json.JsonObject registry) throws IOException {
        Path path = registryPath(global);
        if (path.getParent() != null) Files.createDirectories(path.getParent());
        Files.writeString(path, registry.toString(2));
    }

    // ---------------------------------------------------------------------
    // Module config ที่ runtime (RuntimeController.hpp / LuaLibs.hpp) อ่านจริง:
    //   global -> <MainPath>/config/installed-packages.json  (ไฟล์แยก จะถูก merge
    //             เข้ากับไฟล์ .json อื่น ๆ ใน config/ โดย RuntimeController อัตโนมัติ)
    //   local  -> <CurrentPath>/structfile.json               (ไฟล์เดียวที่ runtime
    //             อ่านตรง ๆ ต่อโปรเจกต์ -> ต้องรักษาเนื้อหาอื่นที่ผู้ใช้เขียนเองไว้)
    // ---------------------------------------------------------------------
    public static Path moduleConfigPath(boolean global) {
        return global
            ? MainPath.resolve("config").resolve("installed-packages.json")
            : CurrentPath.resolve("structfile.json");
    }

    public static json.JsonObject loadModuleConfig(boolean global) throws IOException {
        Path path = moduleConfigPath(global);
        json.JsonObject data;
        if (!Files.exists(path)) {
            data = new json.JsonObject();
        } else {
            String text = Files.readString(path);
            data = text.isBlank() ? new json.JsonObject() : (json.JsonObject) json.parse(text);
        }
        if (!(getOrNull(data, "modules") instanceof json.JsonArray)) {
            data.put("modules", new json.JsonArray());
        }
        return data;
    }

    public static void saveModuleConfig(boolean global, json.JsonObject data) throws IOException {
        Path path = moduleConfigPath(global);
        if (path.getParent() != null) Files.createDirectories(path.getParent());
        Files.writeString(path, data.toString(2));
    }

    /** ลบ entry ทั้งหมดของ package นี้ (จับด้วย tag "sourcePackage") ออกจาก modules array แล้วคืน array ใหม่ */
    private static json.JsonArray removePackageEntries(json.JsonArray modules, String packageName) {
        json.JsonArray filtered = new json.JsonArray();
        for (Object m : modules) {
            if (m instanceof json.JsonObject) {
                Object src = getOrNull((json.JsonObject) m, "sourcePackage");
                if (packageName.equals(src)) continue; // ข้าม (ลบออก)
            }
            filtered.add(m);
        }
        return filtered;
    }

    public static void registerModules(boolean global, String packageName, json.JsonArray newEntries) throws IOException {
        json.JsonObject data = loadModuleConfig(global);
        json.JsonArray modules = data.getJsonArray("modules");
        // ลบ entry เก่าของ package เดียวกันออกก่อน (รองรับ reinstall / -f)
        json.JsonArray filtered = removePackageEntries(modules, packageName);
        for (Object e : newEntries) filtered.add(e);
        data.put("modules", filtered);
        saveModuleConfig(global, data);
    }

    public static void unregisterModules(boolean global, String packageName) throws IOException {
        json.JsonObject data = loadModuleConfig(global);
        json.JsonArray modules = data.getJsonArray("modules");
        json.JsonArray filtered = removePackageEntries(modules, packageName);
        data.put("modules", filtered);
        saveModuleConfig(global, data);
    }

    // ---------------------------------------------------------------------
    // install <path-to-package|dir> [-g|--global] [-f|--force]
    // ---------------------------------------------------------------------
    public static void cmdInstall(String sourceArg, boolean global, boolean force) {
        Path source = Paths.get(sourceArg).toAbsolutePath().normalize();
        if (!Files.exists(source)) {
            System.err.println("[Error] Source not found: " + source);
            System.exit(1);
            return;
        }

        try {
            System.out.println("[Install] Staging package from %s".formatted(source));
            stagePackage(source);
        } catch (IOException e) {
            System.err.println("[Error] Failed to stage package: " + e.getMessage());
            cleanTemp();
            System.exit(1);
            return;
        }

        json.JsonObject initData;
        try {
            String initText = Files.readString(TempPath.resolve("init.json"));
            initData = (json.JsonObject) json.parse(initText);
        } catch (Exception e) {
            System.err.println("[Error] Failed to parse init.json: " + e.getMessage());
            cleanTemp();
            System.exit(1);
            return;
        }

        Object nameObj = getOrNull(initData, "name");
        if (!(nameObj instanceof String) || ((String) nameObj).isBlank()) {
            System.err.println("[Error] init.json missing required field: name");
            cleanTemp();
            System.exit(1);
            return;
        }
        String name = (String) nameObj;
        String version = initData.optString("version", "0.0.0");

        Object buildRaw = getOrNull(initData, "build");
        json.JsonObject buildInfo = (buildRaw instanceof json.JsonObject) ? (json.JsonObject) buildRaw : null;

        Object artifactRaw = getOrNull(initData, "artifact");
        String artifactField = (artifactRaw instanceof String) ? (String) artifactRaw : null;

        Object buildModeRaw = getOrNull(initData, "buildMode");
        String buildMode = (buildModeRaw instanceof String) ? (String) buildModeRaw : null;

        if (buildMode == null) {
            buildMode = (buildInfo != null) ? "source" : (artifactField != null ? "prebuilt" : null);
        }
        if (buildMode == null) {
            System.err.println("[Error] init.json must specify \"buildMode\" (\"source\" or \"prebuilt\"), "
                + "or provide a \"build\" or \"artifact\" field");
            cleanTemp();
            System.exit(1);
            return;
        }

        Path artifactPath;
        if (buildMode.equals("source")) {
            Object cmdRaw = (buildInfo != null) ? getOrNull(buildInfo, "command") : null;
            if (!(cmdRaw instanceof String)) {
                System.err.println("[Error] buildMode \"source\" requires build.command in init.json");
                cleanTemp();
                System.exit(1);
                return;
            }
            String buildCommand = (String) cmdRaw;
            String buildOutput = buildInfo.optString("output", "module.so");

            System.out.println("[Install] Compiling package '%s' via: %s".formatted(name, buildCommand));
            int code = runCommand(buildCommand, TempPath.toFile(), true);
            if (code != 0) {
                System.err.println("[Error] Build failed with exit code " + code);
                cleanTemp();
                System.exit(1);
                return;
            }
            artifactPath = TempPath.resolve(buildOutput);
            if (!Files.exists(artifactPath)) {
                System.err.println("[Error] Build finished but output not found: " + artifactPath);
                cleanTemp();
                System.exit(1);
                return;
            }
        } else if (buildMode.equals("prebuilt")) {
            if (artifactField == null) {
                System.err.println("[Error] buildMode \"prebuilt\" requires an \"artifact\" field in init.json");
                cleanTemp();
                System.exit(1);
                return;
            }
            artifactPath = TempPath.resolve(artifactField);
            if (!Files.exists(artifactPath)) {
                System.err.println("[Error] Declared artifact not found: " + artifactPath);
                cleanTemp();
                System.exit(1);
                return;
            }
        } else {
            System.err.println("[Error] Unknown buildMode: " + buildMode);
            cleanTemp();
            System.exit(1);
            return;
        }

        Path installDir = packagesDir(global).resolve(name);

        try {
            json.JsonObject registry = loadRegistry(global);
            json.JsonObject pkgs = registry.getJsonObject("packages");

            if (pkgs.has(name) && !force) {
                System.err.println("[Error] Package '%s' is already installed (%s scope). Use -f/--force to reinstall."
                    .formatted(name, global ? "global" : "local"));
                cleanTemp();
                System.exit(1);
                return;
            }

            if (Files.exists(installDir)) {
                deleteDirectoryFully(installDir);
            }
            Files.createDirectories(installDir);

            // คัดลอกไฟล์ artifact หลัก (ใช้แค่ base filename ในโฟลเดอร์ที่ติดตั้ง)
            String artifactFileName = artifactPath.getFileName().toString();
            Path installedArtifact = installDir.resolve(artifactFileName);
            Files.copy(artifactPath, installedArtifact, StandardCopyOption.REPLACE_EXISTING);

            // คัดลอกไฟล์เสริมอื่น ๆ ถ้ามีระบุใน init.json ("files": ["extra/foo.so", ...])
            Object filesRaw = getOrNull(initData, "files");
            if (filesRaw instanceof json.JsonArray) {
                for (Object f : (json.JsonArray) filesRaw) {
                    String rel = String.valueOf(f);
                    Path fsrc = TempPath.resolve(rel);
                    if (!Files.exists(fsrc)) {
                        System.out.println("[Warning] Extra file listed but not found, skipping: " + rel);
                        continue;
                    }
                    Path fdst = installDir.resolve(rel);
                    if (fdst.getParent() != null) Files.createDirectories(fdst.getParent());
                    Files.copy(fsrc, fdst, StandardCopyOption.REPLACE_EXISTING);
                }
            }

            // สร้างรายการ modules สำหรับลงทะเบียนเข้ากับไฟล์ที่ runtime อ่านจริง
            json.JsonArray moduleEntries = new json.JsonArray();
            Object modulesRaw = getOrNull(initData, "modules");
            if (modulesRaw instanceof json.JsonArray && !((json.JsonArray) modulesRaw).isEmpty()) {
                for (Object m : (json.JsonArray) modulesRaw) {
                    json.JsonObject mod = (json.JsonObject) m;
                    String modName = mod.optString("name", name);
                    String modPath = mod.optString("path", artifactFileName);
                    String modMode = mod.optString("mode", "sub");
                    Path pathReal = installDir.resolve(modPath).toAbsolutePath().normalize();

                    json.JsonObject entry = new json.JsonObject();
                    entry.put("name", modName);
                    entry.put("mode", modMode);
                    entry.put("pathReal", pathReal.toString());
                    entry.put("sourcePackage", name);
                    moduleEntries.add(entry);
                }
            } else {
                // ไม่ได้ระบุ modules มา -> ใช้ artifact หลักเป็นโมดูลเดียว ชื่อ = ชื่อ package
                json.JsonObject entry = new json.JsonObject();
                entry.put("name", name);
                entry.put("mode", "sub");
                entry.put("pathReal", installedArtifact.toAbsolutePath().normalize().toString());
                entry.put("sourcePackage", name);
                moduleEntries.add(entry);
            }

            // ลงทะเบียน package ใน installed.json (registry)
            json.JsonObject pkgEntry = new json.JsonObject();
            pkgEntry.put("name", name);
            pkgEntry.put("version", version);
            pkgEntry.put("mode", global ? "global" : "local");
            pkgEntry.put("installedAt", java.time.Instant.now().toString());
            pkgEntry.put("installPath", installDir.toString());
            pkgEntry.put("modules", moduleEntries);
            pkgs.put(name, pkgEntry);
            saveRegistry(global, registry);

            // ลงทะเบียน modules เข้ากับไฟล์ config ที่ runtime ใช้จริง
            registerModules(global, name, moduleEntries);

            System.out.println("[Install] '%s' v%s installed (%s) -> %s"
                .formatted(name, version, global ? "global" : "local", installDir));
        } catch (IOException e) {
            System.err.println("[Error] Install failed: " + e.getMessage());
            System.exit(1);
        } finally {
            cleanTemp();
        }
    }

    // ---------------------------------------------------------------------
    // remove <name> [-g|--global]
    // ---------------------------------------------------------------------
    public static void cmdRemove(String name, boolean global) {
        try {
            json.JsonObject registry = loadRegistry(global);
            json.JsonObject pkgs = registry.getJsonObject("packages");

            if (!pkgs.has(name)) {
                System.err.println("[Error] Package '%s' is not installed (%s scope)"
                    .formatted(name, global ? "global" : "local"));
                json.JsonObject otherPkgs = loadRegistry(!global).getJsonObject("packages");
                if (otherPkgs.has(name)) {
                    System.err.println("        Hint: it is installed in the %s scope. Try again %s."
                        .formatted(global ? "local" : "global", global ? "without -g/--global" : "with -g/--global"));
                }
                System.exit(1);
                return;
            }

            json.JsonObject entry = pkgs.getJsonObject(name);
            Object installPathObj = getOrNull(entry, "installPath");
            if (installPathObj != null) {
                Path installPath = Paths.get(installPathObj.toString());
                if (Files.exists(installPath)) {
                    deleteDirectoryFully(installPath);
                }
            }

            pkgs.remove(name);
            saveRegistry(global, registry);
            unregisterModules(global, name);

            System.out.println("[Remove] '%s' removed (%s scope)".formatted(name, global ? "global" : "local"));
        } catch (IOException e) {
            System.err.println("[Error] Remove failed: " + e.getMessage());
            System.exit(1);
        }
    }

    // ---------------------------------------------------------------------
    // list [-g|--global] [--all]
    // ---------------------------------------------------------------------
    public static void cmdList(boolean global, boolean all) {
        try {
            if (all) {
                printRegistry(false);
                printRegistry(true);
            } else {
                printRegistry(global);
            }
        } catch (IOException e) {
            System.err.println("[Error] List failed: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void printRegistry(boolean global) throws IOException {
        json.JsonObject registry = loadRegistry(global);
        json.JsonObject pkgs = registry.getJsonObject("packages");

        System.out.println("== %s packages (%s) =="
            .formatted(global ? "Global" : "Local", packagesDir(global)));

        if (pkgs.size() == 0) {
            System.out.println("  (none installed)");
            return;
        }
        for (Map.Entry<String, Object> e : pkgs) {
            json.JsonObject info = (json.JsonObject) e.getValue();
            json.JsonArray modules = info.has("modules") && info.get("modules") instanceof json.JsonArray
                ? info.getJsonArray("modules") : new json.JsonArray();
            System.out.println("  - %s v%s [%d module(s)] installed %s"
                .formatted(info.opt("name", e.getKey()), info.optString("version", "?"),
                    modules.size(), info.opt("installedAt", "?")));
        }
    }

    // ---------------------------------------------------------------------
    // Dispatch
    // ---------------------------------------------------------------------
    public static void execute() {
        if (argv.isEmpty()) {
            printUsage();
            System.exit(1);
            return;
        }

        boolean global = hasFlag("-g", "--global");
        boolean force = hasFlag("-f", "--force");
        boolean all = hasFlag("--all");
        String cmd = argv.get(0);

        switch (cmd) {
            case "install": {
                if (argv.size() != 2) {
                    System.err.println("[Error] Usage: install <path-to-package> [-g|--global] [-f|--force]");
                    System.exit(1);
                    return;
                }
                cmdInstall(argv.get(1), global, force);
                break;
            }
            case "remove": {
                if (argv.size() != 2) {
                    System.err.println("[Error] Usage: remove <package-name> [-g|--global]");
                    System.exit(1);
                    return;
                }
                cmdRemove(argv.get(1), global);
                break;
            }
            case "list": {
                cmdList(global, all);
                break;
            }
            case "load": {
                // เดิม: ใช้สำหรับ stage + validate init.json อย่างเดียว (คงไว้เพื่อความเข้ากันได้)
                if (argv.size() != 2) {
                    System.err.println("[Error] Usage: load <path-to-package>");
                    System.exit(1);
                    return;
                }
                Path target = Paths.get(argv.get(1)).toAbsolutePath().normalize();
                System.out.println("Load package in path %s".formatted(target));
                if (!Files.exists(target)) {
                    System.err.println("[Error] Path not found %s".formatted(target));
                    System.exit(1);
                    return;
                }
                try {
                    stagePackage(target);
                    System.out.println("[Load] init.json validated OK (staged in %s)".formatted(TempPath));
                } catch (IOException e) {
                    System.err.println("[Error] " + e.getMessage());
                    cleanTemp();
                    System.exit(1);
                }
                break;
            }
            case "help":
            case "--help":
            case "-h": {
                printUsage();
                break;
            }
            default:
                System.err.println("[Error] Unknown command: " + cmd);
                printUsage();
                System.exit(1);
        }
    }

    private static void printUsage() {
        System.err.println("Usage: luapip <command> [args] [flags]");
        System.err.println("Commands:");
        System.err.println("  install <path-to-package|dir>   Install a package (.zip or directory with init.json)");
        System.err.println("  remove <name>                   Remove an installed package");
        System.err.println("  list                             List installed packages");
        System.err.println("Flags:");
        System.err.println("  -g, --global    Act on the permanent/global scope instead of the current project");
        System.err.println("  -f, --force     Reinstall over an existing package");
        System.err.println("  --all           (list only) show both local and global packages");
    }

    public static void main(String[] args) {
        try {
            setupArgvMapping();
            MotherPath = Paths.get(
                LuaPip.class.getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI()
            ).getParent();
            MainPath = MotherPath.getParent();
            CurrentPath = Paths.get(System.getProperty("user.dir"));
            TempPath = Paths.get(MainPath.toString(), "temp");
            readConfig();
            parseArgs(args);
            if (MainConfig.optBoolean("debug", false)) {
                System.out.println("[Debug] Debug mode");
            }
            execute();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}