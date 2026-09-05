/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package baritone.utils.schematic;

import baritone.utils.schematic.litematica.LitematicaHelper;
import baritone.utils.schematic.schematica.SchematicaHelper;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

public final class OptionalSchematicPresenceTest {

    private static final String[] OPTIONAL_PLACEHOLDER_TYPES = {
            "com.github.lunatrius.schematica.Schematica",
            "com.github.lunatrius.schematica.proxy.ClientProxy",
            "com.github.lunatrius.schematica.client.world.SchematicWorld",
            "fi.dy.masa.litematica.Litematica",
            "fi.dy.masa.litematica.data.DataManager",
            "fi.dy.masa.litematica.world.WorldSchematic"
    };

    @Test
    public void helperApisRemainLoadableOnCoreRuntime() throws ClassNotFoundException {
        assertNotNull(Class.forName(SchematicaHelper.class.getName(), false,
                OptionalSchematicPresenceTest.class.getClassLoader()));
        assertNotNull(Class.forName(LitematicaHelper.class.getName(), false,
                OptionalSchematicPresenceTest.class.getClassLoader()));
    }

    @Test
    public void optionalPresenceHelpersReportAbsentMods() {
        assertFalse("Schematica must be absent from the core runtime",
                SchematicaHelper.isSchematicaPresent());
        assertFalse("Litematica must be absent from the core runtime",
                LitematicaHelper.isLitematicaPresent());
    }

    @Test
    public void optionalPlaceholderTypesAreAbsentFromCoreRuntime() {
        ClassLoader loader = OptionalSchematicPresenceTest.class.getClassLoader();
        for (String typeName : OPTIONAL_PLACEHOLDER_TYPES) {
            String resourceName = typeName.replace('.', '/') + ".class";
            assertNull(typeName + " must not be on the production runtime classpath",
                    loader.getResource(resourceName));
            try {
                Class.forName(typeName, false, loader);
                fail(typeName + " must not be loadable from the production runtime classpath");
            } catch (ClassNotFoundException expected) {
                // Expected: optional integration classes are not core classes.
            }
        }
    }
}
