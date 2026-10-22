package com.gathin.tvhome.home;

/** Physical left/right boundaries for a grid whose first column follows its reading direction. */
public final class GridNavigation {
    private GridNavigation() { }

    public static boolean atHorizontalEdge(int position, int count, int columns, boolean rtl, boolean left) {
        boolean towardStart = left != rtl;
        if (towardStart) return position % columns == 0;
        return position % columns == columns - 1 || position == count - 1;
    }
}
