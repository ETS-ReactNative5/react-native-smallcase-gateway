package com.smallcase.gateway.reactnative

import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import android.widget.Toast
import com.facebook.react.bridge.*
import com.smallcase.gateway.data.SmallcaseGatewayListeners
import com.smallcase.gateway.data.SmallcaseLogoutListener
import com.smallcase.gateway.data.listeners.DataListener
import com.smallcase.gateway.data.listeners.SmallPlugResponseListener
import com.smallcase.gateway.data.listeners.TransactionResponseListener
import com.smallcase.gateway.data.models.*
import com.smallcase.gateway.data.requests.InitRequest
import com.smallcase.gateway.portal.SmallcaseGatewaySdk
import com.smallcase.gateway.data.listeners.LeadGenResponseListener


class SmallcaseGatewayModule(reactContext: ReactApplicationContext?) : ReactContextBaseJavaModule(reactContext!!) {
    companion object {
        const val TAG = "SmallcaseGatewayModule"
    }

    override fun getName(): String {
        return "SmallcaseGateway"
    }

    @ReactMethod
    fun setConfigEnvironment(
            envName: String,
            gateway: String,
            isLeprechaunActive: Boolean,
            isAmoEnabled: Boolean,
            preProvidedBrokers: ReadableArray,
            promise: Promise) {

        try {
            val brokerList = ArrayList<String>()
            for (index in 0 until preProvidedBrokers.size()) {
                val broker = preProvidedBrokers.getString(index)
                if (broker != null) {
                    brokerList.add(broker)
                }
            }

            val protocol = getProtocol(envName)

            val env = Environment(
                    gateway = gateway,
                    buildType = protocol,
                    isAmoEnabled = isAmoEnabled,
                    preProvidedBrokers = brokerList,
                    isLeprachaunActive = isLeprechaunActive
            )

            SmallcaseGatewaySdk.setConfigEnvironment(
                    environment = env,
                    smallcaseGatewayListeners = object : SmallcaseGatewayListeners {
                        override fun onGatewaySetupSuccessfull() {
                            promise.resolve(true)
                        }

                        override fun onGatewaySetupFailed(error: String) {
                            promise.reject(Throwable(error))
                        }
                    })
        } catch (e: Exception) {
            promise.reject(e)
        }
    }

    @ReactMethod
    fun setHybridSdkVersion(sdkVersion: String) {
        SmallcaseGatewaySdk.setSDKType("react-native")
        SmallcaseGatewaySdk.setHybridSDKVersion(sdkVersion)
    }

    @ReactMethod
    fun getSdkVersion(reactNativeSdkVersion: String, promise: Promise) {
        val sdkString = "android:${SmallcaseGatewaySdk.getSdkVersion()},react-native:$reactNativeSdkVersion"
        promise.resolve(sdkString)
    }

    @ReactMethod
    fun init(sdkToken: String, promise: Promise) {
        Log.d(TAG, "init: start")

        val initReq = InitRequest(sdkToken)
        SmallcaseGatewaySdk.init(
                authRequest = initReq,
                gatewayInitialisationListener = object : DataListener<InitialisationResponse> {
                    override fun onFailure(errorCode: Int, errorMessage: String) {
                        val err = createErrorJSON(errorCode, errorMessage, null)
                        promise.reject("error", err)
                    }

                    override fun onSuccess(response: InitialisationResponse) {
                        promise.resolve(true)
                    }

                })
    }

    @ReactMethod
    fun triggerTransaction(transactionId: String, utmParams: ReadableMap?, brokerList: ReadableArray?, promise: Promise) {
        Log.d(TAG, "triggerTransaction: start")

        var safeBrokerList = listOf<String>()

        if (brokerList != null) {
            safeBrokerList = brokerList.toArrayList().map { it as String }
        }


        val activity = currentActivity;
        if (activity != null) {
            val utm = readableMapToStrHashMap(utmParams)
            SmallcaseGatewaySdk.triggerTransaction(
                    utmParams = utm,
                    activity = activity,
                    transactionId = transactionId,
                    preProvidedBrokers = safeBrokerList,
                    transactionResponseListener = object : TransactionResponseListener {
                        override fun onSuccess(transactionResult: TransactionResult) {

                            val res = resultToWritableMap(transactionResult)
                            promise.resolve(res)


                        }

                        override fun onError(errorCode: Int, errorMessage: String, data: String?) {
                            val err = createErrorJSON(errorCode, errorMessage, data)
                            promise.reject("error", err)
                        }
                    })
        } else {
            promise.reject(Throwable("no activity"))
        }
    }

    @ReactMethod
    fun showOrders(promise: Promise) {
        val activity = currentActivity;
        if (activity != null) {
            SmallcaseGatewaySdk.showOrders(
                activity = activity,
                showOrdersResponseListener = object : DataListener<Any> {
                    override fun onSuccess(response: Any) {
                        promise.resolve(true)
                    }

                    override fun onFailure(errorCode: Int, errorMessage: String) {
                        val err = createErrorJSON(errorCode, errorMessage, null)
                        promise.reject("error", err)
                    }
                }
            )
        }
    }

    @ReactMethod
    fun launchSmallplug(targetEndpoint: String, params: String, promise: Promise) {
        Log.d(TAG, "launchSmallplug: start")

        SmallcaseGatewaySdk.launchSmallPlug(currentActivity!!, SmallplugData(targetEndpoint, params), object : SmallPlugResponseListener {
            override fun onFailure(errorCode: Int, errorMessage: String) {
                val err = createErrorJSON(errorCode, errorMessage, null)

                promise.reject("error", err)
            }

            override fun onSuccess(smallPlugResult: SmallPlugResult) {
                val res = resultToWritableMap(smallPlugResult)
                promise.resolve(res)
            }

        })
    }

    @ReactMethod
    fun archiveSmallcase(iscid: String, promise: Promise) {
        Log.d(TAG, "markSmallcaseArchive: start")

        SmallcaseGatewaySdk.markSmallcaseArchived(iscid, object : DataListener<SmallcaseGatewayDataResponse> {

            override fun onSuccess(response: SmallcaseGatewayDataResponse) {
                promise.resolve(response)
            }

            override fun onFailure(errorCode: Int, errorMessage: String) {
                val err = createErrorJSON(errorCode, errorMessage, null)
                promise.reject("error", err)
            }
        })
    }

    @ReactMethod
    fun logoutUser(promise: Promise) {
        val activity = currentActivity;
        if (activity != null) {
            SmallcaseGatewaySdk.logoutUser(
                    activity = activity,
                    logoutListener = object : SmallcaseLogoutListener {
                        override fun onLogoutSuccessfull() {
                            promise.resolve(true)
                        }

                        override fun onLogoutFailed(errorCode: Int, error: String) {
                            val err = createErrorJSON(errorCode, error, null)
                            promise.reject("error", err)
                        }
                    })
        }
    }

    @ReactMethod
    fun triggerLeadGen(userDetails: ReadableMap, utmData: ReadableMap) {
        val activity = currentActivity;
        if (activity != null) {
            SmallcaseGatewaySdk.triggerLeadGen(
                    activity=activity,
                    utmParams = readableMapToStrHashMap(utmData),
                    params = readableMapToStrHashMap(userDetails)
            )
        }
    }

    @ReactMethod
    fun triggerLeadGenWithStatus(userDetails: ReadableMap, promise: Promise) {
        val activity = currentActivity
        if (activity != null) {

            SmallcaseGatewaySdk.triggerLeadGen(activity, readableMapToStrHashMap(userDetails), object: LeadGenResponseListener {
                override fun onSuccess(leadResponse: String) {
                    promise.resolve(leadResponse)
                }
            })
        }
    }

    private fun getProtocol(envName: String): Environment.PROTOCOL {
        return when (envName) {
            "production" -> Environment.PROTOCOL.PRODUCTION
            "development" -> Environment.PROTOCOL.DEVELOPMENT
            "staging" -> Environment.PROTOCOL.STAGING
            else -> Environment.PROTOCOL.PRODUCTION
        }
    }

    private fun readableMapToStrHashMap(params: ReadableMap?): HashMap<String, String> {
        val data = HashMap<String, String>()

        if (params != null) {
            val keyIterator = params.keySetIterator()

            while (keyIterator.hasNextKey()) {
                val key = keyIterator.nextKey()
                params.getString(key)?.let {
                    data.put(key, it)
                }
            }
        }

        return data
    }

    private fun resultToWritableMap(result: TransactionResult): WritableMap {
        val writableMap: WritableMap = Arguments.createMap()

        writableMap.putString("data", result.data)
        writableMap.putString("transaction", result.transaction.name)
        return writableMap
    }


    private fun resultToWritableMap(result: SmallPlugResult): WritableMap {
        val writableMap: WritableMap = Arguments.createMap()

        writableMap.putBoolean("success", result.success)
        writableMap.putString("smallcaseAuthToken", result.smallcaseAuthToken)

        return writableMap
    }

    private fun createErrorJSON(errorCode: Int?, errorMessage: String?, data: String?): WritableMap {
        val errObj = Arguments.createMap()

        errorCode?.let { errObj.putInt("errorCode", it) }
        errorMessage?.let { errObj.putString("errorMessage", it) }
        data?.let { errObj.putString("data", it) }

        return errObj
    }
}