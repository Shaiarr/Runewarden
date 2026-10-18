"""Source fixtures exercise the finite extractor independently of upstream."""
import unittest
from quest_assistance_parser import Extractor, java_constants, split_args


class QuestAssistanceParserTest(unittest.TestCase):
    def parser(self, setup='', load='', leaves=None):
        if 'return' not in load: load += ' return steps;'
        raw = 'class Fixture { void setup() {' + setup + '} Map loadSteps() {' + load + '} }'
        return Extractor(raw, {'ItemID.EGG': 1944, 'ItemID.WOOL': 1737, 'VarbitID.FLAG': 100}, leaves or {'early': 2, 'late': 9})

    def test_nested_gameval_namespaces_never_replace_real_item_ids(self):
        data = java_constants('''public final class ItemID {
            public static final int EGG = 1944;
            public static class Cert { public static final int EGG = 1945; }
            public static class Placeholder { public static final int EGG = 18937; }
        }''')
        self.assertEqual({'ItemID.EGG': 1944, 'ItemID.Cert.EGG': 1945, 'ItemID.Placeholder.EGG': 18937}, data)

    def test_first_branch_order_and_unknown_predicates_are_retained(self):
        parser = self.parser('egg = new ItemRequirement("Egg", ItemID.EGG);', '''
            choose = new ConditionalStep(this, early);
            choose.addStep(unknownDialogue, late);
            choose.addStep(egg, early); steps.put(4, choose);''')
        node = parser.stages()['4']
        self.assertEqual('unknown', node['branches'][0]['when']['kind'])
        self.assertEqual([1944], node['branches'][1]['when']['ids'])
        self.assertEqual({'step': 2}, node['fallback'])

    def test_quantity_alternates_and_equipment_are_literal_and_exact(self):
        parser = self.parser('''egg = new ItemRequirement("Egg", ItemID.EGG, 2, true);
            egg.addAlternates(ItemID.WOOL);''')
        item = parser.item('egg.quantity(7).highlighted()')
        self.assertEqual(7, item['quantity'])
        self.assertEqual([1944, 1737], item['ids'])
        self.assertEqual('equipment', item['container'])

    def test_bank_charges_and_unsupported_mutation_do_not_become_inventory_checks(self):
        parser = self.parser('egg = new ItemRequirement("Egg", ItemID.EGG); egg.setShouldCheckBank(true);')
        self.assertEqual('unknown', parser.predicate('egg')['kind'])
        parser = self.parser('egg = new ItemRequirement("Egg", ItemID.EGG);')
        self.assertEqual('unknown', parser.predicate('egg.alsoCheckBank()')['kind'])

    def test_loop_mutations_and_locking_nodes_are_unknown(self):
        parser = self.parser('', '''choose = new ConditionalStep(this, late);
            for (var i : values) { choose.addStep(flag, early); }
            steps.put(1, choose);''')
        self.assertEqual({'kind': 'unknown'}, parser.stages()['1'])
        parser = self.parser('late.setLockingCondition(flag);', 'steps.put(2, late);')
        self.assertEqual({'kind': 'unknown'}, parser.stages()['2'])

    def test_duplicate_field_local_collision_is_not_silently_resolved(self):
        parser = self.parser('choose = new ConditionalStep(this, early);', '''
            choose = new ConditionalStep(this, late); steps.put(1, choose);''')
        self.assertEqual({'kind': 'unknown'}, parser.stages()['1'])

    def test_reused_condition_key_does_not_keep_an_overwritten_first_branch(self):
        parser = self.parser('', '''choose = new ConditionalStep(this, early);
            choose.addStep(flag, early); choose.addStep(flag, late); steps.put(1, choose);''')
        self.assertEqual({'kind': 'unknown'}, parser.stages()['1'])

    def test_only_complete_returned_immutable_map_builder_is_dispatch(self):
        parser = self.parser('', '''return new ImmutableMap.Builder<Integer, QuestStep>()
            .put(0, early).put(4, late).build();''')
        self.assertEqual({'0': {'step': 2}, '4': {'step': 9}}, parser.stages())
        parser = self.parser('', '''other.put(99, late); return new ImmutableMap.Builder<Integer, QuestStep>()
            .put(0, early).unknown().build();''')
        self.assertEqual({}, parser.stages())

    def test_balanced_arguments_preserve_dialogue_commas_and_arrays(self):
        self.assertEqual(['this', 'new int[]{1, 2}', '"Yes, please."', 'and(a, b)'],
                         split_args('this, new int[]{1, 2}, "Yes, please.", and(a, b)'))

    def test_source_dialogue_and_step_requirement_are_preserved(self):
        parser = self.parser('''egg = new ItemRequirement("Egg", ItemID.EGG);
            talk = new NpcStep(this, 123, new WorldPoint(1, 2, 0), "Talk.", egg);
            talk.addDialogStep("Yes, please.");''')
        hints = parser.hints({'talk': {'id': 'fixture-talk'}})['fixture-talk']
        self.assertEqual([123], hints['npcIds'])
        self.assertEqual(['Yes, please.'], hints['dialogue'])
        self.assertEqual(1944, hints['items'][0]['ids'][0])

    def test_literal_puzzle_wrapper_keeps_its_actual_object_target(self):
        parser = self.parser('''lever = new PuzzleWrapperStep(this,
            new ObjectStep(this, 42, new WorldPoint(1, 2, 0), "Pull lever A."));''')
        self.assertEqual([42], parser.hints({'lever': {'id': 'fixture-lever'}})['fixture-lever']['objectIds'])

    def test_strings_comments_and_nested_puts_cannot_invent_stage_evidence(self):
        parser = self.parser('', '''String note = "steps.put(9, late)";
            // steps.put(6, late);
            if (flag) { steps.put(7, late); }
            steps.put(1, early); steps.put(1, missing);''')
        self.assertEqual({'1': {'kind': 'unknown'}}, parser.stages())


if __name__ == '__main__':
    unittest.main()
