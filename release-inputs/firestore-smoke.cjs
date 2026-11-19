const { initializeTestEnvironment, assertFails, assertSucceeds } =
  require("@firebase/rules-unit-testing");
const fs = require("node:fs");
const assert = require("node:assert/strict");
const { test, before, after } = require("node:test");

let env;
const ids = ["aman_uid_002", "rahul_uid_001"];
const convId = ids.join("_");
const now = () => new Date(Date.now() - 5000);
before(async () => {
  env = await initializeTestEnvironment({
    projectId: process.env.GCLOUD_PROJECT || "demo-msguchat",
    firestore: {
      rules: fs.readFileSync("firestore.rules", "utf8"),
      host: "127.0.0.1", port: 8085,
    },
  });
});
after(async () => { if (env) await env.cleanup(); });

test("Rahul -> Aman -> Rahul Firestore chat and private message security", async () => {
  const [amanId, rahulId] = ids;
  await env.withSecurityRulesDisabled(async ctx => {
    for (const [uid, username] of [[rahulId, "rahul"], [amanId, "aman"]]) {
      await ctx.firestore().collection("users").doc(uid).set({
        userId: uid, username, displayName: username, createdAt: now(), updatedAt: now()
      });
    }
  });
  const rahul = env.authenticatedContext(rahulId).firestore();
  const aman = env.authenticatedContext(amanId).firestore();
  const eve = env.authenticatedContext("eve_uid_003").firestore();
  const unauthorized = env.unauthenticatedContext().firestore();
  const conv = rahul.collection("conversations").doc(convId);
  await assertFails(unauthorized.collection("conversations").doc(convId).get());
  await assertSucceeds(conv.set({
    participantUids: ids,
    participantNames: { [rahulId]: "Rahul", [amanId]: "Aman" },
    participantUsernames: { [rahulId]: "rahul", [amanId]: "aman" },
    lastMessageText: "",
    lastMessageSenderId: "",
    lastMessageType: "NONE",
    lastMessageAt: now(),
    unreadCounts: { [rahulId]: 0, [amanId]: 0 },
    createdAt: now(), updatedAt: now(),
  }));
  const msg = conv.collection("messages").doc("rahul1");
  const payload = {
    conversationId: convId, participantUids: ids,
    senderId: rahulId, text: "Hello Aman",
    messageType: "TEXT", clientMessageId: "rahul1",
    clientTimestampMs: Date.now()-1000,
    readBy: [rahulId], createdAt: now(), updatedAt: now(),
  };
  const batch = rahul.batch();
  batch.set(msg,payload);
  batch.update(conv, {
    lastMessageText: "Hello Aman", lastMessageSenderId:rahulId,
    lastMessageType:"TEXT",lastMessageAt:now(),
    ["unreadCounts."+amanId]:1,["unreadCounts."+rahulId]:0,updatedAt:now(),
  });
  await assertSucceeds(batch.commit());
  const received = await assertSucceeds(
    aman.collection("conversations").doc(convId).collection("messages")
      .where("participantUids", "array-contains", amanId).get()
  );
  assert.equal(received.docs.length,1);
  assert.equal(received.docs[0].data().text,"Hello Aman");
  await assertSucceeds(
    aman.collection("conversations").doc(convId).collection("messages").doc("rahul1").update({
      readBy:ids,updatedAt:now()
    })
  );
  await assertFails(eve.collection("conversations").doc(convId).get());
  await assertFails(eve.collection("conversations").doc(convId).collection("messages").doc("rahul1").get());
  await assertFails(aman.collection("conversations").doc(convId).collection("messages").doc("rahul1").update({
    text:"tampered",updatedAt:now(),
  }));
  await assertSucceeds(
    aman.collection("conversations").doc(convId).collection("messages").doc("aman1").set({
      conversationId:convId,participantUids:ids,senderId:amanId,
      text:"Hello Rahul",messageType:"TEXT",clientMessageId:"aman1",
      clientTimestampMs:Date.now()-800,readBy:[amanId],createdAt:now(),updatedAt:now(),
    })
  );
  const reply = await assertSucceeds(
    rahul.collection("conversations").doc(convId).collection("messages").doc("aman1").get()
  );
  assert.equal(reply.data().text,"Hello Rahul");
});
