import json
import os
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from gateway import main
from pipeline.publikclip_pipeline import config
from pipeline.publikclip_pipeline.scoring.providers import ProviderRouter


class ProviderQualityTest(unittest.TestCase):
    def test_quality_budgets_are_ordered_and_bounded(self):
        self.assertLess(config.candidate_budget("fast", 600), config.candidate_budget("maximum", 600))
        self.assertEqual(config.finalist_budget("fast"), 6)
        self.assertEqual(config.finalist_budget("maximum"), 32)
        self.assertLessEqual(config.candidate_budget("maximum", 100000), 100)

    def test_provider_router_reads_profiles_without_exposing_credentials(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            providers = root / "providers.json"
            providers.write_text(json.dumps({"providers": [{
                "id": "custom",
                "name": "Custom",
                "type": "openai_compatible",
                "base_url": "https://example.test/v1",
                "credential_ref": "custom",
                "default_model": "model-a",
                "enabled": True,
                "capabilities": {"chat": True, "structured_output": True, "vision": False, "json_mode": True, "streaming": False}
            }]}))
            with patch.object(config, "home_dir", return_value=root):
                router = ProviderRouter.from_disk()
                public = router.health()
            self.assertEqual(public[0]["id"], "custom")
            self.assertNotIn("api_key", public[0])

    def test_provider_profile_endpoint_masks_keys(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with patch.object(main, "PROCESSING_ROOT", root):
                main.write_provider_profiles([{"id": "gemini", "name": "Gemini", "type": "gemini", "credential_ref": "gemini", "default_model": "gemini-flash-latest", "enabled": True, "capabilities": {}}])
                profiles = main.read_provider_profiles()
                public = main.public_provider_profile(profiles[0])
            self.assertTrue(public["credential_configured"])
            self.assertNotIn("api_key", public)


if __name__ == "__main__":
    unittest.main()
