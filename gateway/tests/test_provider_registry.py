import json
import sqlite3
import tempfile
import unittest
from pathlib import Path

from gateway.provider_registry import (
    ModelDefinition,
    ProviderDefinition,
    get_model,
    get_provider,
    init_registry_schema,
    list_models,
    list_providers,
    register_provider,
    set_provider_enabled,
)
from gateway.secret_vault import SecretVault


class ProviderRegistryTests(unittest.TestCase):
    def setUp(self):
        self.connection = sqlite3.connect(":memory:")
        self.connection.row_factory = sqlite3.Row
        init_registry_schema(self.connection)

    def tearDown(self):
        self.connection.close()

    def test_built_ins_are_registered_without_secrets(self):
        self.assertEqual({provider.id for provider in list_providers(self.connection)}, {"gemini", "openai", "anthropic", "openrouter", "ollama"})

    def test_duplicate_ids_are_rejected(self):
        provider = ProviderDefinition("custom", "Custom", "openai_compatible", True, "https://example.test/v1", "CUSTOM_KEY")
        register_provider(self.connection, provider)
        with self.assertRaises(ValueError):
            register_provider(self.connection, provider)

    def test_enable_disable(self):
        provider = ProviderDefinition("custom", "Custom", "openai_compatible", True, "https://example.test/v1", "CUSTOM_KEY")
        register_provider(self.connection, provider)
        self.assertFalse(set_provider_enabled(self.connection, "custom", False).enabled)
        self.assertTrue(set_provider_enabled(self.connection, "custom", True).enabled)

    def test_custom_model_lookup_and_capability(self):
        provider = ProviderDefinition(
            "custom", "Custom", "openai_compatible", True, "https://example.test/v1", "CUSTOM_KEY",
            ("json", "vision"),
            (ModelDefinition("custom:vision", "custom", "vision-model", "Vision Model", ("json", "vision"), supports_structured_output=True, supports_vision=True),),
        )
        register_provider(self.connection, provider)
        model = get_model(self.connection, "vision-model")
        self.assertIsNotNone(model)
        self.assertTrue(model.supports_vision)
        self.assertIn("vision-model", [item.model_id for item in list_models(self.connection, "custom")])

    def test_missing_provider_and_model(self):
        self.assertIsNone(get_provider(self.connection, "missing"))
        self.assertIsNone(get_model(self.connection, "missing"))


class SecretVaultTests(unittest.TestCase):
    def test_store_retrieve_delete_and_names_without_plaintext_metadata(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "secrets.json"
            vault = SecretVault(path)
            vault.set("TEST_VAULT_KEY", "private-value")
            self.assertEqual(vault.get("TEST_VAULT_KEY"), "private-value")
            self.assertIn("TEST_VAULT_KEY", vault.list_names())
            self.assertNotIn("private-value", json.dumps(vault.list_names()))
            self.assertEqual(path.stat().st_mode & 0o777, 0o600)
            vault.delete("TEST_VAULT_KEY")
            self.assertFalse(vault.has("TEST_VAULT_KEY"))


if __name__ == "__main__":
    unittest.main()
