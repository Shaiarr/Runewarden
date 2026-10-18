"""Regression fixtures for conservative extraction, without the upstream checkout."""
import unittest
from quest_stage_parser import frontiers, strip


class QuestStageParserTest(unittest.TestCase):
    def test_resolved_branches_use_earliest_leaf(self):
        raw = '''public Map loadSteps() {
            ConditionalStep choose = new ConditionalStep(this, late);
            choose.addStep(flag, early);
            steps.put(4, choose);
        }'''
        self.assertEqual({4: 2}, frontiers(strip(raw), {'early': 2, 'late': 8}))

    def test_cast_mutation_excludes_node_and_its_dependents(self):
        raw = '''public Map loadSteps() {
            conditional = new ConditionalStep(this, late);
            ((ConditionalStep) conditional).addStep(flag, early);
            wrapper = new ConditionalStep(this, conditional);
            steps.put(4, conditional);
            steps.put(5, wrapper);
            steps.put(6, safe);
        }'''
        self.assertEqual({6: 1}, frontiers(strip(raw), {'early': 2, 'late': 8, 'safe': 1}))

    def test_this_qualified_cast_cannot_bypass_ambiguity(self):
        raw = '''public Map loadSteps() {
            conditional = new ConditionalStep(this, late);
            ((ConditionalStep) this.conditional).addStep(flag, early);
            steps.put(4, this.conditional);
        }'''
        self.assertEqual({}, frontiers(strip(raw), {'conditional': 10, 'early': 2, 'late': 8}))

    def test_qualified_mutation_and_one_argument_mutation_are_unknown(self):
        raw = '''public Map loadSteps() {
            first = new ConditionalStep(this, late);
            this.first.addStep(flag, early);
            second = new ConditionalStep(this, late);
            second.addStep(otherConditional);
            steps.put(4, first); steps.put(5, second);
        }'''
        self.assertEqual({}, frontiers(strip(raw), {'early': 2, 'late': 8}))

    def test_duplicate_constructor_symbol_is_unknown_even_when_this_qualified(self):
        raw = '''field = new ConditionalStep(this, early);
        public Map loadSteps() {
            field = new ConditionalStep(this, late);
            steps.put(4, this.field);
        }'''
        self.assertEqual({}, frontiers(strip(raw), {'field': 12, 'early': 2, 'late': 8}))

    def test_final_unresolved_put_removes_earlier_value(self):
        raw = '''public Map loadSteps() {
            steps.put(4, early); steps.put(4, missing);
            steps.put(5, missing); steps.put(5, late);
        }'''
        self.assertEqual({5: 8}, frontiers(strip(raw), {'early': 2, 'late': 8}))

    def test_other_method_stage_puts_and_string_comments_are_ignored(self):
        raw = '''public Map loadSteps() {
            // steps.put(4, late);
            String hint = "steps.put(5, late)";
            steps.put(6, early);
        }
        public Map helper() { steps.put(6, late); }'''
        self.assertEqual({6: 2}, frontiers(strip(raw), {'early': 2, 'late': 8}))


if __name__ == '__main__':
    unittest.main()
