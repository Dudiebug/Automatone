package automatone.worker.verification;

import automatone.worker.WorkerEntity;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaEnumConstant;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.Test;

import java.io.IOException;
import java.lang.reflect.Array;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.conditions.ArchConditions.dependOnClassesThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class WorkerArchitectureRulesTest {
    private static final String WORKER_RENDERER = "automatone.worker.client.WorkerRenderer";
    private static final String EVENT_BUS_SUBSCRIBER = "net.neoforged.fml.common.EventBusSubscriber";
    private static final String SUBSCRIBE_EVENT = "net.neoforged.bus.api.SubscribeEvent";
    private static final String REGISTER_RENDERERS =
            "net.neoforged.neoforge.client.event.EntityRenderersEvent$RegisterRenderers";

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

    private static final DescribedPredicate<JavaClass> COMMON_OR_SERVER_WORKER = new DescribedPredicate<>(
            "common or server worker classes"
    ) {
        @Override
        public boolean test(JavaClass input) {
            return input.getName().startsWith("automatone.worker.")
                    && !input.getName().startsWith("automatone.worker.client.");
        }
    };

    private static final ArchRule WORKER_COMMON_DOES_NOT_DEPEND_ON_CLIENT = noClasses()
            .that(COMMON_OR_SERVER_WORKER)
            .should().dependOnClassesThat().resideInAnyPackage(
                    "automatone.worker.client..",
                    "net.minecraft.client..",
                    "net.neoforged.neoforge.client..");

    private static final ArchRule WORKER_DOES_NOT_USE_LEGACY_DEPENDENCIES = noClasses()
            .that().resideInAnyPackage("automatone.worker..")
            .should().dependOnClassesThat().resideInAnyPackage(
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
            // M5's real controller holder is addressed only at these two transport/menu boundaries.
            .and().doNotHaveFullyQualifiedName("automatone.worker.WorkerMenu")
            .and().doNotHaveFullyQualifiedName("automatone.worker.mixin.WorkerPacketListenerMixin")
            .should().dependOnClassesThat().haveFullyQualifiedName("net.minecraft.server.level.ServerPlayer");

    private static final ArchRule WORKER_DOES_NOT_EXTEND_SERVER_PLAYER = noClasses()
            .that().resideInAnyPackage("automatone.worker..")
            .should().beAssignableTo(net.minecraft.server.level.ServerPlayer.class);

    private static final ArchRule LIBRARY_DOES_NOT_DEPEND_ON_WORKER = noClasses()
            .that().resideInAnyPackage("baritone", "baritone..")
            .should().dependOnClassesThat().resideInAnyPackage("automatone.worker..");

    @Test
    public void workerKeepsCommonServerBoundaryAndDoesNotUseLegacyLoaders() throws URISyntaxException {
        JavaClasses workerClasses = importProductionClasses(WorkerEntity.class);
        WORKER_COMMON_DOES_NOT_DEPEND_ON_CLIENT.check(workerClasses);
        WORKER_DOES_NOT_USE_LEGACY_DEPENDENCIES.check(workerClasses);
    }

    @Test
    public void approvedRendererIsRestrictedToTypedClientRegistration() throws URISyntaxException {
        JavaClasses workerClasses = importProductionClasses(WorkerEntity.class);
        assertTrue("The approved worker renderer must remain a production class", workerClasses.contain(WORKER_RENDERER));
        JavaClass renderer = workerClasses.get(WORKER_RENDERER);
        assertTrue("The worker renderer must declare EventBusSubscriber", renderer.isAnnotatedWith(EVENT_BUS_SUBSCRIBER));
        JavaAnnotation<JavaClass> subscriber = renderer.getAnnotationOfType(EVENT_BUS_SUBSCRIBER);
        List<JavaEnumConstant> sides = enumConstants(subscriber.get("value").orElse(null));
        assertEquals("The renderer subscriber must declare exactly one side", 1, sides.size());
        assertEquals("The renderer subscriber must be client-only", "CLIENT", sides.get(0).name());
        assertEquals("The renderer side must come from NeoForge's Dist enum",
                "net.neoforged.api.distmarker.Dist", sides.get(0).getDeclaringClass().getName());

        JavaMethod registration = renderer.getMethods().stream()
                .filter(method -> method.getName().equals("registerRenderers"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("The renderer must declare a typed registration method"));
        assertTrue("Renderer registration must be public", registration.getModifiers().contains(JavaModifier.PUBLIC));
        assertTrue("Renderer registration must be static", registration.getModifiers().contains(JavaModifier.STATIC));
        assertTrue("Renderer registration must subscribe to the mod event bus",
                registration.tryGetAnnotationOfType(SUBSCRIBE_EVENT).isPresent());
        assertEquals("Renderer registration must take exactly one typed event", 1, registration.getRawParameterTypes().size());
        assertEquals("Renderer registration must use EntityRenderersEvent.RegisterRenderers", REGISTER_RENDERERS,
                registration.getRawParameterTypes().get(0).getName());
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
        WORKER_DOES_NOT_EXTEND_SERVER_PLAYER.check(workerClasses);
        WORKER_DOES_NOT_USE_FAKE_PLAYER_FACTORY.check(workerClasses);
    }

    @Test
    public void libraryBytecodeDoesNotDependOnWorkerImplementation() throws URISyntaxException {
        LIBRARY_DOES_NOT_DEPEND_ON_WORKER.check(importProductionClasses(baritone.Baritone.class));
    }

    private static List<JavaEnumConstant> enumConstants(Object annotationValue) {
        List<JavaEnumConstant> result = new ArrayList<>();
        if (annotationValue instanceof JavaEnumConstant constant) {
            result.add(constant);
        } else if (annotationValue instanceof Iterable<?> values) {
            for (Object value : values) {
                addEnumConstant(result, value);
            }
        } else if (annotationValue != null && annotationValue.getClass().isArray()) {
            int length = Array.getLength(annotationValue);
            for (int index = 0; index < length; index++) {
                addEnumConstant(result, Array.get(annotationValue, index));
            }
        }
        return result;
    }

    private static void addEnumConstant(List<JavaEnumConstant> result, Object value) {
        if (value instanceof JavaEnumConstant constant) {
            result.add(constant);
        }
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
