package baritone.utils.type;

import org.junit.Test;

import java.util.SplittableRandom;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

/**
 * Regression coverage for VarInt encoding and its value-object ownership boundary.
 */
public class VarIntSerializationContractTest {

    private static final int[] BOUNDARY_VALUES = {
            0,
            1,
            127,
            128,
            255,
            256,
            16_384,
            Integer.MAX_VALUE,
            Integer.MIN_VALUE,
            -1
    };

    @Test
    public void roundTripsBoundaryValues() {
        for (int value : BOUNDARY_VALUES) {
            assertRoundTrip(value);
        }
    }

    @Test
    public void roundTripsSeededBoundedIntegers() {
        SplittableRandom random = new SplittableRandom(0x5EEDC0DEL);
        for (int i = 0; i < 512; i++) {
            assertRoundTrip(random.nextInt(-1_000_000, 1_000_001));
        }
    }

    @Test
    public void serializedArrayMutationDoesNotChangeFutureEncoding() {
        VarInt value = new VarInt(128);
        byte[] expected = value.serialize().clone();
        byte[] returned = value.serialize();
        returned[0] ^= 0x7F;

        assertArrayEquals(expected, value.serialize());
        assertEquals(value.getValue(), VarInt.read(value.serialize()).getValue());
    }

    private static void assertRoundTrip(int value) {
        assertEquals(value, VarInt.read(new VarInt(value).serialize()).getValue());
    }
}
