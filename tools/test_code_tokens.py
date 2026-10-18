import unittest
from measure_code_tokens import without_comments


class TokenSourceTests(unittest.TestCase):
    def test_comments_are_excluded_but_urls_are_preserved(self):
        source = 'String source = "https://example.invalid/a/*b*/"; // comment\n/* block */ int x = 1;'
        actual = without_comments(source)
        self.assertIn('"https://example.invalid/a/*b*/"', actual)
        self.assertNotIn('comment', actual)
        self.assertNotIn('block', actual)
        self.assertIn('int x = 1;', actual)

    def test_escaped_quotes_and_character_literals_remain_code(self):
        source = r'''String value = "a\"//b"; char c = '/'; /* removed */'''
        actual = without_comments(source)
        self.assertIn(r'"a\"//b"', actual)
        self.assertIn("char c = '/';", actual)
        self.assertNotIn('removed', actual)

    def test_comment_removal_cannot_join_identifiers(self):
        self.assertEqual('left right', without_comments('left/* note */right'))
