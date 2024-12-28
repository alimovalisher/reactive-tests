package con.fnklabs.reactive.tests;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ShellApp {
    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(ShellApp.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        app.run(args);
    }
}
