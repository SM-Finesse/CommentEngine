import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 시험용 생성 서버 (조각 10). llm-server의 {@code POST /v1/generate}와 같은 모양으로 답한다. 모델은 없다.
 * <p>
 * 참고용 레포의 small-gen 을 새 생성 규약(조각 6, 「llm-server 범용 생성 입구 추가」 지침서)으로 바꾼 것.
 * 엔진(조각 5 · 7b)과 칩(조각 8 · 9)을 GPU 없이 개발하고, CI에서 돌리는 데 쓴다.
 *
 * <pre>
 * 요청  {"messages":[{"role":"system|user|assistant","content":"..."}], "max_tokens":1~512, "temperature":0~2, "top_p":0~1}
 * 응답  200 {"text":"...", "finish_reason":"stop", "elapsed_ms":12}
 * 에러  422 형식이 틀림 (모르는 칸도 422 — 실제 llm-server와 같음)
 *       503 생성기 사용 중 (서버당 한 번에 하나. FAIL_RATE 로 흉내도 냄)
 * 그 밖 GET /health → {"status":"ok"}
 * </pre>
 *
 * 실행 (빌드 없음, JDK 25):
 * <pre>
 *   java e2e/test-gen/TestGenServer.java          (기본 9200 포트)
 *   java e2e/test-gen/TestGenServer.java 9201     (다른 포트)
 * </pre>
 * 환경 변수 (PowerShell 예: {@code $env:FAIL_RATE=50; java e2e/test-gen/TestGenServer.java}):
 * <pre>
 *   TEXT       돌려줄 문장. 기본 "[test-gen:포트] 시험용 생성 서버의 응답입니다."
 *   DELAY_MS   매 요청 이만큼 늦게 답함 (엔진 마감 시험)
 *   FAIL_RATE  0~100. 이 비율만큼 503 (다른 서버로 넘기기 시험)
 *   BUSY_LOCK  1이면 실제 llm-server처럼 서버당 한 번에 하나, 겹치면 503. 기본 0 (겹쳐도 답함)
 * </pre>
 * 분야를 모른다: 받은 프롬프트를 읽지 않고, 분야 단어도 쓰지 않는다.
 */
public class TestGenServer {

    private static final Set<String> TOP_KEYS = Set.of("messages", "max_tokens", "temperature", "top_p");
    private static final Set<String> MESSAGE_KEYS = Set.of("role", "content");
    private static final Set<String> ROLES = Set.of("system", "user", "assistant");

    private static final int DELAY_MS = intEnv("DELAY_MS", 0);
    private static final int FAIL_RATE = intEnv("FAIL_RATE", 0);
    private static final boolean BUSY_LOCK = intEnv("BUSY_LOCK", 0) == 1;
    private static final AtomicBoolean busy = new AtomicBoolean(false);

    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 9200;
        String text = System.getenv().getOrDefault("TEXT", "[test-gen:" + port + "] 시험용 생성 서버의 응답입니다.");

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());

        server.createContext("/health", ex -> {
            if (!"GET".equals(ex.getRequestMethod())) {
                send(ex, 405, "{\"detail\":\"Method Not Allowed\"}");
                return;
            }
            send(ex, 200, "{\"status\":\"ok\"}");
        });

        server.createContext("/v1/generate", ex -> {
            if (!"POST".equals(ex.getRequestMethod())) {
                send(ex, 405, "{\"detail\":\"Method Not Allowed\"}");
                return;
            }
            long t0 = System.nanoTime();
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String problem = validate(body);
            if (problem != null) {
                log("422 " + problem);
                send(ex, 422, "{\"detail\":" + quote(problem) + "}");
                return;
            }
            if (ThreadLocalRandom.current().nextInt(100) < FAIL_RATE) {
                log("503 (FAIL_RATE)");
                send(ex, 503, "{\"detail\":\"생성기 사용 중\"}");
                return;
            }
            if (BUSY_LOCK && !busy.compareAndSet(false, true)) {
                log("503 (BUSY_LOCK)");
                send(ex, 503, "{\"detail\":\"생성기 사용 중\"}");
                return;
            }
            try {
                if (DELAY_MS > 0) {
                    Thread.sleep(DELAY_MS);
                }
                long elapsed = (System.nanoTime() - t0) / 1_000_000;
                log("200 " + elapsed + "ms");
                send(ex, 200, "{\"text\":" + quote(text) + ",\"finish_reason\":\"stop\",\"elapsed_ms\":" + elapsed + "}");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                send(ex, 502, "{\"detail\":\"생성 실패\"}");
            } finally {
                if (BUSY_LOCK) {
                    busy.set(false);
                }
            }
        });

        server.start();
        System.out.println("test-gen listening on :" + port + "  DELAY_MS=" + DELAY_MS + "  FAIL_RATE=" + FAIL_RATE + "%  BUSY_LOCK=" + BUSY_LOCK);
    }

    /** 요청이 규약에 맞으면 null, 아니면 무엇이 틀렸는지 */
    static String validate(String body) {
        Object root;
        try {
            root = new Json(body).parse();
        } catch (IllegalArgumentException e) {
            return "JSON이 아님: " + e.getMessage();
        }
        if (!(root instanceof Map<?, ?> req)) {
            return "본문은 JSON 객체여야 함";
        }
        for (Object k : req.keySet()) {
            if (!TOP_KEYS.contains(k)) {
                return "모르는 칸: " + k;
            }
        }
        if (!(req.get("messages") instanceof List<?> messages) || messages.isEmpty()) {
            return "messages는 1개 이상인 배열이어야 함";
        }
        for (Object m : messages) {
            if (!(m instanceof Map<?, ?> msg)) {
                return "messages의 각 칸은 객체여야 함";
            }
            for (Object k : msg.keySet()) {
                if (!MESSAGE_KEYS.contains(k)) {
                    return "messages에 모르는 칸: " + k;
                }
            }
            if (!ROLES.contains(msg.get("role"))) {
                return "role은 system · user · assistant 중 하나: " + msg.get("role");
            }
            if (!(msg.get("content") instanceof String)) {
                return "content는 문자열이어야 함";
            }
        }
        if (!(req.get("max_tokens") instanceof Double mt) || mt != Math.floor(mt) || mt < 1 || mt > 512) {
            return "max_tokens는 1~512 정수여야 함 (필수)";
        }
        String r = range(req, "temperature", 0, 2);
        if (r != null) {
            return r;
        }
        return range(req, "top_p", 0, 1);
    }

    private static String range(Map<?, ?> req, String key, double min, double max) {
        if (!req.containsKey(key)) {
            return null;
        }
        if (!(req.get(key) instanceof Double v) || v < min || v > max) {
            return key + "는 " + min + "~" + max + " 숫자여야 함";
        }
        return null;
    }

    /** 의존성 없이 쓰는 작은 JSON 읽개 (객체 → Map, 배열 → List, 숫자 → Double) */
    static final class Json {
        private final String s;
        private int i;

        Json(String s) {
            this.s = s;
        }

        Object parse() {
            Object v = value();
            ws();
            if (i != s.length()) {
                throw new IllegalArgumentException("끝에 남은 글자 (" + i + ")");
            }
            return v;
        }

        private Object value() {
            ws();
            if (i >= s.length()) {
                throw new IllegalArgumentException("값이 없음");
            }
            char c = s.charAt(i);
            return switch (c) {
                case '{' -> object();
                case '[' -> array();
                case '"' -> string();
                case 't' -> word("true", Boolean.TRUE);
                case 'f' -> word("false", Boolean.FALSE);
                case 'n' -> word("null", null);
                default -> number();
            };
        }

        private Map<String, Object> object() {
            Map<String, Object> m = new LinkedHashMap<>();
            i++;
            ws();
            if (peek('}')) {
                i++;
                return m;
            }
            while (true) {
                ws();
                if (!peek('"')) {
                    throw new IllegalArgumentException("칸 이름이 없음 (" + i + ")");
                }
                String k = string();
                ws();
                expect(':');
                m.put(k, value());
                ws();
                if (peek(',')) {
                    i++;
                } else {
                    expect('}');
                    return m;
                }
            }
        }

        private List<Object> array() {
            List<Object> a = new ArrayList<>();
            i++;
            ws();
            if (peek(']')) {
                i++;
                return a;
            }
            while (true) {
                a.add(value());
                ws();
                if (peek(',')) {
                    i++;
                } else {
                    expect(']');
                    return a;
                }
            }
        }

        private String string() {
            StringBuilder b = new StringBuilder();
            i++;
            while (i < s.length()) {
                char c = s.charAt(i++);
                if (c == '"') {
                    return b.toString();
                }
                if (c == '\\') {
                    if (i >= s.length()) {
                        break;
                    }
                    char e = s.charAt(i++);
                    switch (e) {
                        case 'n' -> b.append('\n');
                        case 't' -> b.append('\t');
                        case 'r' -> b.append('\r');
                        case 'b' -> b.append('\b');
                        case 'f' -> b.append('\f');
                        case 'u' -> {
                            if (i + 4 > s.length()) {
                                throw new IllegalArgumentException("\\u 뒤 글자가 모자람");
                            }
                            b.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                            i += 4;
                        }
                        default -> b.append(e);
                    }
                } else {
                    b.append(c);
                }
            }
            throw new IllegalArgumentException("문자열이 닫히지 않음");
        }

        private Double number() {
            int start = i;
            while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) {
                i++;
            }
            try {
                return Double.parseDouble(s.substring(start, i));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("알 수 없는 값 (" + start + ")");
            }
        }

        private Object word(String w, Object v) {
            if (!s.startsWith(w, i)) {
                throw new IllegalArgumentException("알 수 없는 값 (" + i + ")");
            }
            i += w.length();
            return v;
        }

        private void ws() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
                i++;
            }
        }

        private boolean peek(char c) {
            return i < s.length() && s.charAt(i) == c;
        }

        private void expect(char c) {
            if (!peek(c)) {
                throw new IllegalArgumentException("'" + c + "'가 와야 함 (" + i + ")");
            }
            i++;
        }
    }

    private static void send(HttpExchange ex, int status, String json) throws IOException {
        byte[] b = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, b.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(b);
        }
    }

    private static String quote(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default -> {
                    if (c < 0x20) {
                        b.append(String.format("\\u%04x", (int) c));
                    } else {
                        b.append(c);
                    }
                }
            }
        }
        return b.append('"').toString();
    }

    private static void log(String line) {
        System.out.println("<- /v1/generate -> " + line);
    }

    private static int intEnv(String key, int def) {
        String v = System.getenv(key);
        return v == null || v.isBlank() ? def : Integer.parseInt(v.strip());
    }
}
