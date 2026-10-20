package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** AppAdapter.tileWidthPx with rows_below_dock: the dock, its title and the rows must fit the grid's height. */
public class TileFitTest {

    private static final float D = 2f;              // 1080p TV: 2 px per dp
    private static final int INNER = 1696;          // grid width minus padding at 1920 px
    private static final int GRID_H = 846;          // grid height under the top bar

    private static int height(int tileW, int rows, boolean title) {
        int itemH = AppAdapter.itemHeightPx(AppAdapter.tileHeightPx(tileW), D);
        return itemH * (rows + 1) + Math.round((AppAdapter.DOCK_EXTRA_DP + AppAdapter.BOTTOM_ROOM_DP
                + (title ? AppAdapter.TITLE_ROOM_DP : 0)) * D);
    }

    @Test
    public void noRowsBelowDockKeepsTheWidthOnlySize() {
        int widthOnly = AppAdapter.tileWidthPx(INNER, 4, D);
        assertEquals(widthOnly, AppAdapter.tileWidthPx(INNER, GRID_H, 4, 0, true, D));
        assertEquals(widthOnly, AppAdapter.tileWidthPx(INNER, 0, 4, 3, true, D));   // not laid out yet
    }

    @Test
    public void dockTitleAndRowsFitTheHeight() {
        for (int rows = 1; rows <= 3; rows++) {
            for (boolean title : new boolean[]{true, false}) {
                if (rows == 3 && title) continue;   // below the 80dp minimum, see the next test
                int w = AppAdapter.tileWidthPx(INNER, GRID_H, 4, rows, title, D);
                assertTrue("rows " + rows + " title " + title, height(w, rows, title) <= GRID_H + 2);
            }
        }
    }

    @Test
    public void threeRowsWithTheTitleStopAtTheMinimumTile() {
        // Tiles would have to be about 23dp tall; they stop at the 80dp-wide minimum instead (dock_title false fits).
        assertEquals(Math.round(80 * D), AppAdapter.tileWidthPx(INNER, GRID_H, 4, 3, true, D));
    }

    @Test
    public void droppingTheTitleGivesBiggerTiles() {
        assertTrue(AppAdapter.tileWidthPx(INNER, GRID_H, 4, 3, false, D)
                > AppAdapter.tileWidthPx(INNER, GRID_H, 4, 3, true, D));
    }
}
