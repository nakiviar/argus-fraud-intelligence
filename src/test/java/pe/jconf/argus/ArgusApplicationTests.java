package pe.jconf.argus;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.server.context.WebServerApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArgusApplicationTests {

    @Test
    void startsLocallyAndServesHealthWithoutAnExternalChatModel() throws Exception {
        try (var context = SpringApplication.run(ArgusApplication.class,
                "--spring.profiles.active=demo-local", "--server.port=0")) {
            assertTrue(context.getBeansOfType(ChatModel.class).isEmpty());

            int port = ((WebServerApplicationContext) context).getWebServer().getPort();
            var request = HttpRequest.newBuilder(
                    URI.create("http://127.0.0.1:" + port + "/actuator/health")).build();
            try (var client = HttpClient.newHttpClient()) {
                var response = client.send(request, HttpResponse.BodyHandlers.ofString());
                assertEquals(200, response.statusCode());
                assertTrue(response.body().contains("\"UP\""));
            }
        }
    }
}
