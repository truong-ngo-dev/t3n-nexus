package vn.t3nexus.storefrontwebbff;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import vn.t3nexus.lib.webbff.EnableWebBff;

@EnableWebBff
@SpringBootApplication
public class StorefrontWebBffApplication {

    public static void main(String[] args) {
        SpringApplication.run(StorefrontWebBffApplication.class, args);
    }
}
