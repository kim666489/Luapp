import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class LuaPip {
    public static Path MotherPath;
    public static Path CurrentPath;
    public static Path MainPath;
    public static json.JsonObject MainConfig;
    public static ArrayList<String> argv = new ArrayList<>();
    public static HashMap<String, List<String>> argvMapping = new HashMap<>();

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

    public static void deleteDirectory(Path targetDir) throws IOException {
        if (!Files.exists(targetDir)) {
            return;
        }
        try (var paths = Files.walk(targetDir)) {
            for (Path path : paths.sorted((first, second) -> second.compareTo(first)).toList()) {
                Files.deleteIfExists(path);
            }
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

    public static void editConfig(String[] args) {
        for (String arg : args) {
            String[] parse = arg.split("=", 2);
            List<String> mapping = argvMapping.get(parse[0]);

            if (mapping == null) {
                // ไม่ตรงกับ mapping ใด ๆ -> เก็บไว้เป็น argument ทั่วไป
                argv.add(arg);
                continue;
            }

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
        }
    }

    private static void printUsage() {
        System.out.println("LuaPip package manager");
        System.out.println("  install <package.zip|folder> [--mode project|permanent]");
        System.out.println("  list [--mode project|permanent]");
        System.out.println("  remove <name> [--mode project|permanent]");
        System.out.println("  help");
    }

    private static String parseMode(List<String> args, int start) {
        String mode = "project";
        for (int index = start; index < args.size(); index++) {
            String arg = args.get(index);
            if (arg.startsWith("--mode=")) {
                mode = arg.substring("--mode=".length());
            } else if (arg.equals("--mode") || arg.equals("-m")) {
                if (++index >= args.size()) {
                    throw new IllegalArgumentException("--mode ต้องตามด้วย project หรือ permanent");
                }
                mode = args.get(index);
            } else if (arg.startsWith("-")) {
                throw new IllegalArgumentException("ไม่รู้จัก option: " + arg);
            }
        }
        mode = mode.toLowerCase(Locale.ROOT);
        if (mode.equals("global")) {
            mode = "permanent";
        }
        if (!mode.equals("project") && !mode.equals("permanent")) {
            throw new IllegalArgumentException("mode ต้องเป็น project หรือ permanent");
        }
        return mode;
    }

    private static Path getPackagesPath(String mode) {
        if (mode.equals("project")) {
            return CurrentPath.resolve("packages").normalize();
        }
        return Paths.get(System.getProperty("user.home"), ".luapip", "packages").toAbsolutePath().normalize();
    }

    private static Path extractArchive(Path archive, Path destination) throws IOException {
        Files.createDirectories(destination);
        try (InputStream input = Files.newInputStream(archive);
             ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                Path output = destination.resolve(entry.getName()).normalize();
                if (!output.startsWith(destination)) {
                    throw new IOException("ZIP มี path ที่ออกนอกโฟลเดอร์แพ็กเกจ: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(output);
                } else {
                    Files.createDirectories(output.getParent());
                    Files.copy(zip, output, StandardCopyOption.REPLACE_EXISTING);
                }
                zip.closeEntry();
            }
        }
        return findPackageRoot(destination);
    }

    private static Path findPackageRoot(Path extractedPath) throws IOException {
        if (Files.isRegularFile(extractedPath.resolve("init.json"))) {
            return extractedPath;
        }
        List<Path> packageRoots;
        try (var children = Files.list(extractedPath)) {
            packageRoots = children.filter(Files::isDirectory)
                    .filter(path -> Files.isRegularFile(path.resolve("init.json")))
                    .toList();
        }
        if (packageRoots.size() != 1) {
            throw new IOException("ไม่พบ init.json ที่รากแพ็กเกจ หรือพบหลายแพ็กเกจในไฟล์ ZIP");
        }
        return packageRoots.get(0);
    }

    private static json.JsonObject readManifest(Path packageRoot) throws IOException {
        Object parsed = json.parse(Files.readString(packageRoot.resolve("init.json")));
        if (!(parsed instanceof json.JsonObject)) {
            throw new IOException("init.json ต้องเป็น JSON object");
        }
        json.JsonObject manifest = (json.JsonObject) parsed;
        String name = manifest.optString("name", "");
        if (!name.matches("[A-Za-z0-9][A-Za-z0-9._-]*")) {
            throw new IOException("init.json ต้องมี name ที่ใช้ได้ (ตัวอักษร ตัวเลข . _ -)");
        }
        return manifest;
    }

    private static void copyPackage(Path source, Path destination) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attrs) throws IOException {
                if (Files.isSymbolicLink(directory)) {
                    throw new IOException("ไม่รองรับ symbolic link ในแพ็กเกจ: " + directory);
                }
                Files.createDirectories(destination.resolve(source.relativize(directory)));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (Files.isSymbolicLink(file)) {
                    throw new IOException("ไม่รองรับ symbolic link ในแพ็กเกจ: " + file);
                }
                Files.copy(file, destination.resolve(source.relativize(file)));
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void installPackage(String packageArgument, String mode) throws IOException {
        Path source = Paths.get(packageArgument).toAbsolutePath().normalize();
        if (!Files.exists(source)) {
            throw new IOException("ไม่พบแพ็กเกจ: " + source);
        }

        Path temporaryPath = null;
        try {
            Path packageRoot;
            if (Files.isDirectory(source)) {
                packageRoot = findPackageRoot(source);
            } else {
                temporaryPath = Files.createTempDirectory("luapip-");
                packageRoot = extractArchive(source, temporaryPath);
            }

            json.JsonObject manifest = readManifest(packageRoot);
            String name = manifest.getString("name");
            Path packagesPath = getPackagesPath(mode);
            Path destination = packagesPath.resolve(name).normalize();
            if (!destination.startsWith(packagesPath)) {
                throw new IOException("ชื่อแพ็กเกจไม่ถูกต้อง");
            }
            if (Files.exists(destination)) {
                throw new IOException("ติดตั้งแพ็กเกจ " + name + " อยู่แล้วใน " + mode);
            }

            Files.createDirectories(packagesPath);
            try {
                copyPackage(packageRoot, destination);
            } catch (IOException error) {
                deleteDirectory(destination);
                throw error;
            }
            System.out.println("ติดตั้ง " + name + " สำเร็จที่ " + destination);
        } finally {
            if (temporaryPath != null) {
                deleteDirectory(temporaryPath);
            }
        }
    }

    private static void listPackages(String mode) throws IOException {
        Path packagesPath = getPackagesPath(mode);
        if (!Files.isDirectory(packagesPath)) {
            System.out.println("ยังไม่มีแพ็กเกจในโหมด " + mode);
            return;
        }
        try (var packages = Files.list(packagesPath)) {
            List<Path> installed = packages.filter(Files::isDirectory).sorted().toList();
            if (installed.isEmpty()) {
                System.out.println("ยังไม่มีแพ็กเกจในโหมด " + mode);
                return;
            }
            for (Path packagePath : installed) {
                String version = "";
                Path manifestPath = packagePath.resolve("init.json");
                if (Files.isRegularFile(manifestPath)) {
                    try {
                        version = readManifest(packagePath).optString("version", "");
                    } catch (Exception ignored) {
                        version = "(init.json ไม่ถูกต้อง)";
                    }
                }
                System.out.println(packagePath.getFileName() + (version.isEmpty() ? "" : " " + version));
            }
        }
    }

    private static void removePackage(String name, String mode) throws IOException {
        if (!name.matches("[A-Za-z0-9][A-Za-z0-9._-]*")) {
            throw new IOException("ชื่อแพ็กเกจไม่ถูกต้อง");
        }
        Path packagesPath = getPackagesPath(mode);
        Path packagePath = packagesPath.resolve(name).normalize();
        if (!packagePath.startsWith(packagesPath) || !Files.exists(packagePath)) {
            throw new IOException("ไม่พบแพ็กเกจ " + name + " ในโหมด " + mode);
        }
        deleteDirectory(packagePath);
        System.out.println("ถอนการติดตั้ง " + name + " สำเร็จ");
    }

    public static void execute() throws IOException {
        if (argv.isEmpty()) {
            printUsage();
            return;
        }
        String command = argv.get(0).toLowerCase(Locale.ROOT);
        if (command.equals("help") || command.equals("--help") || command.equals("-h")) {
            printUsage();
            return;
        }

        String mode = parseMode(argv, 1);
        switch (command) {
            case "install":
            case "add":
            case "load":
                if (argv.size() < 2 || argv.get(1).startsWith("-")) {
                    throw new IllegalArgumentException("รูปแบบคำสั่ง: install <package.zip|folder> [--mode project|permanent]");
                }
                installPackage(argv.get(1), mode);
                break;
            case "list":
                listPackages(mode);
                break;
            case "remove":
            case "uninstall":
                if (argv.size() < 2 || argv.get(1).startsWith("-")) {
                    throw new IllegalArgumentException("รูปแบบคำสั่ง: remove <name> [--mode project|permanent]");
                }
                removePackage(argv.get(1), mode);
                break;
            default:
                throw new IllegalArgumentException("ไม่รู้จักคำสั่ง: " + command);
        }
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
            readConfig();
            editConfig(args);
            if (Boolean.TRUE.equals(MainConfig.getBoolean("debug"))) {
                System.out.println("[Debug] Debug mode");
            }
            execute();
        } catch (Exception e) {
                System.err.println("[Error] " + e.getMessage());
                System.exit(1);
        }
    }
}