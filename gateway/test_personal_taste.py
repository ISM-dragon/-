import tempfile
import unittest
from pathlib import Path

from gateway.personal_taste import better_recommendations, empty_state, load_state, record_event, save_state, similarity_recommendations


class PersonalTasteTest(unittest.TestCase):
    def test_likes_and_dislikes_update_profile_incrementally(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "taste.json"
            save_state(path, empty_state())
            for _ in range(10):
                record_event(path, {"event_type": "LIKE", "reason": "strong_hook", "features": {"hook": 1}})
            positive = load_state(path)["profile"]["hook"]["weight"]
            for _ in range(10):
                record_event(path, {"event_type": "DISLIKE", "reason": "strong_hook", "features": {"hook": 1}})
            negative = load_state(path)["profile"]["hook"]["weight"]
            self.assertGreater(positive, 0)
            self.assertLess(negative, positive)
            self.assertEqual(load_state(path)["profile"]["hook"]["sample_count"], 20)

    def test_more_like_this_balances_topic_style_and_duration(self):
        profile = empty_state()["profile"]
        selected = {"topic": "ai editing", "duration_sec": 30, "features": {"hook": 0.8, "story": 0.7}}
        candidates = [
            {"id": "same", "topic": "ai editing", "duration_sec": 31, "score": 70, "features": {"hook": 0.8, "story": 0.7}},
            {"id": "topic_only", "topic": "ai editing", "duration_sec": 90, "score": 70, "features": {"hook": -0.8, "story": -0.6}},
        ]
        results = similarity_recommendations(profile, selected, candidates, 5)
        self.assertEqual(results[0]["id"], "same")
        self.assertIn("similar duration", results[0]["explanation"])

    def test_find_better_requires_threshold_and_returns_delta(self):
        selected = {"topic": "ai editing", "score": 84, "features": {"hook": 0.2}}
        candidates = [
            {"id": "86", "topic": "ai editing", "score": 86, "features": {"hook": 0.2, "clarity": 0.0, "ending_strength": 0.0}},
            {"id": "93", "topic": "ai editing", "score": 93, "features": {"hook": 0.9, "clarity": 0.9, "ending_strength": 0.9}},
        ]
        results = better_recommendations(empty_state()["profile"], selected, candidates, 5)
        self.assertEqual(results[0]["id"], "93")
        self.assertGreaterEqual(results[0]["score_delta"], 5)
        self.assertNotIn("86", [item["id"] for item in results])


if __name__ == "__main__":
    unittest.main()
