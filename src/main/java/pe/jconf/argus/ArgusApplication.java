package pe.jconf.argus;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import pe.jconf.argus.ai.ConnectivityDiagnostic;
import pe.jconf.argus.investigation.InvestigationDiagnostic;

@SpringBootApplication
public class ArgusApplication {

    public static void main(String[] args) {
        if (ConnectivityDiagnostic.requested(args)) {
            System.exit(ConnectivityDiagnostic.run(ArgusApplication.class, args));
        } else if (InvestigationDiagnostic.requested(args)) {
            System.exit(InvestigationDiagnostic.run(ArgusApplication.class, args));
        } else {
            SpringApplication.run(ArgusApplication.class, args);
        }
    }
}
