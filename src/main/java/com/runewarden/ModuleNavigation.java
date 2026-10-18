/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.util.ArrayList;
import java.util.List;

/** Bounded sidebar history. Navigation never carries a character's feature state. */
final class ModuleNavigation
{
    private static final int LIMIT = 32;
    private final List<String> history = new ArrayList<>();
    private int cursor = -1;

    void visit(String module)
    {
        if (module == null || module.isEmpty() || module.equals(current())) return;
        while (history.size() > cursor + 1) history.remove(history.size() - 1);
        history.add(module);
        if (history.size() > LIMIT) history.remove(0);
        cursor = history.size() - 1;
    }

    String current() { return cursor < 0 ? null : history.get(cursor); }
    boolean canBack() { return cursor > 0; }
    boolean canForward() { return cursor >= 0 && cursor + 1 < history.size(); }
    String back() { return canBack() ? history.get(--cursor) : null; }
    String forward() { return canForward() ? history.get(++cursor) : null; }
    int size() { return history.size(); }
    void reset(String current)
    {
        history.clear(); cursor = -1; visit(current);
    }
}
