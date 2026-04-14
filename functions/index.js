const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");

initializeApp();

exports.sendSosFcm = onDocumentCreated("sos_requests/{requestId}", async (event) => {
  const snap = event.data;
  if (!snap) {
    return;
  }
  const data = snap.data();
  const targetId = data.targetDeviceId;
  if (!targetId) {
    return;
  }
  const deviceDoc = await getFirestore().collection("devices").doc(targetId).get();
  const token = deviceDoc.get("fcmToken");
  if (!token) {
    return;
  }
  const senderName = data.senderName || "Contact";
  await getMessaging().send({
    token,
    notification: {
      title: "SOS SafeNow",
      body: senderName + " declenche une alerte",
    },
    data: {
      type: "SOS",
      senderName: String(data.senderName || ""),
      senderDeviceId: String(data.senderDeviceId || ""),
    },
    android: {
      priority: "high",
      notification: {
        channelId: "safenow_sos",
        sound: "default",
      },
    },
  });
});
