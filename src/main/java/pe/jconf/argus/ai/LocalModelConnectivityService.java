package pe.jconf.argus.ai;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("demo-local & !demo-azure")
final class LocalModelConnectivityService implements ModelConnectivityService {
    @Override
    public ModelConnectivityResult check() {
        return new ModelConnectivityResult(ModelConnectivityResult.Mode.SIMULATED, true, null, 0,
                "SIMULATED_CONNECTIVITY_OK", null, null);
    }
}
