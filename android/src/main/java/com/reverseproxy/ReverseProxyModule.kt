package com.reverseproxy

import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.modules.network.OkHttpClientProvider
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.Response.Status
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.DataInputStream

class ReverseProxyModule(reactContext: ReactApplicationContext) :
  ReactContextBaseJavaModule(reactContext) {

  override fun getName(): String {
    return NAME
  }

  val pool = mutableMapOf<String, ServerInstance>()

  // Example method
  // See https://reactnative.dev/docs/native-modules-android
  @ReactMethod
  fun start(req: ReadableMap, promise: Promise) {
    val key = req.getString("key")!!
    val port = req.getInt("port")
    val origin = req.getString("origin")!!
    val xHeaders = req.keySetIterator().let {
      mutableMapOf<String, String>().also { m ->
        while (it.hasNextKey()) {
          val k = it.nextKey()
          if (k.startsWith("x-rnrp-")) {
            req.getString(k)?.let { v -> m.put(k, v) }
          }
        }
      }
    }
    synchronized(pool) {
      var svc = pool.get(key)
      if (svc == null) pool.put(key, ServerInstance(port, origin, xHeaders).also { svc = it })
      try {
        svc!!.start()
        promise.resolve(port)
      } catch (e: Throwable) {
        e.printStackTrace()
        promise.reject(e)
      }
    }
  }

  @ReactMethod
  fun stop(req: ReadableMap, promise: Promise) {
    val key = req.getString("key")!!
    synchronized(pool) {
      val svc = pool.get(key)
      pool.remove(key)
      try {
        svc?.stop()
        promise.resolve(0)
      } catch (e: Throwable) {
        e.printStackTrace()
        promise.reject(e)
      }
    }
  }

  companion object {
    const val NAME = "ReverseProxy"
  }
}

open class ServerInstance(
  val port: Int,
  val origin: String,
  val xHeaders: Map<String, String>
) {
  private var server: NanoHTTPD? = null

  fun start(): Int {
    synchronized(this) {
      assert(server == null)
      val port = port.also {
      server = object : NanoHTTPD(it) {
          init { start(10_000, false) }
          override fun serve(req: IHTTPSession): Response {
            val reqContentType = req.headers["content-type"]
            val reqHeaders = req.headers
            for ((k, v) in xHeaders) {
              if (reqHeaders[k] != v) {
                return newFixedLengthResponse(Status.UNAUTHORIZED, MIME_PLAINTEXT, "Proxy Unauthorized")
              }
            }

            val fwReq = Request.Builder().let {
              for ((k, v) in reqHeaders) { it.addHeader(k, v) }
              it.removeHeader("host")
              it.method(req.method.name, req.inputStream.let {
                val bytes = ByteArray(it.available())
                val dataInputStream = DataInputStream(it)
                dataInputStream.readFully(bytes)
                if (bytes.isNotEmpty()) bytes.toRequestBody(reqContentType?.toMediaTypeOrNull()) else null
              })
              it.url(req.uri.let { path ->
                val search: String? = req.queryParameterString
                origin + path + run {
                  if (search?.isNotEmpty() == true) "?" + search else ""
                }
              })
            }
            return OkHttpClientProvider.createClient().newCall(fwReq.build()).execute().let {
              val status = Status.lookup(it.code)
              val body = it.body
              if (body != null) {
                newChunkedResponse(
                  status,
                  it.header("content-type"),
                  body.byteStream(),
                )
              } else {
                newFixedLengthResponse(
                  status,
                  it.header("content-type"),
                  ""
                )
              }
            }
          }
        }
      }
      return port
    }
  }

  fun stop() {
    synchronized(this) {
      server?.stop()
      server = null
    }
  }
}
