package pe.jconf.argus;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("demo-local")
class ArgusApplicationTests {
    @Autowired
    private ApplicationContext context;

    @LocalServerPort
    private int port;

    @Test
    void startsLocallyAndServesHealthWithoutAnExternalChatModel() throws Exception {
        assertTrue(context.getBeansOfType(ChatModel.class).isEmpty());
        var request = HttpRequest.newBuilder(
                URI.create("http://127.0.0.1:" + port + "/actuator/health")).build();
        try (var client = HttpClient.newHttpClient()) {
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("\"UP\""));
        }
    }
}
