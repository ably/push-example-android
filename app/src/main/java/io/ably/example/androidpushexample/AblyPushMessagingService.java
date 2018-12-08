package io.ably.example.androidpushexample;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import io.ably.lib.util.Log;

import java.util.Map;

public class AblyPushMessagingService extends FirebaseMessagingService {
	@Override
	public void onMessageReceived (RemoteMessage message) {
		Map<String, String> messageData = message.getData();
		if(!messageData.isEmpty()) {
			Log.i(TAG, "Received data message");
			for(String key : messageData.keySet()) {
				Log.i(TAG, "message data: key = " + key + "; value = " + messageData.get(key));
			}
		}
		RemoteMessage.Notification messageNotification = message.getNotification();
		if(messageNotification != null) {
			Log.i(TAG, "Received message notification: title = " + messageNotification.getTitle() + "; body = " + messageNotification.getBody());
		}
	}

	private static final String TAG = AblyPushMessagingService.class.getName();
}
