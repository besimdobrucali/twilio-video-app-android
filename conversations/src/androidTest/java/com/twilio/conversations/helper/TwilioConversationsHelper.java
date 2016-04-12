package com.twilio.conversations.helper;

import android.content.Context;
import android.support.annotation.Nullable;

import com.twilio.conversations.TwilioConversations;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class TwilioConversationsHelper {
    public static int INIT_TIMEOUT_SECONDS = 10;

    public static void initialize(Context context) throws InterruptedException {
        final CountDownLatch initLatch = new CountDownLatch(1);

        TwilioConversations.initialize(context, createInitListener(initLatch));

        assertTrue(initLatch.await(INIT_TIMEOUT_SECONDS, TimeUnit.SECONDS));

    }

    public static void destroy() {
        if(TwilioConversations.isInitialized()) {
            TwilioConversations.destroy();
        }
        while(TwilioConversations.isInitialized());
    }

    public static TwilioConversations.InitListener createInitListener() {
        return createInitListener(null);
    }

    public static TwilioConversations.InitListener createInitListener(
            @Nullable final CountDownLatch initLatch) {
        return new TwilioConversations.InitListener() {
            @Override
            public void onInitialized() {
                if (initLatch != null) {
                    initLatch.countDown();
                }
            }

            @Override
            public void onError(Exception exception) {
                fail(exception.getMessage());
            }
        };
    }
}
