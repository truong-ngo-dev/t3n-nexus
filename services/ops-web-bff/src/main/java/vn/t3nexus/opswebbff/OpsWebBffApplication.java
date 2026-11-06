package vn.t3nexus.opswebbff;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import vn.t3nexus.lib.webbff.EnableWebBff;

@EnableWebBff
@SpringBootApplication
public class OpsWebBffApplication {

    public static void main(String[] args) {
        SpringApplication.run(OpsWebBffApplication.class, args);
    }
}
