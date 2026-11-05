# SETEC ŠIFRI — AI Istraži (optional server)

The Android APK does not contain an OpenAI API key. It connects through an HTTPS proxy controlled by the server administrator. The proxy uses OpenAI Responses API with web search to return up to 5 important specs plus source links. The app sends the product model only, not the camera photo.

## One-time setup by an adult / server administrator

1. Deploy the Cloudflare Worker in this folder (using Wrangler from the ai-backend directory).
2. In the Worker secret settings create OPENAI_API_KEY: a server-owned OpenAI API key. Never paste it into the APK, chat or GitHub.
3. Create APP_CLIENT_TOKEN: a separate private, random token of at least 32 unpredictable characters. Never use the OpenAI API key as this token.
4. Configure spending limits, request rate limits and monitoring. Web search may incur extra API charges.
5. Deploy and check the public Worker HTTPS address at /health.
6. Open SETEC ŠIFRI → Skeniraj model → ⚙ AI and enter the HTTPS Worker URL and separate APP_CLIENT_TOKEN. Once the exact product is displayed, press AI Istraži.

## API

GET /health returns server configuration status; it never returns secrets.
POST /research requires Content-Type application/json and Authorization Bearer APP_CLIENT_TOKEN. JSON body: {"model":"ACER Aspire Go 15 (AG15-42P-R844)"}. It returns {"model":"...", "summary":"...", "sources":[{"title":"...", "url":"https://..."}], "verified":true/false, "note":"..."}.

The AI response may omit attributes when reliable exact-model information cannot be found. Always verify details using the supplied source links. If there are no links, the app marks the result as unverified.

## Security and limits

This single-token Worker is a private prototype, not a production multi-user billing/authentication system. Add per-user authentication, server-side rate limiting and monitoring before public distribution. Secrets are server-side only; the client access token is stored in Android private app preferences. Rotate it if exposed.

OpenAI API usage and ChatGPT subscriptions are billed separately. The repository has no live AI server and no configured OpenAI account; an authorized adult/server administrator must deploy the backend. The APK's normal SETEC features work without it; pressing the AI button opens setup.