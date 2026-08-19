# Android Gateway and Usage Integration

The Android client keeps local Room usage and remote Gateway usage as two separate sources. Local `AiUsageAggregate` rows describe work executed by the Android pipeline. Remote `GatewayUsageSummary` rows describe work executed by the Python Gateway. The Dashboard renders them separately so the same request is never counted twice.

The Android client can read `/v1/ai/providers`, `/v1/ai/usage`, and `/v1/processing/capabilities` through `ProcessingGatewayClient`. Remote video processing now creates a Project through `/api/v1/projects`, starts a linked Job through `/api/v1/projects/{id}/process`, and observes the real job status. Cancellation remains a real Gateway operation and is not represented as local success.

The Registry models contain no API keys. Gateway responses expose only whether a credential is configured, provider/model metadata, capabilities, and optional pricing. Missing provider usage remains marked as estimated by the Gateway and is displayed separately in the Android Dashboard.
