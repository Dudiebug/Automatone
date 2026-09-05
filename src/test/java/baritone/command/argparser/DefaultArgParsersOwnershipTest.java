/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package baritone.command.argparser;

import baritone.api.command.argument.ICommandArgument;
import baritone.command.argument.CommandArguments;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DefaultArgParsersOwnershipTest {

    @Test
    public void defaultParsersStillParseOrdinaryBooleanAndNumericValues() throws Exception {
        List<ICommandArgument> arguments = CommandArguments.from("yes no 17 922337203685477580 3.5 -2.75");

        assertEquals(true, arguments.get(0).getAs(Boolean.class));
        assertEquals(false, arguments.get(1).getAs(Boolean.class));
        assertEquals(Integer.valueOf(17), arguments.get(2).getAs(Integer.class));
        assertEquals(Long.valueOf(922337203685477580L), arguments.get(3).getAs(Long.class));
        assertEquals(Float.valueOf(3.5F), arguments.get(4).getAs(Float.class));
        assertEquals(Double.valueOf(-2.75D), arguments.get(5).getAs(Double.class));
    }

    @Test
    public void allParserListCannotBeReplaced() {
        assertEquals(
                List.of(
                        DefaultArgParsers.IntArgumentParser.INSTANCE,
                        DefaultArgParsers.LongArgumentParser.INSTANCE,
                        DefaultArgParsers.FloatArgumentParser.INSTANCE,
                        DefaultArgParsers.DoubleArgumentParser.INSTANCE,
                        DefaultArgParsers.BooleanArgumentParser.INSTANCE
                ),
                DefaultArgParsers.ALL
        );

        assertRejectsReplacement(
                DefaultArgParsers.ALL,
                DefaultArgParsers.BooleanArgumentParser.INSTANCE
        );
    }

    @Test
    public void truthyValuesCannotBeReplaced() {
        assertEquals(
                List.of("1", "true", "yes", "t", "y", "on", "enable"),
                DefaultArgParsers.BooleanArgumentParser.TRUTHY_VALUES
        );

        assertRejectsReplacement(
                DefaultArgParsers.BooleanArgumentParser.TRUTHY_VALUES,
                "__replacement__"
        );
    }

    @Test
    public void falsyValuesCannotBeReplaced() {
        assertEquals(
                List.of("0", "false", "no", "f", "n", "off", "disable"),
                DefaultArgParsers.BooleanArgumentParser.FALSY_VALUES
        );

        assertRejectsReplacement(
                DefaultArgParsers.BooleanArgumentParser.FALSY_VALUES,
                "__replacement__"
        );
    }

    private static <T> void assertRejectsReplacement(List<T> actual, T replacement) {
        List<T> expected = List.copyOf(actual);
        boolean rejected = false;

        try {
            actual.set(0, replacement);
        } catch (UnsupportedOperationException expectedException) {
            rejected = true;
        } finally {
            if (!actual.equals(expected)) {
                actual.set(0, expected.get(0));
            }
        }

        assertTrue("built-in parser list must reject replacement", rejected);
        assertEquals(expected, actual);
    }
}
