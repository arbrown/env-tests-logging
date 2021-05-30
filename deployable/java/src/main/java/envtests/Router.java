/*
 * Copyright 2021 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package main.java.envtests;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.logging.Severity;
import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.cloud.pubsub.v1.SubscriptionAdminClient;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.PubsubMessage;
import com.google.pubsub.v1.PushConfig;
import com.google.pubsub.v1.TopicName;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class Router {

  private static Snippets snippets = new Snippets();

  private static void subscribe(String projectId, String subscriptionId) {
    ProjectSubscriptionName subscriptionName = ProjectSubscriptionName.of(projectId, subscriptionId);

    MessageReceiver receiver = (PubsubMessage message, AckReplyConsumer consumer) -> {
      triggerTest(message, snippets);
      System.out.println("Id: " + message.getMessageId());
      System.out.println("Data: " + message.getData().toStringUtf8());
      consumer.ack();
    };

    Subscriber subscriber = null;
    try {
      subscriber = Subscriber.newBuilder(subscriptionName, receiver).build();
      subscriber.startAsync().awaitRunning();
      System.out.printf("Listening for messages on %s:\n", subscriptionName.toString());
      subscriber.awaitTerminated(30, TimeUnit.SECONDS);
    } catch (TimeoutException timeoutException) {
      subscriber.stopAsync();
    }
  }

  private static void triggerTest(PubsubMessage message, Snippets snippets) {
    // TODO while we could make it generic and use reflection, it's not safe so for now - ugly ifs
    if (message == null)
      return;

    String testName = message.getData().toStringUtf8();
    if (testName == null || testName == "")
      return;

    String logName = message.getAttributesOrDefault("log_name", null);
    String logText = message.getAttributesOrDefault("log_text", null);
    Severity severity = Severity.valueOf(message.getAttributesOrDefault("severity", null));
    if (testName == "simpleLog") {
      snippets.SimpleLog(logName, logText, severity);
    }
  }

  public static void main(String[] args) throws IOException {
    System.out.println("hello world!");
    String projectId = "MY_PROJECT_ID";
    String topicId;
    String subscriptionId;

    // ****************** GAE, GKE, GCE ******************
    // Enable app subscriber for all environments except GCR
    if (System.getenv("ENABLE_SUBSCRIBER") == "true") {
      // TODO - zeltser - figure out where to read it from
      GoogleCredentials credentials = GoogleCredentials.getApplicationDefault();
      if (credentials instanceof ServiceAccountCredentials) {
        projectId = ((ServiceAccountCredentials) credentials).getProjectId();
      }

      topicId = System.getenv("PUBSUB_TOPIC");
      if (topicId != null && topicId != "") {
        topicId = "logging-test";
      }

      Snippets snippets = new Snippets();

      subscriptionId = topicId + "-subscriber";
      TopicName topicName = TopicName.of(projectId, topicId);
      ProjectSubscriptionName subscriptionName = ProjectSubscriptionName.of(projectId, subscriptionId);
      SubscriptionAdminClient subscriptionClient = SubscriptionAdminClient.create();
      subscriptionClient.createSubscription(
              subscriptionName,
              topicName,
              PushConfig.newBuilder().build(),
              20);
      subscribe(projectId, subscriptionId);
    }
  }
}