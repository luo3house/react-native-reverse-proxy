## React Native Reverse Proxy

An embedded simple reverse proxy for React Native,

also ships a tiny function for authorization.

It uses **Nanohttpd** for Android & **FlyingFox** for iOS as underlying server library.

### Installation

```bash
npm i react-native-reverse-proxy
```

### Linking

Ask React Native to do Autolinking for you.

### Start a reverse proxy on port 50000

```ts
import RNReverseProxy from "react-native-reverse-proxy"

const key = String(new Date().getTime())
const origin = "https://example.org"
const port = 50000

// Start server
await RNReverseProxy.start({ key, origin, port })

// Stop server
await RNReverseProxy.stop({ key })
```

Once server starts, it is able to override the origin in url of incoming requests.

```ts
fetch("http://localhost:50000")
// Will got the same response as from https://example.org

fetch("http://localhost:50000?msg=hi")
// https://example.org?msg=hi
```

### Start a reverse proxy with simple authorization

If you feel unsafe to accept all incoming requests to your private domain,
even though there have seldom requests establish from other apps to your app.

Could add custom http headers to challenge for them.

```ts
const key = String(new Date().getTime())
const origin = "https://example.org"
const port = 50000
const xHeaders = { "x-rnrp-authorization": "myauth" }
await RNReverseProxy.start({ key, origin, port })
```

By configuring http headers starts with "x-rnrp-" so that server validates via equivalent one by one, or rejects on any fails.

```ts
await fetch("http://localhost:5000")
// Proxy Unauthorized

await fetch("http://localhost:5000", { headers: xHeaders })
// Forwarded to https://example.org
```
