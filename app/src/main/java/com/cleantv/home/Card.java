package com.cleantv.home;

import java.util.ArrayList;
import java.util.List;

/** What one card shows right now. Built by a Source from real data. */
final class Card {
    String id = "", title = "", big = "", sub = "", detail = "", badge = "", credit = "";
    int accent = 0xFFFFFFFF;
    float progress = -1f;      // 0..1, or -1 for no bar
    long updated;              // when the underlying data was fetched
    boolean offline;           // showing saved data because the network failed
    final List<String> lines = new ArrayList<>();   // extra rows for the detail screen
}
