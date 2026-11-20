package com.commentengine.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 가짜 칩(src/test/resources/chips/fake/gateway.yml)이 들고 온 경로대로 요청이 넘어가는지 본다.
 * 가짜 서버는 받은 경로를 "sample-api:" 뒤에 붙여 돌려준다. 실제 칩 폴더(chips/)는 읽지 않는다 — 본체 시험이 특정 칩에 기대지 않게.
 */
@SpringBootTest(webEnvironment = RANDOM_PORT, properties = "CHIPS_DIR=src/test/resources/chips")
class GatewayRoutingTests {

    private static final HttpServer SAMPLE_API = startFake("sample-api:");

    private final HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

    @Value("${local.server.port}")
    private int port;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("test.sample-api-url", () -> "http://127.0.0.1:" + SAMPLE_API.getAddress().getPort());
    }

    @AfterAll
    static void stop() {
        SAMPLE_API.stop(0);
    }

    @Test
    void 칩이_들고_온_경로는_칩_서비스로_넘어간다() throws Exception {
        HttpResponse<String> response = get("/api/v1/stats/someone?refresh=true");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("sample-api:/api/v1/stats/someone?refresh=true");
    }

    @Test
    void 칩_경로가_아닌_요청은_넘기지_않는다() throws Exception {
        // comment-engine은 밖에 열지 않는다 (참고용 레포의 /v1/comments/** 경로는 가져오지 않음)
        assertThat(get("/v1/comments/light").statusCode()).isEqualTo(404);
        assertThat(get("/other").statusCode()).isEqualTo(404);
    }

    @Test
    void 헬스는_gateway가_직접_답한다() throws Exception {
        HttpResponse<String> response = get("/actuator/health");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"status\":\"UP\"");
    }

    private HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    /** 받은 경로(쿼리 포함)를 name 뒤에 붙여 돌려주는 가짜 서버 */
    private static HttpServer startFake(String name) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                byte[] body = (name + exchange.getRequestURI()).getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
