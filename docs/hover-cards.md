# Shared hover cards

`HoverCardOverlay` is the sole production card renderer for NPC reference hovers and inventory/bank skilling-resource hovers. `RunewardenSightOverlay` retains the actor outline and LOS line on `ABOVE_SCENE`, while the resource `WidgetItemOverlay` retains native item visibility and clipping checks. Both submit content to a separately registered `DYNAMIC` overlay at `ALWAYS_ON_TOP` and `PRIORITY_HIGHEST`.

The card uses current-frame content dimensions before computing placement. Its preferred position is above the pointer; if that exceeds the top edge, it is clamped to the top rather than flipped below the pointer. All four edges remain inside the canvas. The renderer wraps text by actual font metrics, splits unbroken long words, and caps excessive height to complete rows. Resource level-requirement warning colours survive the conversion from RuneLite colour-tagged text.

Every top-layer render consumes and clears the pending submission before checking game state, menu state or mouse bounds. A frame without a valid item or NPC submission paints no card. Right-click menus suppress the box so the menu remains usable. Shutdown and account changes clear the shared renderer. No native tooltip or plugin settings are modified.

The five-argument `ResourceTooltipOverlay` constructor retains the older `TooltipManager` route for existing standalone test fixtures. Production injection always uses its six-argument constructor and the shared top-layer renderer. Sight-tool five- and six-argument constructors also remain compatible; production injection adds the shared renderer as the seventh argument.
