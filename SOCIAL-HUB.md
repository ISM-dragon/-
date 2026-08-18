# Social Hub API contract

`Social Hub` is the in-app control surface for account connections, copy preparation, approval, and scheduling. It delegates OAuth, token storage, media upload, platform-specific publishing, and retry handling to a user-controlled HTTPS Gateway API.

The Gateway must be configured in the Social Hub screen. The Android app sends only the post metadata and a public media URL; it must not receive or store platform app secrets. The Gateway should store refresh tokens encrypted at rest and should expose HTTPS endpoints only.

## Endpoints

### Start OAuth

```http
GET /v1/social/oauth/{platform}/start
Authorization: Bearer <session-token>
```

`platform` is one of `instagram`, `facebook`, `tiktok`, `youtube`, or `x`.

Response:

```json
{ "url": "https://provider.example/oauth/authorize?..." }
```

The Gateway owns the OAuth callback and must validate the `state` value before storing the resulting account connection.

### Schedule a post

```http
POST /v1/social/schedule
Authorization: Bearer <session-token>
Content-Type: application/json
```

Example payload:

```json
{
  "id": "local-post-id",
  "platform": "youtube",
  "account": "channel-name",
  "mediaUrl": "https://cdn.example.com/clip.mp4",
  "title": "Searchable clip title",
  "caption": "Short hook and call to action",
  "description": "Long-form description",
  "hashtags": "#shorts #creator",
  "keywords": "podcast, clips, advice",
  "scheduledAt": "2026-08-18T19:30:00Z",
  "status": "scheduled"
}
```

Response:

```json
{ "id": "gateway-job-id", "status": "scheduled" }
```

### Publish or approve a post

```http
POST /v1/social/publish
Authorization: Bearer <session-token>
Content-Type: application/json
```

The payload has the same shape as the schedule request. The Gateway should upload the media, call the selected platform's official API, persist the platform post ID, and return a normalized status:

```json
{
  "id": "gateway-job-id",
  "status": "published",
  "platformPostId": "provider-post-id",
  "permalink": "https://provider.example/post/123"
}
```

## Provider responsibilities

The Gateway is responsible for platform-specific OAuth scopes, refresh-token rotation, resumable uploads, rate limits, status polling, and provider errors. Instagram and Facebook accounts must satisfy Meta's professional-account/Page requirements. TikTok may require Content Posting API approval. YouTube may require Google OAuth verification before public uploads. X video publishing uses chunked media upload followed by post creation.

## Security requirements

The APK must never contain Meta app secrets, TikTok client secrets, Google client secrets, X client secrets, or platform refresh tokens. Use short-lived session tokens for the Gateway, rotate them, validate HTTPS certificates, rate-limit the endpoints, and require explicit user approval when the account or content policy requires it.

## Update or cancel a scheduled post

The calendar uses the local post ID as the stable identifier. A Gateway that supports server-side scheduling should implement the following endpoints:

```http
PATCH /v1/social/schedule/{post-id}
Authorization: Bearer <session-token>
Content-Type: application/json
```

The request body contains the full post payload with the new `scheduledAt`, content, account, or platform. The Gateway should return the normalized scheduled record.

```http
DELETE /v1/social/schedule/{post-id}
Authorization: Bearer <session-token>
```

The Gateway should cancel a queued provider job and return `{ "id": "gateway-job-id", "status": "cancelled" }`. The app keeps a local copy of the calendar for offline review; when no Gateway is configured, edits and cancellations remain local and are not published remotely.

## Automatic publishing

The composer now includes `autoPublish`. When it is enabled, the app submits a scheduled record to the Gateway and monitors due local records while the Social Hub is open. A due record is sent to `POST /v1/social/publish`, and the UI changes it to `published` or `failed`.

For reliable publishing while the Android app is closed or the device is offline, the Gateway must treat `POST /v1/social/schedule` as a durable server-side job. The Android timer is a foreground convenience and recovery mechanism, not a replacement for a persistent backend worker. The Gateway should claim jobs idempotently, refresh OAuth tokens, retry transient provider errors, and report the normalized final state through a future sync/status endpoint.
