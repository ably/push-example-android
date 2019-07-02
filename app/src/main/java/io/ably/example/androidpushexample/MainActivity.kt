package io.ably.example.androidpushexample

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.firebase.iid.FirebaseInstanceId
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import io.ably.lib.realtime.*
import io.ably.lib.rest.AblyRest
import io.ably.lib.rest.Auth
import io.ably.lib.rest.Auth.TokenCallback
import io.ably.lib.types.*
import java.util.*

const val TAG = "androidpushexample"
const val CLIENT_ID_SUBSCRIPTION: Int = 1
const val DEVICE_ID_SUBSCRIPTION: Int = 2

class MainActivity : AppCompatActivity() {
	/**
	 * API Key and Environment values are read from local.properties. Check build.gradle for more
	 * details
	 */
	val apiKey = BuildConfig.ABLY_KEY
	val environment = BuildConfig.ABLY_ENV

	var runId = ""
	var channelName = ""
	var subscriptionType = 0

	lateinit var ablyForDevice: AblyRealtime
	lateinit var ablyForAdmin: AblyRest
	lateinit var textView: TextView
	lateinit var logger: TextViewLogger
	lateinit var pushMessageReceiver: PushReceiver
	lateinit var pushActivateReceiver: ActivateReceiver

	inner class PushReceiver : BroadcastReceiver() {
		private val lock:Object = Object()
		private var action:String = ""
		private var runId:String = ""
		override fun onReceive(context: Context?, intent: Intent?) {
			val pushData :HashMap<String, String> = intent!!.getSerializableExtra("data") as HashMap<String, String>
			synchronized(lock) {
				this.action = intent!!.action
				this.runId = pushData.get("runId")!!
				lock.notifyAll()
			}
			onPushMessageReceived(this.action, pushData)
		}

		fun waitFor(action:String, runId:String) = synchronized(lock) {
			while(action != this.action || runId != this.runId) {
				lock.wait()
			}
		}
	}

	inner class ActivateReceiver : BroadcastReceiver() {
		private val lock:Object = Object()
		private var action:String = ""
		private var hasError:Boolean = false
		private var errorMessage:String = ""
		override fun onReceive(context: Context?, intent: Intent?) {
			var action:String = intent!!.action
			val hasError:Boolean = intent!!.getBooleanExtra("hasError", false)
			synchronized(lock) {
				this.action = action
				this.hasError = hasError
				if(hasError) {
					this.errorMessage = intent!!.getStringExtra("error.message")
				}
				lock.notifyAll()
			}
		}

		fun waitFor(action:String) = synchronized(lock) {
			while(action != this.action) {
				lock.wait()
			}
		}
	}

	fun generateRunId():String {
		runId = java.util.UUID.randomUUID().toString()
		channelName = channelName(runId)
		return runId
	}

	fun channelName(runId:String):String {
		return "push:test_push_channel_${runId}"
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		Log.i(TAG, "MainActvity onCreate")
		setContentView(R.layout.activity_main)
		var toolbar = findViewById(R.id.toolbar) as Toolbar
		setSupportActionBar(toolbar)
		textView = findViewById(R.id.editText1) as TextView
		logger = TextViewLogger(textView);

		generateRunId()
		registerReceivers()
		initAbly()
	}

	override fun onDestroy() {
		LocalBroadcastManager.getInstance(this).unregisterReceiver(pushMessageReceiver)
		LocalBroadcastManager.getInstance(this).unregisterReceiver(pushActivateReceiver)
		closeAbly()
		super.onDestroy()
	}

	fun registerReceivers() {
		pushMessageReceiver = PushReceiver()
		val messageIntentFilter = IntentFilter()
		messageIntentFilter.addAction(AblyPushMessagingService.PUSH_DATA_ACTION)
		messageIntentFilter.addAction(AblyPushMessagingService.PUSH_NOTIFICATION_ACTION)
		LocalBroadcastManager.getInstance(this).registerReceiver(pushMessageReceiver, messageIntentFilter)

		pushActivateReceiver = ActivateReceiver()
		val activateIntentFilter = IntentFilter()
		activateIntentFilter.addAction("io.ably.broadcast.PUSH_ACTIVATE")
		activateIntentFilter.addAction("io.ably.broadcast.PUSH_DEACTIVATE")
		LocalBroadcastManager.getInstance(this).registerReceiver(pushActivateReceiver, activateIntentFilter)
	}

	fun runPushTests():Boolean {
		logger.i("runPushTests()", "Running push tests")
		object: Thread(){
			override fun run() {

				/* prepare */
				resetLocalDevice()
				resetActivationState()
				activatePush(true)

				/* direct publish tests */
				val testDirectRunId = generateRunId()
				logger.i("runPushTests()", "Direct runId = ${testDirectRunId}")
				pushDirectDataTest(testDirectRunId)
				pushDirectNotificationTest(testDirectRunId)

				/* channel push tests */
				val testChannelRunId = generateRunId()
				val testChannelName = channelName(testChannelRunId)
				logger.i("runPushTests()", "Channel runId = ${testChannelRunId}")
				realtimeSubscribe(channelName(testChannelRunId), true)
				pushSubscribe(testChannelName, true)
				pushPublishDataTest(testChannelRunId)
				pushPublishNotificationTest(testChannelRunId)
				pushUnsubscribe(testChannelName, true)

				logger.i("runPushTests()", "Finished")
			}
		}.start()
		return true
	}

	fun pushDirectDataTest(testRunId:String) {
		logger.i("pushDirectDataTest()", "${testRunId}")
		pushDirectData(testRunId, true)
		logger.i("pushDirectDataTest()", "waiting for notification: ${testRunId}")
		pushMessageReceiver.waitFor(AblyPushMessagingService.PUSH_DATA_ACTION, testRunId)
		logger.i("pushDirectDataTest()", "notification received: ${testRunId}")
	}

	fun pushDirectNotificationTest(testRunId:String) {
		logger.i("pushDirectNotificationTest()", "${testRunId}")
		pushDirectNotification(testRunId, true)
		logger.i("pushDirectNotificationTest()", "waiting for notification: ${testRunId}")
		pushMessageReceiver.waitFor(AblyPushMessagingService.PUSH_NOTIFICATION_ACTION, testRunId)
		logger.i("pushDirectNotificationTest()", "notification received: ${testRunId}")
	}

	fun pushPublishDataTest(testRunId:String) {
		logger.i("pushPublishDataTest()", "${testRunId}")
		pushPublishData(testRunId, true)
		logger.i("pushDirectDataTest()", "waiting for notification: ${testRunId}")
		pushMessageReceiver.waitFor(AblyPushMessagingService.PUSH_DATA_ACTION, testRunId)
		logger.i("pushDirectDataTest()", "notification received: ${testRunId}")
	}

	fun pushPublishNotificationTest(testRunId:String) {
		logger.i("pushPublishNotificationTest()", "${testRunId}")
		pushPublishNotification(testRunId, true)
		logger.i("pushDirectNotificationTest()", "waiting for notification: ${testRunId}")
		pushMessageReceiver.waitFor(AblyPushMessagingService.PUSH_NOTIFICATION_ACTION, testRunId)
		logger.i("pushDirectNotificationTest()", "notification received: ${testRunId}")
	}

	/**
	 * Initialise the Ably library and connect
	 */
	fun initAbly():Boolean {
		val adminOptions = ClientOptions(apiKey)
		adminOptions.environment = environment
		adminOptions.logLevel = io.ably.lib.util.Log.VERBOSE
		ablyForAdmin = AblyRest(adminOptions)

		val deviceOptions = ClientOptions()
		deviceOptions.environment = environment
		deviceOptions.logLevel = io.ably.lib.util.Log.VERBOSE

		/* ensure that the device client has push-subscribe */
		deviceOptions.authCallback = object: TokenCallback {
            override fun getTokenRequest(params: Auth.TokenParams?): Any {
				val deviceTokenParams = Auth.TokenParams()
				deviceTokenParams.clientId = clientId()
				deviceTokenParams.capability = "{\"*\":[\"publish\",\"subscribe\",\"push-subscribe\"]}"
                return ablyForAdmin.auth.requestToken(deviceTokenParams, null)
            }
        }
		ablyForDevice = AblyRealtime(deviceOptions)

		/* this is necessary before the ablyForDevice can perform any operations that depend on
		 * an Android context, such as making the necessary platform operations for push */
		ablyForDevice.setAndroidContext(this)
		logger.i("initAbly()", "initialised library")

		/* monitor for connection state changes */
		ablyForDevice.connection.on(object: ConnectionStateListener {
			override fun onConnectionStateChanged(state: ConnectionStateListener.ConnectionStateChange?) {
				when(state!!.current) {
					ConnectionState.connecting -> logger.w("initAbly()", "connecting")
					ConnectionState.disconnected -> logger.w("initAbly()", "disconnected")
					ConnectionState.connected -> logger.i("initAbly()", "connected")
					ConnectionState.closed -> logger.e("initAbly()", "closed")
					ConnectionState.failed -> logger.e("initAbly()", "failed: err: " + state!!.reason.message)
					else -> logger.e("initAbly()", "unexpected connection state: ${state!!.current}")
				}
			}
		})
		return true
	}

    fun clientId(): String{
        return Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID)
    }

	/**
	 * Close the Ably connection. This removes any push channel subscription
	 * and does so after a delay - this is solely so that the background notifications
	 * work. (That is, we can finish() the activity, and send the notification, and
	 * the Ably library will remain until after that has happened */
	fun closeAbly() {
		/* tell the logger that we're exiting, so it doesn't try to appeand to a disposed UI */
		logger.close()
		object:Thread() {
			override fun run() {
				Thread.sleep(2000)
				pushUnsubscribe(channelName, true)
				ablyForDevice.close()
			}
		}.start()
	}

	/**
	 * Initialise the Push target system on this device. This uses the default (local)
	 * push registrar. This call ensures that the device will create a registration if
	 * one doesn't already exist, and it is ready to process any token renewal events
	 * that arise */
	fun activatePush(wait:Boolean = false):Boolean {
		object:Thread() {
			override fun run() {
				/* activate Firebase */
				logger.i("activatePush()", "initialising Firebase")
				FirebaseInstanceId.getInstance().getToken()

				synchronized(pushActivateReceiver) {
					/* ensure the Ably library registers any new token with the server */
					logger.i("activatePush()", "activating push system .. waiting")
					ablyForDevice.push.activate()
					if(wait) {
						pushActivateReceiver.waitFor("io.ably.broadcast.PUSH_ACTIVATE")
					}
					logger.i("activatePush()", ".. activated push system")
				}
			}
		}.start()
		return true
	}

	/**
	 * Reset the Push setup for this device. This uses the default (local)
	 * push registrar. This call ensures that the device will create a registration if
	 * one doesn't already exist, and it is ready to process any token renewal events
	 * that arise */
	fun deactivatePush(wait:Boolean = false):Boolean {
		object:Thread() {
			override fun run() {
				synchronized(pushActivateReceiver) {
					/* ensure the Ably library registers any new token with the server */
					logger.i("deactivatePush()", "deactivating push system .. waiting")
					ablyForDevice.push.deactivate()
					if(wait) {
						pushActivateReceiver.waitFor("io.ably.broadcast.PUSH_DEACTIVATE")
					}
					logger.i("deactivatePush()", ".. deactivated push system")
				}
			}
		}.start()
		return true
	}

	/**
	 * Subscribe for messages on a realtime channel
	 */
	fun realtimeSubscribe(testChannelName:String = channelName, wait:Boolean = false):Boolean {
        logger.i("realtimeSubscribe()", "subscribing to channel")
		val channel = ablyForDevice.channels.get(testChannelName)
        channel.on(object:ChannelStateListener {
            override fun onChannelStateChanged(stateChange: ChannelStateListener.ChannelStateChange?) {
                when(stateChange!!.current) {
                    ChannelState.attaching -> logger.w("realtimeSubscribe()", "attaching")
                    ChannelState.attached -> logger.i("realtimeSubscribe()", "attached")
                    ChannelState.failed -> logger.e("realtimeSubscribe()", "failed: err: " + stateChange!!.reason.message)
                    else -> logger.e("realtimeSubscribe()", "unexpected connection state: ${stateChange!!.current}")
                }
            }
        })
		val waiter = Object()
		var error:ErrorInfo? = null
		synchronized(waiter) {
			channel.attach(object:CompletionListener{
				override fun onError(reason: ErrorInfo?) {
					error = reason
					synchronized(waiter) {waiter.notify()}
				}

				override fun onSuccess() {
					channel.subscribe(object: Channel.MessageListener {
						override fun onMessage(message: Message?) {
							logger.i("realtimeSubscribe()", "received message: name=${message!!.name}; data=${message.data as String}")
						}
					})
					synchronized(waiter) {waiter.notify()}
				}
			})
			if(wait) {
				logger.i("realtimeSubscribe()", "waiting for channel attach ..")
				waiter.wait()
				logger.i("realtimeSubscribe()", ".. channel attached")
			}
		}
		if(error != null) {
			throw AblyException.fromErrorInfo(error)
		}
		return true
	}

	/**
	 * Publish a message via the realtime connection
	 */
	fun realtimePublish(testChannelName:String = channelName):Boolean {
        logger.i("realtimePublish()", "publishing to channel")
		val channel = ablyForDevice.channels.get(testChannelName)
		val message = Message("testMessageName", "testMessageData")
		channel.publish(message, object: CompletionListener {
			override fun onSuccess() {
                logger.i("realtimePublish()", "publish success")
			}
			override fun onError(reason: ErrorInfo?) {
                logger.e("realtimePublish()", "failed: err: " + reason!!.message)
			}
		});
		return true
	}

	/**
	 * Subscribe this device for push messages on the channel
	 */
	fun pushSubscribe(testChannelName:String = channelName, wait:Boolean = true):Boolean {
        if (subscriptionType == CLIENT_ID_SUBSCRIPTION) {
            logger.i("pushSubscribe()", "Unsubscribing the clientId subscription first")
            pushUnsubscribeClient()
        }
        logger.i("pushSubscribe()", "push subscribing to channel")
		val channel = ablyForDevice.channels.get(testChannelName)
		val waiter = Object()
		var error:ErrorInfo? = null
		synchronized(waiter) {
			channel.push.subscribeDeviceAsync(object: CompletionListener {
				override fun onSuccess() {
					subscriptionType = DEVICE_ID_SUBSCRIPTION
					logger.i("pushSubscribe()", "subscribe success")
					synchronized(waiter) {waiter.notify()}
				}
				override fun onError(reason: ErrorInfo?) {
					logger.e("pushSubscribe()", "failed: err: " + reason!!.message)
					synchronized(waiter) {error = reason; waiter.notify()}
				}
			})
			if(wait) {
				logger.i("pushSubscribe()", "waiting for push subscription to channel ..")
				waiter.wait()
				logger.i("pushSubscribe()", ".. push subscription complete")
			}
		}
		if(error != null) {
			throw AblyException.fromErrorInfo(error)
		}
		return true
	}

	/**
	 * Unsubscribe this device from push messages on the channel
	 */
	fun pushUnsubscribe(testChannelName:String = channelName, wait:Boolean = true):Boolean {
        logger.i("pushUnsubscribe()", "push unsubscribing from channel")
		val channel = ablyForDevice.channels.get(testChannelName)

		val waiter = Object()
		var error:ErrorInfo? = null
		synchronized(waiter) {
			val listener:CompletionListener? = if(wait) object: CompletionListener {
				override fun onSuccess() {
					logger.i("pushUnsubscribe()", "unsubscribe success")
					synchronized(waiter) {waiter.notify()}
				}
				override fun onError(reason: ErrorInfo?) {
					logger.e("pushUnsubscribe()", "failed: err: " + reason!!.message)
					synchronized(waiter) {error = reason; waiter.notify()}
				}
			} else null
			channel.push.unsubscribeDeviceAsync(listener)
			if(wait) {
				logger.i("pushSubscribe()", "waiting to unsubscribe from channel ..")
				waiter.wait()
				logger.i("pushSubscribe()", ".. unsubscribe complete")
			}
		}
		if(error != null) {
			throw AblyException.fromErrorInfo(error)
		}
		return true
	}

	/**
	 * Send a message on the channel containing push data
	 */
	fun pushPublishData(testRunId:String = runId, wait:Boolean = false):Boolean {
        logger.i("pushPublishData()", "pushing data message to channel")
		val channel = ablyForDevice.channels.get(channelName(testRunId))
		val data = JsonObject()
		data.add("testKey", JsonPrimitive("testValuePublish"))
		data.add("runId", JsonPrimitive(testRunId))
		val payload = JsonObject()
		payload.add("data", data)
		val extras = JsonObject()
		extras.add("push", payload)
		val message = Message("testMessageName", "testMessageData", extras)

		val waiter = Object()
		var error:ErrorInfo? = null
		synchronized(waiter) {
			channel.publish(message, object: CompletionListener {
				override fun onSuccess() {
					logger.i("pushPublishData()", "publish success")
					synchronized(waiter) {waiter.notify()}
				}
				override fun onError(reason: ErrorInfo?) {
					logger.e("pushPublishData()", "failed: err: " + reason!!.message)
					synchronized(waiter) {error = reason; waiter.notify()}
				}
			})
			if(wait) {
				logger.i("pushPublishData()", "waiting for push publish to channel ..")
				waiter.wait()
				logger.i("pushPublishData()", ".. push publish complete")
			}
		}
		if(error != null) {
			throw AblyException.fromErrorInfo(error)
		}
		return true
	}

	/**
	 * Send a message on the channel containing a push notification
	 */
	fun pushPublishNotification(testRunId:String = runId, wait:Boolean = false):Boolean {
        logger.i("pushPublishNotification()", "pushing notification message to channel")
		val channel = ablyForDevice.channels.get(channelName(testRunId))
		val notification = JsonObject()
		notification.add("title", JsonPrimitive("testNotification"))
		notification.add("body", JsonPrimitive("Hello from Ably push publish"))
		val payload = JsonObject()
		payload.add("notification", notification)
		val data = JsonObject()
		data.add("runId", JsonPrimitive(testRunId))
		payload.add("data", data)
		val extras = JsonObject()
		extras.add("push", payload)
		val message = Message("testMessageName", "testMessageData", extras)

		val waiter = Object()
		var error:ErrorInfo? = null
		synchronized(waiter) {
			channel.publish(message, object : CompletionListener {
				override fun onSuccess() {
					logger.i("pushPublishNotification()", "publish success")
					synchronized(waiter) {waiter.notify()}
				}

				override fun onError(reason: ErrorInfo?) {
					logger.e("pushPublishNotification()", "failed: err: " + reason!!.message)
					synchronized(waiter) {error = reason; waiter.notify()}
				}
			})
			if (wait) {
				logger.i("pushPublishNotification()", "waiting for push publish to channel ..")
				waiter.wait()
				logger.i("pushPublishNotification()", ".. push publish complete")
			}
		}
		if(error != null) {
			throw AblyException.fromErrorInfo(error)
		}
		return true
	}

	/**
	 * Send a message on the channel containing a push notification, after first exiting
	 * this activity, so this app is in the background when the notification arrives
	 */
	fun pushPublishNotificationBackground():Boolean {
        logger.i("pushPublishNotification()", "pushing notification message directly to device in background")
		object:Thread() {
			override fun run() {
				Thread.sleep(1000)
				pushPublishNotification()
			}
		}.start()
		finish()
		return true;
	}

	/**
	 * Send a message directly to the device containing push data
	 */
	fun pushDirectData(testRunId:String = runId, wait:Boolean = false):Boolean {
        logger.i("pushDirectData()", "pushing data message direct to device")
		val data = JsonObject()
		data.add("testKey", JsonPrimitive("testValueDirect"))
		data.add("runId", JsonPrimitive(testRunId))
		val payload = JsonObject()
		payload.add("data", data)
		val deviceId = ablyForDevice.push.getLocalDevice().id

		val waiter = Object()
		var error:ErrorInfo? = null
		synchronized(waiter) {
			ablyForAdmin.push.admin.publishAsync(arrayOf(Param("deviceId", deviceId)), payload, object: CompletionListener {
				override fun onSuccess() {
					logger.i("pushDirectData()", "publish success")
					synchronized(waiter) {waiter.notify()}
				}
				override fun onError(reason: ErrorInfo?) {
					logger.e("pushDirectData()", "failed: err: " + reason!!.message)
					synchronized(waiter) {error = reason; waiter.notify()}
				}
			})
			if (wait) {
				logger.i("pushDirectData()", "waiting for push direct publish ..")
				waiter.wait()
				logger.i("pushDirectData()", ".. push publish complete")
			}
		}
		if(error != null) {
			throw AblyException.fromErrorInfo(error)
		}
		return true
	}

	/**
	 * Send a message containing push data directly to devices with a specified clientId
	 */
	fun pushDirectDataClient(testRunId:String = runId, wait:Boolean = false):Boolean {
		logger.i("pushDirectData()", "pushing data message direct to clientId")
		val data = JsonObject()
		data.add("testKey", JsonPrimitive("testValueDirect"))
		data.add("runId", JsonPrimitive(testRunId))
		val payload = JsonObject()
		payload.add("data", data)
		val clientId = ablyForDevice.push.getLocalDevice().clientId

		val waiter = Object()
		var error:ErrorInfo? = null
		synchronized(waiter) {
			ablyForAdmin.push.admin.publishAsync(arrayOf(Param("clientId", clientId)), payload, object: CompletionListener {
				override fun onSuccess() {
					logger.i("pushDirectDataClient()", "publish success")
					synchronized(waiter) {waiter.notify()}
				}
				override fun onError(reason: ErrorInfo?) {
					logger.e("pushDirectDataClient()", "failed: err: " + reason!!.message)
					synchronized(waiter) {error = reason; waiter.notify()}
				}
			})
			if (wait) {
				logger.i("pushDirectDataClient()", "waiting for push direct to clientId publish ..")
				waiter.wait()
				logger.i("pushDirectDataClient()", ".. push publish complete")
			}
		}
		if(error != null) {
			throw AblyException.fromErrorInfo(error)
		}
		return true
	}

	/**
	 * Send a message directly to the device containing a push notification
	 */
	fun pushDirectNotification(testRunId:String = runId, wait:Boolean = false):Boolean {
        logger.i("pushDirectNotification()", "pushing notification message direct to device")
		val notification = JsonObject()
		notification.add("title", JsonPrimitive("testNotification"))
		notification.add("body", JsonPrimitive("Hello from Ably push direct"))
		val payload = JsonObject()
		payload.add("notification", notification)
		val data = JsonObject()
		data.add("runId", JsonPrimitive(testRunId))
		payload.add("data", data)
		val deviceId = ablyForDevice.push.getLocalDevice().id

		val waiter = Object()
		var error:ErrorInfo? = null
		synchronized(waiter) {
			ablyForAdmin.push.admin.publishAsync(arrayOf(Param("deviceId", deviceId)), payload, object: CompletionListener {
				override fun onSuccess() {
					logger.i("pushDirectNotification()", "publish success")
					synchronized(waiter) {waiter.notify()}
				}
				override fun onError(reason: ErrorInfo?) {
					logger.e("pushDirectNotification()", "failed: err: " + reason!!.message)
					synchronized(waiter) {error = reason; waiter.notify()}
				}
			})
			if (wait) {
				logger.i("pushDirectData()", "waiting for push direct publish ..")
				waiter.wait()
				logger.i("pushDirectData()", ".. push publish complete")
			}
		}
		if(error != null) {
			throw AblyException.fromErrorInfo(error)
		}
		return true
	}

	/**
	 * Send a message directly to the device containing a push notification, after first exiting
	 * this activity, so this app is in the background when the notification arrives
	 */
	fun pushDirectNotificationBackground():Boolean {
        logger.i("pushDirectNotificationBackground()", "pushing notification message direct to device in background")
		object:Thread() {
			override fun run() {
				Thread.sleep(1000)
				pushDirectNotification()
			}
		}.start()
		finish()
		return true;
	}

    /**
     * Subscribe this device for push messages on the channel
     */
    fun pushSubscribeClient(testChannelName: String = channelName, wait: Boolean = true): Boolean {
        if (subscriptionType == DEVICE_ID_SUBSCRIPTION) {
            logger.i("pushSubscribeClient()", "Unsubscribing channel that was subscribed using " +
                    "deviceId")
            pushUnsubscribe()
        }
        logger.i("pushSubscribeClient()", "push subscribing to channel")
        val channel = ablyForDevice.channels.get(testChannelName)
        val waiter = Object()
        var error: ErrorInfo? = null
        synchronized(waiter) {
            channel.push.subscribeClientAsync(object : CompletionListener {
                override fun onSuccess() {
                    logger.i("pushSubscribeClient()", "subscribe success using clientId: " + clientId())
                    subscriptionType = CLIENT_ID_SUBSCRIPTION
                    synchronized(waiter) { waiter.notify() }
                }

                override fun onError(reason: ErrorInfo?) {
                    logger.e("pushSubscribeClient()", "failed: err: " + reason!!.message)
                    synchronized(waiter) { error = reason; waiter.notify() }
                }
            })
            if (wait) {
                logger.i("pushSubscribeClient()", "waiting for push subscription to channel ..")
                waiter.wait()
                logger.i("pushSubscribeClient()", ".. push subscription complete")
            }
        }
        if (error != null) {
            throw AblyException.fromErrorInfo(error)
        }
        return true
    }

    /**
     * Unsubscribe this device from push messages on the channel
     */
    fun pushUnsubscribeClient(testChannelName: String = channelName, wait: Boolean = true): Boolean {
        logger.i("pushUnsubscribeClient()", "push unsubscribing from channel")
        val channel = ablyForDevice.channels.get(testChannelName)

        val waiter = Object()
        var error: ErrorInfo? = null
        synchronized(waiter) {
            val listener: CompletionListener? = if (wait) object : CompletionListener {
                override fun onSuccess() {
                    logger.i("pushUnsubscribeClient()", "unsubscribe success")
                    synchronized(waiter) { waiter.notify() }
                }

                override fun onError(reason: ErrorInfo?) {
                    logger.e("pushUnsubscribeClient()", "failed: err: " + reason!!.message)
                    synchronized(waiter) { error = reason; waiter.notify() }
                }
            } else null
            channel.push.unsubscribeClientAsync(listener)
            if (wait) {
                logger.i("pushUnsubscribeClient()", "waiting to unsubscribe from channel ..")
                waiter.wait()
                logger.i("pushUnsubscribeClient()", ".. unsubscribe complete")
            }
        }
        if (error != null) {
            throw AblyException.fromErrorInfo(error)
        }
        return true
    }

	/**
	 * Get details of the LocalDevice
	 */
	fun getLocalDevice():Boolean {
		val localDevice = ablyForDevice.push.getLocalDevice()
        logger.i("getLocalDevice()", "local device id: ${localDevice.id}")
		return true
	}

	/**
	 * Delete any persisted details of the LocalDevice
	 */
	fun resetLocalDevice():Boolean {
        logger.i("resetLocalDevice()", "resetting local device")
		ablyForDevice.push.getLocalDevice().reset()
		return true
	}

	/**
	 * Get the state of the push activation state machine
	 */
	fun getActivationState():Boolean {
		val activationState = ablyForDevice.push.activationContext.getActivationStateMachine().current.javaClass.canonicalName
        logger.i("getActivationState()", "activation state: ${activationState}")
		return true
	}

	/**
	 * Reset the state and delete any persisted details of the push activation state machine
	 */
	fun resetActivationState():Boolean {
        logger.i("resetActivationState()", "resetting activation state")
		ablyForDevice.push.activationContext.getActivationStateMachine().reset()
		return true
	}

	fun onPushMessageReceived(action: String, data: HashMap<String, String>) {
		val actionString = when(action) {
			AblyPushMessagingService.PUSH_DATA_ACTION -> "push message"
			AblyPushMessagingService.PUSH_NOTIFICATION_ACTION -> "push notification"
			else -> "unknown"
		}
		logger.i("onPushMessageReceived()", "${actionString} received:")
		data.forEach { (key, value) -> logger.i(" - ", "$key = $value") }
	}

	override fun onCreateOptionsMenu(menu: Menu): Boolean {
		menuInflater.inflate(R.menu.toolbar_menu, menu)
		return true
	}

	override fun onOptionsItemSelected(item: MenuItem): Boolean {
		return when(item.itemId) {
			R.id.action_run_test -> runPushTests()
			R.id.action_activate_push -> activatePush(true)
			R.id.action_deactivate_push -> deactivatePush(true)
			R.id.action_realtime_subscribe -> realtimeSubscribe()
			R.id.action_realtime_publish -> realtimePublish()
			R.id.action_push_subscribe -> pushSubscribe()
			R.id.action_push_unsubscribe -> pushUnsubscribe()
			R.id.action_push_publish_data -> pushPublishData()
			R.id.action_push_publish_notification -> pushPublishNotification()
			R.id.action_push_publish_notification_bg -> pushPublishNotificationBackground()
			R.id.action_push_direct_data -> pushDirectData()
			R.id.action_push_direct_notification -> pushDirectNotification()
			R.id.action_push_direct_notification_bg -> pushDirectNotificationBackground()
			R.id.action_push_direct_data_client -> pushDirectDataClient()
			R.id.action_get_local_device -> getLocalDevice()
			R.id.action_reset_local_device -> resetLocalDevice()
			R.id.action_get_activation_state -> getActivationState()
			R.id.action_reset_activation_state -> resetActivationState()
            R.id.action_push_subscribe_client -> pushSubscribeClient()
            R.id.action_push_unsubscribe_client -> pushUnsubscribeClient()
			else -> super.onOptionsItemSelected(item)
		}
	}
}
