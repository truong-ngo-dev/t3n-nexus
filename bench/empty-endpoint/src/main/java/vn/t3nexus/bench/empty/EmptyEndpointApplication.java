package vn.t3nexus.bench.empty;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Điểm cuối rỗng của bộ đo nền: một {@code GET /ping} trả hằng, không bảo mật, không log yêu cầu, không cơ sở dữ liệu.
 * Số đo là trần của khung Spring Boot trên máy này, để so với dịch vụ thật.
 */
@SpringBootApplication
@RestController
public class EmptyEndpointApplication {

    public static void main(String[] args) {
        SpringApplication.run(EmptyEndpointApplication.class, args);
    }

    @GetMapping("/ping")
    public String ping() {
        return "ok";
    }
}
