import java.util.*;

/**
 * json.java — JSON library แบบไฟล์เดียว ใช้งานได้จริง ไม่ต้องพึ่ง dependency ภายนอก
 *
 * ความสามารถ:
 *  - Parse JSON string -> JsonObject / JsonArray / String / Number / Boolean / null
 *  - สร้าง JSON เองด้วย JsonObject / JsonArray (สไตล์คล้าย org.json)
 *  - Serialize กลับเป็น String (ปกติ หรือ pretty-print)
 *
 * ตัวอย่างการใช้งาน (parse):
 *   Object data = json.parse("{\"name\":\"Tom\",\"age\":20,\"tags\":[\"a\",\"b\"]}");
 *   JsonObject obj = (JsonObject) data;
 *   String name = obj.getString("name");
 *   int age = obj.getInt("age");
 *
 * ตัวอย่างการใช้งาน (สร้าง JSON เอง):
 *   JsonObject obj = new JsonObject();
 *   obj.put("name", "Tom");
 *   obj.put("age", 20);
 *   JsonArray tags = new JsonArray();
 *   tags.add("a"); tags.add("b");
 *   obj.put("tags", tags);
 *   System.out.println(obj.toString(2)); // pretty print เยื้อง 2 ช่อง
 */
public final class json {

    private json() {}

    // ==================== PUBLIC ENTRY POINTS ====================

    /** Parse ข้อความ JSON เป็น Object (JsonObject, JsonArray, String, Double/Long, Boolean, หรือ null) */
    public static Object parse(String jsonText) {
        Parser p = new Parser(jsonText);
        Object result = p.parseValue();
        p.skipWhitespace();
        if (!p.isAtEnd()) {
            throw new JsonParseException("มีข้อมูลเกินหลังจบ JSON ที่ตำแหน่ง " + p.pos);
        }
        return result;
    }

    /** แปลง Object ใดๆ (JsonObject, JsonArray, String, Number, Boolean, null, List, Map) เป็น JSON string แบบบรรทัดเดียว */
    public static String stringify(Object value) {
        StringBuilder sb = new StringBuilder();
        Writer.write(value, sb, -1, 0);
        return sb.toString();
    }

    /** แปลง Object เป็น JSON string แบบ pretty-print (เยื้องตาม indentSpaces) */
    public static String stringify(Object value, int indentSpaces) {
        StringBuilder sb = new StringBuilder();
        Writer.write(value, sb, indentSpaces, 0);
        return sb.toString();
    }

    /** Exception เฉพาะของ json เวลา parse ไม่สำเร็จ */
    public static class JsonParseException extends RuntimeException {
        public JsonParseException(String message) { super(message); }
    }

    // ==================== JsonObject ====================

    /** แทน JSON object {...} ภายในใช้ LinkedHashMap เพื่อรักษาลำดับ key ตามที่ใส่ */
    public static class JsonObject implements Iterable<Map.Entry<String, Object>> {
        private final LinkedHashMap<String, Object> map = new LinkedHashMap<>();

        public JsonObject() {}

        // ---- put: เพิ่ม/แก้ไขค่า (คืน this เพื่อ chain ได้) ----
        public JsonObject put(String key, Object value) { map.put(key, value); return this; }
        public JsonObject put(String key, String value) { map.put(key, value); return this; }
        public JsonObject put(String key, int value)     { map.put(key, (long) value); return this; }
        public JsonObject put(String key, long value)    { map.put(key, value); return this; }
        public JsonObject put(String key, double value)  { map.put(key, value); return this; }
        public JsonObject put(String key, boolean value) { map.put(key, value); return this; }

        public boolean has(String key) { return map.containsKey(key); }
        public boolean isNull(String key) { return map.containsKey(key) && map.get(key) == null; }
        public void remove(String key) { map.remove(key); }
        public Set<String> keys() { return map.keySet(); }
        public int size() { return map.size(); }

        // ---- get: ดึงค่าแบบ "ต้องมี" ไม่งั้น throw ----
        public Object get(String key) {
            if (!map.containsKey(key)) throw new NoSuchElementException("ไม่พบ key: " + key);
            return map.get(key);
        }
        public String getString(String key) { return (String) get(key); }
        public boolean getBoolean(String key) { return (Boolean) get(key); }
        public int getInt(String key) { return ((Number) get(key)).intValue(); }
        public long getLong(String key) { return ((Number) get(key)).longValue(); }
        public double getDouble(String key) { return ((Number) get(key)).doubleValue(); }
        public JsonObject getJsonObject(String key) { return (JsonObject) get(key); }
        public JsonArray getJsonArray(String key) { return (JsonArray) get(key); }

        // ---- opt: ดึงค่าแบบ "ถ้าไม่มีใช้ default" ไม่ throw ----
        public Object opt(String key, Object def) { return map.containsKey(key) ? map.get(key) : def; }
        public String optString(String key, String def) {
            Object v = map.get(key);
            return v instanceof String ? (String) v : def;
        }
        public int optInt(String key, int def) {
            Object v = map.get(key);
            return v instanceof Number ? ((Number) v).intValue() : def;
        }
        public double optDouble(String key, double def) {
            Object v = map.get(key);
            return v instanceof Number ? ((Number) v).doubleValue() : def;
        }
        public boolean optBoolean(String key, boolean def) {
            Object v = map.get(key);
            return v instanceof Boolean ? (Boolean) v : def;
        }

        @Override
        public Iterator<Map.Entry<String, Object>> iterator() { return map.entrySet().iterator(); }

        Map<String, Object> raw() { return map; }

        @Override
        public String toString() { return json.stringify(this); }
        public String toString(int indentSpaces) { return json.stringify(this, indentSpaces); }
    }

    // ==================== JsonArray ====================

    /** แทน JSON array [...] ภายในใช้ ArrayList */
    public static class JsonArray implements Iterable<Object> {
        private final List<Object> list = new ArrayList<>();

        public JsonArray() {}

        public JsonArray add(Object value) { list.add(value); return this; }
        public JsonArray add(String value)  { list.add(value); return this; }
        public JsonArray add(int value)     { list.add((long) value); return this; }
        public JsonArray add(long value)    { list.add(value); return this; }
        public JsonArray add(double value)  { list.add(value); return this; }
        public JsonArray add(boolean value) { list.add(value); return this; }

        public int size() { return list.size(); }
        public boolean isEmpty() { return list.isEmpty(); }
        public void remove(int index) { list.remove(index); }

        public Object get(int index) { return list.get(index); }
        public String getString(int index) { return (String) list.get(index); }
        public boolean getBoolean(int index) { return (Boolean) list.get(index); }
        public int getInt(int index) { return ((Number) list.get(index)).intValue(); }
        public long getLong(int index) { return ((Number) list.get(index)).longValue(); }
        public double getDouble(int index) { return ((Number) list.get(index)).doubleValue(); }
        public JsonObject getJsonObject(int index) { return (JsonObject) list.get(index); }
        public JsonArray getJsonArray(int index) { return (JsonArray) list.get(index); }

        @Override
        public Iterator<Object> iterator() { return list.iterator(); }

        List<Object> raw() { return list; }

        @Override
        public String toString() { return json.stringify(this); }
        public String toString(int indentSpaces) { return json.stringify(this, indentSpaces); }
    }

    // ==================== PARSER (ภายใน) ====================

    private static final class Parser {
        private final String s;
        private int pos = 0;

        Parser(String s) { this.s = s; }

        boolean isAtEnd() { return pos >= s.length(); }

        void skipWhitespace() {
            while (pos < s.length()) {
                char c = s.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') pos++;
                else break;
            }
        }

        char peek() {
            if (pos >= s.length()) throw new JsonParseException("ข้อมูล JSON จบก่อนเวลาอันควรที่ตำแหน่ง " + pos);
            return s.charAt(pos);
        }

        char next() { return s.charAt(pos++); }

        void expect(char c) {
            if (isAtEnd() || s.charAt(pos) != c) {
                throw new JsonParseException("คาดหวังอักขระ '" + c + "' ที่ตำแหน่ง " + pos);
            }
            pos++;
        }

        Object parseValue() {
            skipWhitespace();
            char c = peek();
            switch (c) {
                case '{': return parseObject();
                case '[': return parseArray();
                case '"': return parseString();
                case 't': expectLiteral("true"); return Boolean.TRUE;
                case 'f': expectLiteral("false"); return Boolean.FALSE;
                case 'n': expectLiteral("null"); return null;
                default:
                    if (c == '-' || Character.isDigit(c)) return parseNumber();
                    throw new JsonParseException("อักขระที่ไม่คาดคิด '" + c + "' ที่ตำแหน่ง " + pos);
            }
        }

        void expectLiteral(String literal) {
            if (pos + literal.length() > s.length() || !s.startsWith(literal, pos)) {
                throw new JsonParseException("คาดหวังคำว่า '" + literal + "' ที่ตำแหน่ง " + pos);
            }
            pos += literal.length();
        }

        JsonObject parseObject() {
            JsonObject obj = new JsonObject();
            expect('{');
            skipWhitespace();
            if (peek() == '}') { pos++; return obj; }
            while (true) {
                skipWhitespace();
                if (peek() != '"') throw new JsonParseException("คาดหวัง key เป็น string ที่ตำแหน่ง " + pos);
                String key = parseString();
                skipWhitespace();
                expect(':');
                Object value = parseValue();
                obj.put(key, value);
                skipWhitespace();
                char c = next();
                if (c == '}') break;
                if (c != ',') throw new JsonParseException("คาดหวัง ',' หรือ '}' ที่ตำแหน่ง " + (pos - 1));
            }
            return obj;
        }

        JsonArray parseArray() {
            JsonArray arr = new JsonArray();
            expect('[');
            skipWhitespace();
            if (peek() == ']') { pos++; return arr; }
            while (true) {
                Object value = parseValue();
                arr.add(value);
                skipWhitespace();
                char c = next();
                if (c == ']') break;
                if (c != ',') throw new JsonParseException("คาดหวัง ',' หรือ ']' ที่ตำแหน่ง " + (pos - 1));
            }
            return arr;
        }

        String parseString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                char c = next();
                if (c == '"') break;
                if (c == '\\') {
                    char esc = next();
                    switch (esc) {
                        case '"': sb.append('"'); break;
                        case '\\': sb.append('\\'); break;
                        case '/': sb.append('/'); break;
                        case 'b': sb.append('\b'); break;
                        case 'f': sb.append('\f'); break;
                        case 'n': sb.append('\n'); break;
                        case 'r': sb.append('\r'); break;
                        case 't': sb.append('\t'); break;
                        case 'u':
                            if (pos + 4 > s.length()) throw new JsonParseException("unicode escape ไม่สมบูรณ์ที่ตำแหน่ง " + pos);
                            String hex = s.substring(pos, pos + 4);
                            sb.append((char) Integer.parseInt(hex, 16));
                            pos += 4;
                            break;
                        default:
                            throw new JsonParseException("escape sequence ไม่ถูกต้อง '\\" + esc + "' ที่ตำแหน่ง " + (pos - 1));
                    }
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }

        Object parseNumber() {
            int start = pos;
            if (peek() == '-') pos++;
            while (!isAtEnd() && Character.isDigit(peek())) pos++;
            boolean isDouble = false;
            if (!isAtEnd() && peek() == '.') {
                isDouble = true;
                pos++;
                while (!isAtEnd() && Character.isDigit(peek())) pos++;
            }
            if (!isAtEnd() && (peek() == 'e' || peek() == 'E')) {
                isDouble = true;
                pos++;
                if (!isAtEnd() && (peek() == '+' || peek() == '-')) pos++;
                while (!isAtEnd() && Character.isDigit(peek())) pos++;
            }
            String numStr = s.substring(start, pos);
            if (isDouble) {
                return Double.parseDouble(numStr);
            } else {
                try {
                    return Long.parseLong(numStr);
                } catch (NumberFormatException e) {
                    return Double.parseDouble(numStr); // เลขใหญ่เกิน long
                }
            }
        }
    }

    // ==================== WRITER (ภายใน) ====================

    private static final class Writer {

        @SuppressWarnings("unchecked")
        static void write(Object value, StringBuilder sb, int indent, int depth) {
            if (value == null) {
                sb.append("null");
            } else if (value instanceof JsonObject) {
                writeObjectEntries(((JsonObject) value).raw(), sb, indent, depth);
            } else if (value instanceof JsonArray) {
                writeArrayItems(((JsonArray) value).raw(), sb, indent, depth);
            } else if (value instanceof Map) {
                writeObjectEntries((Map<String, Object>) value, sb, indent, depth);
            } else if (value instanceof List) {
                writeArrayItems((List<Object>) value, sb, indent, depth);
            } else if (value instanceof String) {
                writeString((String) value, sb);
            } else if (value instanceof Boolean || value instanceof Number) {
                sb.append(value.toString());
            } else {
                // fallback: ไม่รู้จักชนิด ให้แปลงเป็น string
                writeString(value.toString(), sb);
            }
        }

        static void writeObjectEntries(Map<String, Object> map, StringBuilder sb, int indent, int depth) {
            if (map.isEmpty()) { sb.append("{}"); return; }
            sb.append('{');
            boolean pretty = indent >= 0;
            int i = 0, n = map.size();
            for (Map.Entry<String, Object> e : map.entrySet()) {
                if (pretty) newlineIndent(sb, indent, depth + 1);
                writeString(e.getKey(), sb);
                sb.append(':');
                if (pretty) sb.append(' ');
                write(e.getValue(), sb, indent, depth + 1);
                if (++i < n) sb.append(',');
            }
            if (pretty) newlineIndent(sb, indent, depth);
            sb.append('}');
        }

        static void writeArrayItems(List<Object> list, StringBuilder sb, int indent, int depth) {
            if (list.isEmpty()) { sb.append("[]"); return; }
            sb.append('[');
            boolean pretty = indent >= 0;
            int n = list.size();
            for (int i = 0; i < n; i++) {
                if (pretty) newlineIndent(sb, indent, depth + 1);
                write(list.get(i), sb, indent, depth + 1);
                if (i < n - 1) sb.append(',');
            }
            if (pretty) newlineIndent(sb, indent, depth);
            sb.append(']');
        }

        static void newlineIndent(StringBuilder sb, int indent, int depth) {
            sb.append('\n');
            for (int i = 0; i < indent * depth; i++) sb.append(' ');
        }

        static void writeString(String str, StringBuilder sb) {
            sb.append('"');
            for (int i = 0; i < str.length(); i++) {
                char c = str.charAt(i);
                switch (c) {
                    case '"': sb.append("\\\""); break;
                    case '\\': sb.append("\\\\"); break;
                    case '\b': sb.append("\\b"); break;
                    case '\f': sb.append("\\f"); break;
                    case '\n': sb.append("\\n"); break;
                    case '\r': sb.append("\\r"); break;
                    case '\t': sb.append("\\t"); break;
                    default:
                        if (c < 0x20) {
                            sb.append(String.format("\\u%04x", (int) c));
                        } else {
                            sb.append(c);
                        }
                }
            }
            sb.append('"');
        }
    }
}
