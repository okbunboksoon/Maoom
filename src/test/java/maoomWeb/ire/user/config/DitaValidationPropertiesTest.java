package maoomWeb.ire.user.config;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class DitaValidationPropertiesTest {
    @Test
    void acceptsTheDefaultConfiguration() {
        assertDoesNotThrow(new DitaValidationProperties()::afterPropertiesSet);
    }

    @Test
    void rejectsInvalidLimitsAndAStagingFolderInsideGomsOutput() {
        DitaValidationProperties invalidLimit = new DitaValidationProperties();
        invalidLimit.setAsyncThreads(0);
        assertThrows(IllegalStateException.class, invalidLimit::afterPropertiesSet);

        DitaValidationProperties invalidPath = new DitaValidationProperties();
        invalidPath.setGomsOutputRoot("C:/validation");
        invalidPath.setStagingRoot("C:/validation/temp");
        assertThrows(IllegalStateException.class, invalidPath::afterPropertiesSet);
    }
}
