package baritone.command.defaults;

import baritone.api.selection.ISelection;
import baritone.api.utils.BetterBlockPos;
import baritone.selection.Selection;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CommandQualityCleanup20260902SelectionTest {

    @Test
    public void selectionOriginUsesEverySelection() {
        ISelection[] selections = {
                new Selection(new BetterBlockPos(10, 10, 10), new BetterBlockPos(12, 12, 12)),
                new Selection(new BetterBlockPos(-2, 15, 3), new BetterBlockPos(0, 16, 4))
        };

        assertEquals(new BetterBlockPos(-2, 10, 3), SelCommand.selectionOrigin(selections));
    }
}
