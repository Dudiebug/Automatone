package automatone.worker.verification;

import automatone.worker.WorkerEntity;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.jar.JarFile;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.conditions.ArchConditions.dependOnClassesThat;

public final class WorkerArchitectureRulesTest {
    private static final DescribedPredicate<JavaClass> NON_API_BARITONE = new DescribedPredicate<>(
            "non-API Baritone classes"
    ) {
        @Override
        public boolean test(JavaClass input) {
            String name = input.getName();
            return name.equals("baritone.Baritone")
                    || (name.startsWith("baritone.") && !name.startsWith("baritone.api."));
        }
    };

    private static final ArchRule WORKER_NO_CLIENT_OR_LEGACY_DEPENDENCIES = noClasses()
            .that().resideInAnyPackage("automatone.worker..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "net.minecraft.client..",
                    "net.neoforged.neoforge.client..",
                    "net.fabricmc..",
                    "org.quiltmc..",
                    "dev.onyxstudios.cardinal_components..");

    private static final ArchRule WORKER_DOES_NOT_OWN_NATIVE_ENGINE = noClasses()
            .that().resideInAnyPackage("automatone.worker..")
            .should(dependOnClassesThat(NON_API_BARITONE));

    private static final ArchRule WORKER_DOES_NOT_USE_FAKE_PLAYER = noClasses()
            .that().resideInAnyPackage("automatone.worker..")
            .should().dependOnClassesThat().haveFullyQualifiedName(
                    "net.neoforged.neoforge.common.util.FakePlayer");

    private static final ArchRule WORKER_DOES_NOT_USE_FAKE_PLAYER_FACTORY = noClasses()
            .that().resideInAnyPackage("automatone.worker..")
            .should().dependOnClassesThat().haveFullyQualifiedName(
                    "net.neoforged.neoforge.common.util.FakePlayerFactory");

    private static final ArchRule WORKER_DOES_NOT_USE_SERVER_PLAYER = noClasses()
            .that().resideInAnyPackage("automatone.worker..")
            .should().dependOnClassesThat().haveFullyQualifiedName("net.minecraft.server.level.ServerPlayer");

    private static final ArchRule LIBRARY_DOES_NOT_DEPEND_ON_WORKER = noClasses()
            .that().resideInAnyPackage("baritone", "baritone..")
            .should().dependOnClassesThat().resideInAnyPackage("automatone.worker..");

    @Test
    public void workerRemainsServerOnlyAndDoesNotUseLegacyLoaders() throws URISyntaxException {
        WORKER_NO_CLIENT_OR_LEGACY_DEPENDENCIES.check(importProductionClasses(WorkerEntity.class));
    }

    @Test
    public void workerDoesNotDuplicateNativePathingOrMining() throws URISyntaxException {
        WORKER_DOES_NOT_OWN_NATIVE_ENGINE.check(importProductionClasses(WorkerEntity.class));
    }

    @Test
    public void workerDoesNotUseFakeOrRealPlayerInfrastructure() throws URISyntaxException {
        JavaClasses workerClasses = importProductionClasses(WorkerEntity.class);
        WORKER_DOES_NOT_USE_FAKE_PLAYER.check(workerClasses);
        WORKER_DOES_NOT_USE_SERVER_PLAYER.check(workerClasses);
        WORKER_DOES_NOT_USE_FAKE_PLAYER_FACTORY.check(workerClasses);
    }

    @Test
    public void libraryBytecodeDoesNotDependOnWorkerImplementation() throws URISyntaxException {
        LIBRARY_DOES_NOT_DEPEND_ON_WORKER.check(importProductionClasses(baritone.Baritone.class));
    }

    private static JavaClasses importProductionClasses(Class<?> anchor) throws URISyntaxException {
        if (anchor.getProtectionDomain().getCodeSource() == null
                || anchor.getProtectionDomain().getCodeSource().getLocation() == null) {
            throw new IllegalStateException("Production class location is unavailable for " + anchor.getName());
        }
        Path location = Path.of(anchor.getProtectionDomain().getCodeSource().getLocation().toURI());
        if (java.nio.file.Files.isDirectory(location)) {
            return new ClassFileImporter().importPath(location);
        }
        try (JarFile jar = new JarFile(location.toFile())) {
            return new ClassFileImporter().importJar(jar);
        } catch (IOException exception) {
            throw new IllegalStateException("Production class archive cannot be imported: " + location, exception);
        }
    }

}
