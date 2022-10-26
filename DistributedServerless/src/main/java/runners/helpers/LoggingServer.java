package runners.helpers;

import java.util.logging.Logger;

public interface LoggingServer {
    default Logger initializeLogging(String s, Boolean boolean1){
        return Logger.getLogger("Logger");
    }
}