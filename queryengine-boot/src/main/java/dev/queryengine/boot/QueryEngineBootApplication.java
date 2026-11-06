package dev.queryengine.boot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.env.Environment;

@SpringBootApplication
public class QueryEngineBootApplication implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(QueryEngineBootApplication.class);

    private final Environment environment;

    public QueryEngineBootApplication(Environment environment) {
        this.environment = environment;
    }

    public static void main(String[] args) {
        SpringApplication.run(QueryEngineBootApplication.class, args);
    }

    @Override
    public void run(String... args) {
        String port = environment.getProperty("local.server.port", environment.getProperty("server.port", "8080"));
        log.info("QueryEngine 已监听 http://127.0.0.1:{}", port);
        log.info("试一下: curl -s -X POST http://127.0.0.1:" + port
                + "/get -H 'Content-Type: application/json' -d '{\"User\":{\"id\":38710}}'");
    }
}
