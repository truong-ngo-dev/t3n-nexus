package vn.t3nexus.sellerwebbff;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import vn.t3nexus.lib.webbff.EnableWebBff;

@EnableWebBff
@SpringBootApplication
public class SellerWebBffApplication {

    public static void main(String[] args) {
        SpringApplication.run(SellerWebBffApplication.class, args);
    }
}
