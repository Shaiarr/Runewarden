# Quest-stage observation

Follow quest progress uses exact, source-mapped quest stages while the client
reports the quest in progress. The bundled table covers 191 quests and 2,473
literal stages from Quest Helper revision
`5f791937a99a676f23c02aa48c4e4d5b50e7edad`.

Each stage resolves to the earliest possible guide row across every statically
resolved conditional branch. This proves an earlier phase has passed; it does
not guess which alternative the player took. Unknown stages, unresolved helper
calls, ambiguous conditional symbols and unsupported quests do not advance.
The generator is tools/build-quest-stages.py, using tools/quest_stage_parser.py. Cast-receiver mutations, qualified mutations and unsupported addStep forms are rejected with their dependent branches rather than treated as fully resolved. Source URLs are retained per quest;
the existing Quest Helper BSD-2-Clause notice applies to the adapted table.

The guide follows each newly observed frontier once. Manual review of earlier
steps is preserved until the next actual frontier. The Follow quest progress
checkbox can disable this behaviour. Quest-finished observations and explicit
item-acquisition rules remain independent. Boss phases and waves remain manual.

## Branch focus and interaction targets

The separate `quest-assistance.json` table resolves finite current-stage branches to an active row without completing skipped rows. It adds source-defined NPC/object highlights, concrete step supplies and selected Recruitment Drive puzzle answers. Unknown branch evidence blocks selection; exact stage frontiers remain the completion boundary described above. See `quest-assistance.md` and `quest-assistance-coverage.json` for per-record source coverage and remaining unsupported cases.
