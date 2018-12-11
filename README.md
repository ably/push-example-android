## Ably Android push example

This is a simple interactive app that exercises the use of the Ably push client API in an Android app.

### Interactive actions

Several operations can be triggered interactively via the menu. The operations are:

- **Activate push**: this triggers activation of the push system, so the device can be the target of push notifications.
- **Subscribe to channel**: this subscribes to a realtime channel. The channel name is randomly generated on startup.
Messages received on the channel are logged to the activity and logcat.
- **Publish to channel**: this publishes a single message to the realtime channel.
- **Subscribe to push channel**: this subscribes the device to a channel for push messages.
Push messages received on the channel are logged to the activity and logcat.
- **Unsubscribe to push channel**: this unsubscribes from the push channel.
- **Publish data to push channel**: this publishes a message to the realtime channel containing data in `message.extras.push.data`.
- **Publish notification to push channel**: this publishes a message to the realtime channel containing data in `message.extras.push.notification`.
- **Publish notification to push channel from background**: this publishes a message to the realtime channel containing data in `message.extras.push.notification`
after exiting the activity. The resulting message is expected to be delivered as a UI notification.
- **Publish data to push device**: this publishes a message direct to the device as a push target containing data in `payload.data`.
- **Publish notification to push device**: this publishes a message direct to the device as a push target containing data in `payload.notification`.
- **Publish notification to push device from background**: this publishes a message direct to the device as a push target containing data in `payload.notification`
after exiting the activity. The resulting message is expected to be delivered as a UI notification.
- **Get local device state**: this reads the current persisted `LocalDevice` state
- **Reset local device state**: this resets the persisted `LocalDevice` state
- **Get activation state**: this reads the current persisted activation state machine state
- **Reset activation state**: this resets the persisted activation state machine state

### End-to-end tests

There is a menu action to run end-to-end push tests which performs the following, in sequence:

- `pushDirectDataTest()`: publish data directly to the device, and verify receipt;
- `pushDirectNotificationTest()`: publish a notification directly to the device, and verify receipt;
- `pushPublishDataTest()`: publish data to the channel, and verify receipt as a push message;
- `pushPublishNotificaionTest()`: publish a notification to the channel, and verify receipt as a push notification;

Output is to the activity and logcat.

### Using this app yourself

You can clone and modify this app and use it to build your own push-enabled app, but in order for that to work you will need to:

- change the package id;
- re-namespace the code to reflect the package namespace;
- obtain a Firebase account, and register your app's package id with Firebase;
- download `google-services.json` and add that to the app in place of the existing file
- obtain an Ably API key;
- add the Ably API key to the app in place of the existing API key;
- configure the app's Firebase server key as the `fcmKey` in the Ably app
