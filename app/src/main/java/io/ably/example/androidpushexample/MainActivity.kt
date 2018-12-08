package io.ably.example.androidpushexample

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import android.support.v7.widget.Toolbar
import com.google.firebase.iid.FirebaseInstanceId
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import io.ably.lib.realtime.AblyRealtime
import io.ably.lib.realtime.CompletionListener
import io.ably.lib.types.ClientOptions
import io.ably.lib.types.ErrorInfo
import io.ably.lib.types.Message
import io.ably.lib.types.Param

const val TAG = "androidpushexample"

class MainActivity : AppCompatActivity() {
	val apiKey = "EHuTJg.KaTlBw:iNakMp56GDDlNwvk"
	val environment = "sandbox"
	val channelName = "push:test_push_channel_" + java.util.UUID.randomUUID().toString()

	lateinit var client: AblyRealtime

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		Log.i(TAG, "MainActvity onCreate")
		setContentView(R.layout.activity_main)
		var toolbar = findViewById(R.id.toolbar) as Toolbar
		setSupportActionBar(toolbar)

		initAbly()
	}

	override fun onDestroy() {
		closeAbly()
		super.onDestroy()
	}

	fun closeAbly() {
		object:Thread() {
			override fun run() {
				Thread.sleep(2000)
				pushUnsubscribe(object:CompletionListener {
					override fun onError(reason: ErrorInfo?) {
						client.close()
					}
					override fun onSuccess() {
						client.close()
					}
				})
			}
		}.start()
	}

	fun initAbly():Boolean {
		val options = ClientOptions(apiKey)
		options.environment = environment
		options.useBinaryProtocol = false
		options.logLevel = io.ably.lib.util.Log.VERBOSE
		options.autoConnect = true
		client = AblyRealtime(options)
		client.setAndroidContext(this)
		return true
	}

	fun activatePush():Boolean {
		/* activate Firebase */
		FirebaseInstanceId.getInstance().getToken()
		/* ensure the Ably library registers any new token with the server */
		client.push.activate()
		return true
	}

	fun realtimeSubscribe():Boolean {
		val channel = client.channels.get(channelName)
		channel.subscribe({ message -> displayMessage(message.data as String) })
		return true
	}

	fun realtimePublish():Boolean {
		val channel = client.channels.get(channelName)
		val message = Message("testMessageName", "testMessageData")
		channel.publish(message, object: CompletionListener {
			override fun onSuccess() {
			}

			override fun onError(reason: ErrorInfo?) {
			}

		});
		return true
	}

	fun pushSubscribe():Boolean {
		val channel = client.channels.get(channelName)
		channel.push.subscribeDeviceAsync(object: CompletionListener {
			override fun onSuccess() {
			}

			override fun onError(reason: ErrorInfo?) {
			}

		})
		return true
	}

	fun pushUnsubscribe():Boolean {
		val listener = object: CompletionListener {
			override fun onSuccess() {
			}

			override fun onError(reason: ErrorInfo?) {
			}

		}
		return pushUnsubscribe(listener)
	}

	fun pushUnsubscribe(listener:CompletionListener):Boolean {
		val channel = client.channels.get(channelName)
		channel.push.unsubscribeDeviceAsync(listener)
		return true
	}

	fun pushPublishData():Boolean {
		val channel = client.channels.get(channelName)
		val data = JsonObject()
		data.add("testKey", JsonPrimitive("testValuePublish"))
		val payload = JsonObject()
		payload.add("data", data)
		val extras = JsonObject()
		extras.add("push", payload)
		val message = Message("testMessageName", "testMessageData", extras)
		channel.publish(message, object: CompletionListener {
			override fun onSuccess() {
			}
			override fun onError(reason: ErrorInfo?) {
			}
		});
		return true
	}

	fun pushPublishNotification():Boolean {
		val channel = client.channels.get(channelName)
		val notification = JsonObject()
		notification.add("title", JsonPrimitive("testNotification"))
		notification.add("body", JsonPrimitive("Hello from Ably push publish"))
		val payload = JsonObject()
		payload.add("notification", notification)
		val extras = JsonObject()
		extras.add("push", payload)
		val message = Message("testMessageName", "testMessageData", extras)
		channel.publish(message, object: CompletionListener {
			override fun onSuccess() {
			}

			override fun onError(reason: ErrorInfo?) {
			}

		});
		return true
	}

	fun pushPublishNotificationBackground():Boolean {
		object:Thread() {
			override fun run() {
				Thread.sleep(1000)
				pushPublishNotification()
			}
		}.start()
		finish()
		return true;
	}

	fun pushDirectData():Boolean {
		val data = JsonObject()
		data.add("testKey", JsonPrimitive("testValueDirect"))
		val payload = JsonObject()
		payload.add("data", data)
		val deviceId = client.push.getLocalDevice().id
		client.push.admin.publishAsync(arrayOf(Param("deviceId", deviceId)), payload, object: CompletionListener {
			override fun onSuccess() {
			}

			override fun onError(reason: ErrorInfo?) {
			}

		})
		return true
	}

	fun pushDirectNotification():Boolean {
		val notification = JsonObject()
		notification.add("title", JsonPrimitive("testNotification"))
		notification.add("body", JsonPrimitive("Hello from Ably push direct"))
		val payload = JsonObject()
		payload.add("notification", notification)
		val deviceId = client.push.getLocalDevice().id
		client.push.admin.publishAsync(arrayOf(Param("deviceId", deviceId)), payload, object: CompletionListener {
			override fun onSuccess() {
			}
			override fun onError(reason: ErrorInfo?) {
			}
		})
		return true
	}

	fun pushDirectNotificationBackground():Boolean {
		object:Thread() {
			override fun run() {
				Thread.sleep(1000)
				pushDirectNotification()
			}
		}.start()
		finish()
		return true;
	}

	fun getLocalDevice():Boolean {
		val localDevice = client.push.getLocalDevice()

		return true
	}

	fun resetLocalDevice():Boolean {
		client.push.getLocalDevice().reset()
		return true
	}

	fun getActivationState():Boolean {
		client.push.activationContext.getActivationStateMachine().current.javaClass.canonicalName
		return true
	}

	fun resetActivationState():Boolean {
		client.push.activationContext.getActivationStateMachine().reset()
		return true
	}

	fun displayMessage(message:String) {
		Toast.makeText(this, message, Toast.LENGTH_LONG).show()
	}

	override fun onCreateOptionsMenu(menu: Menu): Boolean {
		menuInflater.inflate(R.menu.toolbar_menu, menu)
		return true
	}

	override fun onOptionsItemSelected(item: MenuItem): Boolean {
		return when(item.itemId) {
			R.id.action_activate_push -> activatePush()
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
			R.id.action_get_local_device -> getLocalDevice()
			R.id.action_reset_local_device -> resetLocalDevice()
			R.id.action_get_activation_state -> getActivationState()
			R.id.action_reset_activation_state -> resetActivationState()
			else -> super.onOptionsItemSelected(item)
		}
	}
}
