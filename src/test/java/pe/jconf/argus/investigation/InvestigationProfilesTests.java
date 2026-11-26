package pe.jconf.argus.investigation;

import org.junit.jupiter.api.Test;
import org.springframework.boot.Banner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import pe.jconf.argus.ArgusApplication;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class InvestigationProfilesTests {
    @Test
    void azureRegistersOnlyItsRealAdapterAndMakesNoCallsAtStartup() {
        try (var context = new SpringApplicationBuilder(ArgusApplication.class)
                .web(WebApplicationType.NONE).bannerMode(Banner.Mode.OFF).logStartupInfo(false)
                .properties("logging.level.root=OFF").run("--spring.profiles.active=demo-azure",
                        "--spring.ai.openai.base-url=https://fixture.openai.azure.com",
                        "--spring.ai.openai.chat.model=fixture-deployment",
                        "--AZURE_OPENAI_ACCESS_TOKEN=test-only-bearer")) {
            assertEquals(2, context.getBeansOfType(InvestigationAgent.class).size());
            assertInstanceOf(SpringAiAgentModel.class, context.getBean(AgentModel.class));
        }
    }
}
