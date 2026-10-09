package vn.t3nexus.lib.observability;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Configuration;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cổng quản trị riêng (feature 07, bước 9): các endpoint vận hành như {@code loggers} nằm ở cổng khác với cổng công khai, nghe địa chỉ nội bộ,
 * và đổi được mức log của một lớp lúc đang chạy mà không khởi động lại.
 */
@SpringBootTest(
        classes = ManagementEndpointsTest.Cfg.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "management.tracing.export.enabled=false")
class ManagementEndpointsTest {

    @Configuration
    @EnableAutoConfiguration
    static class Cfg {
    }

    @LocalServerPort int serverPort;
    @LocalManagementPort int managementPort;

    private final HttpClient http = HttpClient.newHttpClient();

    private HttpResponse<String> send(HttpRequest request) throws Exception {
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void management_runs_on_its_own_port_and_the_loggers_endpoint_is_not_served_on_the_public_port() throws Exception {
        assertThat(managementPort).isNotEqualTo(serverPort);

        HttpResponse<String> onManagement = send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + managementPort + "/actuator/loggers/ROOT")).build());
        HttpResponse<String> onPublic = send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + serverPort + "/actuator/loggers/ROOT")).build());

        assertThat(onManagement.statusCode()).isEqualTo(200);
        assertThat(onPublic.statusCode()).isEqualTo(404);
    }

    @Test
    void changing_a_loggers_level_at_runtime_takes_effect_without_restart() throws Exception {
        Logger logger = LoggerFactory.getLogger("test.Dynamic.Level");
        assertThat(logger.isDebugEnabled()).isFalse();

        HttpResponse<String> changed = send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + managementPort + "/actuator/loggers/test.Dynamic.Level"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"configuredLevel\":\"DEBUG\"}")).build());

        assertThat(changed.statusCode()).isEqualTo(204);
        assertThat(logger.isDebugEnabled()).isTrue();

        send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + managementPort + "/actuator/loggers/test.Dynamic.Level"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"configuredLevel\":null}")).build());
        assertThat(logger.isDebugEnabled()).isFalse();
    }
}
