package baritone.command.defaults;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

final class CommandActionAliases {

    private CommandActionAliases() {}

    static <E extends Enum<E>> E getByName(E[] values, Function<E, String[]> aliases, String name) {
        for (E value : values) {
            for (String alias : aliases.apply(value)) {
                if (alias.equalsIgnoreCase(name)) {
                    return value;
                }
            }
        }
        return null;
    }

    static <E extends Enum<E>> String[] getAllNames(E[] values, Function<E, String[]> aliases) {
        Set<String> names = new HashSet<>();
        for (E value : values) {
            names.addAll(Arrays.asList(aliases.apply(value)));
        }
        return names.toArray(new String[0]);
    }
}
