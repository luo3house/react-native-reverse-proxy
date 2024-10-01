import { NativeModules, Platform } from 'react-native'

const LINKING_ERROR =
  `The package 'react-native-reverse-proxy' doesn't seem to be linked. Make sure: \n\n` +
  Platform.select({ ios: "- You have run 'pod install'\n", default: '' }) +
  '- You rebuilt the app after installing the package\n' +
  '- You are not using Expo Go\n'

const ReverseProxy = NativeModules.ReverseProxy
  ? NativeModules.ReverseProxy
  : new Proxy(
      {},
      {
        get() {
          throw new Error(LINKING_ERROR)
        },
      }
    )

/**
 * Start a reverse proxy with options
 * @param req
 * @returns noop
 */
function start(req: RNRPStartReq): Promise<void> {
  return ReverseProxy.start(req)
}

function stop(req: RNRPStopReq): Promise<void> {
  return ReverseProxy.stop(req)
}

export type RNRPHeaders = {
  /**
   * Other options which key starts with "x-rnrp" in lowercase,
   * are collected as proxy authorization to challenge for income requests
   * before forwarding to target origin
   */
  'x-rnrp-authorization'?: string
  [otherXRnRpHeaders: string]: string | number | undefined
}

export type RNRPStartReq = Partial<RNRPHeaders> & {
  /** The unique literal key to recognize your proxy from map */
  key: string
  /** The port of proxy server */
  port: number
  /** The target origin to override */
  origin: string
}

export type RNRPStopReq = {
  /** The unique literal key to restore your proxy from map */
  key: string
}

export const RNReverseProxy = { start, stop }
export default RNReverseProxy
