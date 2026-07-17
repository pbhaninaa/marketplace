package com.servicehub

import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule

class ServiceHubConfigModule(reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

  override fun getName(): String = "ServiceHubConfig"

  override fun getConstants(): Map<String, Any> =
      mapOf("appEnv" to BuildConfig.SERVICEHUB_ENV)
}
