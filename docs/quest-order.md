# Quest route

The Guides sidebar's Quests category defaults to **Optimal order**, following the OSRS Wiki regular member account route. **Name** restores alphabetical order. Search retains the selected order; **Hide completed quests** uses RuneLite's actual quest state. Manual checklist ticks do not mark a quest finished in this filter.

The next-quest card offers the earliest unfinished ranked quest whose bundled prerequisites can be verified from live client facts. If no such quest is verified, it offers the first remaining route entry as **Next in route**. A missing or unsupported requirement remains unknown. Quest state and skills are read on the client thread; queued results are discarded when the character changes.

`quest-order.json` records 238 route rows, including the source's supporting milestones. It ranks 220 of the 238 bundled quest-category guides. The other 18 remain visible at the end, labelled **Unranked**; no positions are invented. Equivalent Quest Helper titles for Recipe for Disaster, Tutorial Island and four diary entries share the source's position. The whole Recipe for Disaster guide has no single route position and remains unranked; its individual subquests are ranked.

Source: [OSRS Wiki optimal quest guide, revision 15358869](https://oldschool.runescape.wiki/w/Optimal_quest_guide?oldid=15358869), revision timestamp 2026-09-30T12:59:44Z, retrieved 2026-10-16 through the Wiki API. This is the regular-account route, intended to minimise direct skill training; it does not promise minimum travel time or cost. Only row order and titles are adapted. The website is not contacted by the plugin at runtime.

Wiki contributors' order data is licensed **CC BY-NC-SA 3.0**. See `licenses/quest-order-CC-BY-NC-SA.txt`. Original Java code remains BSD-2-Clause.
