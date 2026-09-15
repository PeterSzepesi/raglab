# Microsoft AI Foundry authentication

**Status:** API key for local development; managed identity implemented and required before cloud exposure

## Local API-key mode

Keep `AZURE_AI_FOUNDRY_API_KEY` in a Windows user environment variable or an untracked `.env` consumed by your own launcher. Never put it in YAML, source, frontend storage, screenshots, benchmark output, or GitHub workflow text. The runtime-config API reveals only `credentialConfigured: true|false`.

```powershell
[Environment]::SetEnvironmentVariable("AZURE_AI_FOUNDRY_API_KEY", "<rotated-key>", "User")
```

Open a new terminal afterward. The default `api-key` mode adds an `api-key` header only on the backend.

## Local Entra developer credential

The same implementation supports Azure CLI/IDE credentials through `DefaultAzureCredential`:

```powershell
$env:AZURE_AI_FOUNDRY_AUTHENTICATION_MODE = "entra"
$env:AZURE_AI_FOUNDRY_TOKEN_SCOPE = "https://ai.azure.com/.default"
az login
```

Grant the signed-in identity the minimum model-inference role on the Foundry resource. The token scope is configurable because the resource's generated Code sample is authoritative if its endpoint family requires a different scope.

## Hosted managed identity

1. Deploy the container with a system-assigned or user-assigned managed identity.
2. Grant only the required Foundry model-inference role at the smallest resource scope.
3. Set `AZURE_AI_FOUNDRY_AUTHENTICATION_MODE=managed-identity` and do not inject an API key.
4. For a user-assigned identity, set the standard Azure client-id environment setting supported by `DefaultAzureCredential`.
5. Validate `/api/v1/capabilities` reports managed-identity and credential configured, then make one deliberate model request. Health polling itself never spends tokens.

The credential chain and bearer-token acquisition live behind `FoundryAuthentication`; model and conversation code cannot read secret values.
