## React Native Reverse Proxy

An embedded simple reverse proxy for React Native,

also ships a tiny authorization.

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
await RNReverseProxy.start({ key, origin, port })

await fetch("http://localhost:5000?msg=greeting")
// Forwarded to https://example.org?msg=greeting

await RNReverseProxy.stop({ key })
```

### Start a reverse proxy with simple authorization

If you feel unsafe to accept all incoming requests to your private domain,
even though there have seldom requests establish from other apps to your app.

Could add custom http headers to challenge for them.

```ts
const key = String(new Date().getTime())
const origin = "https://example.org"
const port = 50000
const xHeaders = { "x-rnrp-authorization": String(new Date().getTime()) }
await RNReverseProxy.start({ key, origin, port })

await fetch("http://localhost:5000")
// Proxy Unauthorized

await fetch("http://localhost:5000", { headers: xHeaders })
// Forwarded to https://example.org

await RNReverseProxy.stop({ key })
```
