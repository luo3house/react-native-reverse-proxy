import Darwin
import Foundation
import FlyingFox

@objc(ReverseProxy)
class ReverseProxy: NSObject {
    
    var pool: [String: ServerInstance] = Dictionary()
    
    @objc(start:withResolver:withRejecter:)
    func start(_ req: NSDictionary,resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) -> Void {
        let key = (req.object(forKey: "key") as! NSString) as String
        let port = (req.object(forKey: "port") as! NSNumber).intValue
        let origin = (req.object(forKey: "origin") as! NSString) as String
        var xHeaders: [String: String] = Dictionary()
        for (anyK, anyV) in req {
            let k = String(describing: anyK)
            if (k.starts(with: "x-rnrp-")) {
                xHeaders[k] = String(describing: anyV)
            }
        }
        var svc = pool[key]
        if (svc == nil) {
            svc = ServerInstance(port: port, origin: origin, xHeaders: xHeaders)
            pool[key] = svc
        }
        Task {
            do {
                try await svc!.start()
                resolve(nil)
            } catch let err {
                pool.removeValue(forKey: key)
                reject("1", err.localizedDescription, err)
            }
        }
    }
    
    @objc(stop:withResolver:withRejecter:)
    func stop(req: NSDictionary,resolve: @escaping RCTPromiseResolveBlock,reject: @escaping RCTPromiseRejectBlock) -> Void {
        let key = (req.object(forKey: "key") as! NSString) as String
        let svc = pool[key]
        Task {
            await svc?.stop()
            pool.removeValue(forKey: key)
            resolve(nil)
        }
    }
}

class ServerInstance {
    let port: Int;
    let origin: String;
    let xHeaders: [String: String];
    var server: HTTPServer?;
    var task: Task<Any, Error>?;
    
    init(port: Int, origin: String, xHeaders: [String: String]) {
        self.port = port
        self.origin = origin
        self.xHeaders = xHeaders
    }
    
    func start() async throws {
        if (self.server != nil || self.task != nil) {
            throw NSError(domain: "server is running", code: 1)
        }
        let server = HTTPServer(port: UInt16(port), handler: RNRPHandler(
            origin: self.origin,
            xHeaders: self.xHeaders
        ))
        self.server = server
        do {
            self.task = Task { try await server.start() }
            try await server.waitUntilListening()
        } catch let err {
            self.server = nil
            self.task = nil
            throw err
        }
    }
    
    func stop() async {
        await self.server?.stop(timeout: 1)
        self.task?.cancel()
        self.server = nil
        self.task = nil
    }
}

final class RNRPHandler: NSObject, Sendable, HTTPHandler, URLSessionDelegate {
    let origin: String
    let xHeaders: [String: String]
    init(origin: String, xHeaders: [String: String]) {
        self.origin = origin
        self.xHeaders = xHeaders
    }
    func handleRequest(_ request: HTTPRequest) async throws -> HTTPResponse {
        let reqHeaders = request.headers
        for (k, v) in xHeaders {
            if (reqHeaders[HTTPHeader(k)] != v) {
                return HTTPResponse(
                    statusCode: HTTPStatusCode.unauthorized,
                    body: "Proxy Unauthorized".data(using: .utf8)!
                )
            }
        }
        return try await ProxyHTTPHandler(base: origin).handleRequest(request)
    }
}
