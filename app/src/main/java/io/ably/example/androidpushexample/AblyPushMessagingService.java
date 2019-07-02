package io.ably.example.androidpushexample;

import android.content.Intent;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.HashMap;
import java.util.Map;

import io.ably.lib.push.ActivationContext;
import io.ably.lib.types.RegistrationToken;
import io.ably.lib.util.Log;

public class AblyPushMessagingService extends FirebaseMessagingService {
	public static final String PUSH_DATA_ACTION = "io.ably.example.androidpushexample.PUSH_DATA_MESSAGE";
	public static final String PUSH_NOTIFICATION_ACTION = "io.ably.example.androidpushexample.PUSH_NOTIFICATION_MESSAGE";

	@Override
	public void onNewToken(String token) {
		super.onNewToken(token);
		ActivationContext.getActivationContext(getApplicationContext()).onNewRegistrationToken(RegistrationToken.Type.FCM, token);
	}

	@Override
	public void onMessageReceived (RemoteMessage message) {
		Map<String, String> messageData = message.getData();
		if(messageData.isEmpty()) { return; }
		HashMap<String, String> serialisableData = new HashMap<String, String>();
		serialisableData.putAll(messageData);

		RemoteMessage.Notification messageNotification = message.getNotification();
		if(messageNotification != null) {
			Log.i(TAG, "Received message notification: title = " + messageNotification.getTitle() + "; body = " + messageNotification.getBody());
			serialisableData.put("title", messageNotification.getTitle());
			serialisableData.put("body", messageNotification.getBody());
			sendData(PUSH_NOTIFICATION_ACTION, serialisableData);
		} else {
			Log.i(TAG, "Received data message");
			sendData(PUSH_DATA_ACTION, serialisableData);
		}
	}

	private void sendData(String action, HashMap<String, String> data) {
		Intent broadCastIntent = new Intent();
		broadCastIntent.setAction(action);
		broadCastIntent.putExtra("data", data);
		LocalBroadcastManager.getInstance(this).sendBroadcast(broadCastIntent);
	}

	private static final String TAG = AblyPushMessagingService.class.getName();
}
