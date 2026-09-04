package baritone.verification;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.Test;

import java.io.File;
import java.net.URISyntaxException;
import java.security.CodeSource;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ServerArchitectureRulesTest {
    private static final ArchRule SERVER_CORE_NO_CLIENT_DEPENDENCIES = noClasses()
            .that().resideInAnyPackage(
                    "baritone.behavior..",
                    "baritone.cache..",
                    "baritone.pathing..",
                    "baritone.process..")
            .should().dependOnClassesThat().resideInAnyPackage("net.minecraft.client..");

    private static final ArchRule NO_LEGACY_LOADER_RUNTIME_DEPENDENCIES = noClasses()
            .should().dependOnClassesThat().resideInAnyPackage(
                    "net.fabricmc..",
                    "org.quiltmc..",
                    "dev.onyxstudios.cardinal_components..");

    @Test
    public void serverCoreMustNotDependOnClientClasses() throws URISyntaxException {
        SERVER_CORE_NO_CLIENT_DEPENDENCIES.check(importProductionClasses());
    }

    @Test
    public void runtimeMustNotDependOnLegacyLoaderClasses() throws URISyntaxException {
        NO_LEGACY_LOADER_RUNTIME_DEPENDENCIES.check(importProductionClasses());
    }

    @Test
    public void negativeControlRejectsClientDependentClass() {
        try {
            SERVER_CORE_NO_CLIENT_DEPENDENCIES.check(new ClassFileImporter().importClasses(
                    baritone.pathing.sensor.fixture.ClientDependentFixture.class));
            fail("The client-dependent fixture must violate the server-core rule.");
        } catch (AssertionError expected) {
            assertTrue(expected.getMessage().contains("ClientDependentFixture"));
        }
    }

    @Test
    public void positiveControlAcceptsServerOnlyClass() {
        SERVER_CORE_NO_CLIENT_DEPENDENCIES.check(new ClassFileImporter().importClasses(
                baritone.pathing.sensor.fixture.ServerOnlyFixture.class));
    }

    private static JavaClasses importProductionClasses() throws URISyntaxException {
        CodeSource codeSource = baritone.Baritone.class.getProtectionDomain().getCodeSource();
        if (codeSource == null || codeSource.getLocation() == null) {
            throw new IllegalStateException("Production class location is unavailable.");
        }
        return new ClassFileImporter().importPath(new File(codeSource.getLocation().toURI()).toPath());
    }
}
