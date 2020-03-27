/*
 * Copyright (C) 2017 Twilio, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.twilio.video.base;

import static org.apache.commons.lang3.RandomStringUtils.random;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import android.Manifest;
import android.support.annotation.CallSuper;
import android.support.annotation.Nullable;
import android.support.test.rule.ActivityTestRule;
import android.support.test.rule.GrantPermissionRule;
import com.twilio.video.ConnectOptions;
import com.twilio.video.IceOptions;
import com.twilio.video.LocalAudioTrack;
import com.twilio.video.LocalVideoTrack;
import com.twilio.video.Room;
import com.twilio.video.TestUtils;
import com.twilio.video.Video;
import com.twilio.video.VideoCodec;
import com.twilio.video.helper.CallbackHelper;
import com.twilio.video.model.VideoRoom;
import com.twilio.video.ui.MediaTestActivity;
import com.twilio.video.util.Constants;
import com.twilio.video.util.CredentialsUtils;
import com.twilio.video.util.RoomUtils;
import com.twilio.video.util.StringUtils;
import com.twilio.video.util.Topology;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.Rule;

public abstract class BaseStatsTest extends BaseVideoTest {
    @Rule
    public GrantPermissionRule recordAudioPermissionRule =
            GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO);

    @Rule
    public ActivityTestRule<MediaTestActivity> activityRule =
            new ActivityTestRule<>(MediaTestActivity.class);

    protected MediaTestActivity mediaTestActivity;
    protected String aliceToken;
    protected String bobToken;
    protected String roomName;
    protected Room aliceRoom;
    protected Room bobRoom;
    protected LocalVideoTrack aliceLocalVideoTrack;
    protected LocalAudioTrack aliceLocalAudioTrack;
    protected LocalVideoTrack bobLocalVideoTrack;
    protected LocalAudioTrack bobLocalAudioTrack;
    protected CallbackHelper.FakeRoomListener aliceListener;
    protected CallbackHelper.FakeRoomListener bobListener;
    protected CallbackHelper.FakeRemoteParticipantListener aliceMediaListener;
    protected CallbackHelper.FakeRemoteParticipantListener bobMediaListener;
    protected Topology topology;
    private VideoRoom videoRoom;

    protected void baseSetup(Topology topology) throws InterruptedException {
        baseSetup(topology, null);
    }

    protected void baseSetup(Topology topology, @Nullable List<VideoCodec> videoCodecs)
            throws InterruptedException {
        super.setup();
        mediaTestActivity = activityRule.getActivity();
        roomName = random(Constants.ROOM_NAME_LENGTH);
        videoRoom = RoomUtils.createRoom(roomName, topology, false, null, null, videoCodecs);
        assertNotNull(videoRoom);
        aliceToken = CredentialsUtils.getAccessToken(Constants.PARTICIPANT_ALICE);
        bobToken = CredentialsUtils.getAccessToken(Constants.PARTICIPANT_BOB);
        aliceListener = new CallbackHelper.FakeRoomListener();
        aliceMediaListener = new CallbackHelper.FakeRemoteParticipantListener();
        bobMediaListener = new CallbackHelper.FakeRemoteParticipantListener();
        bobListener = new CallbackHelper.FakeRoomListener();
    }

    @CallSuper
    protected void teardown() throws InterruptedException {
        roomTearDown(aliceRoom);
        roomTearDown(bobRoom);

        /*
         * After all participants have disconnected complete the room to clean up backend
         * resources.
         */
        if (aliceRoom != null && !StringUtils.isNullOrEmpty(aliceRoom.getSid())) {
            RoomUtils.completeRoom(aliceRoom);
        }
        if (bobRoom != null && !StringUtils.isNullOrEmpty(bobRoom.getSid())) {
            RoomUtils.completeRoom(bobRoom);
        }
        if (videoRoom != null) {
            RoomUtils.completeRoom(videoRoom);
        }

        if (aliceLocalAudioTrack != null) {
            aliceLocalAudioTrack.release();
        }
        if (aliceLocalVideoTrack != null) {
            aliceLocalVideoTrack.release();
        }
        if (bobLocalAudioTrack != null) {
            bobLocalAudioTrack.release();
        }
        if (bobLocalVideoTrack != null) {
            bobLocalVideoTrack.release();
        }
    }

    protected Room createRoom(
            String token, CallbackHelper.FakeRoomListener listener, String roomName)
            throws InterruptedException {
        return createRoom(token, listener, roomName, null, null);
    }

    protected Room createRoom(
            String token,
            CallbackHelper.FakeRoomListener listener,
            String roomName,
            List<LocalAudioTrack> audioTracks)
            throws InterruptedException {
        return createRoom(token, listener, roomName, audioTracks, null);
    }

    protected Room createRoom(
            String token,
            CallbackHelper.FakeRoomListener listener,
            String roomName,
            @Nullable List<LocalAudioTrack> audioTracks,
            @Nullable List<LocalVideoTrack> videoTracks)
            throws InterruptedException {
        if (audioTracks == null) {
            audioTracks = new ArrayList<>();
        }
        if (videoTracks == null) {
            videoTracks = new ArrayList<>();
        }

        IceOptions iceOptions =
                new IceOptions.Builder()
                        .abortOnIceServersTimeout(true)
                        .iceServersTimeout(TestUtils.ICE_TIMEOUT)
                        .build();
        ConnectOptions connectOptions =
                new ConnectOptions.Builder(token)
                        .roomName(roomName)
                        .audioTracks(audioTracks)
                        .videoTracks(videoTracks)
                        .iceOptions(iceOptions)
                        .build();

        return createRoom(listener, connectOptions);
    }

    protected Room createRoom(
            CallbackHelper.FakeRoomListener listener, ConnectOptions connectOptions)
            throws InterruptedException {
        listener.onConnectedLatch = new CountDownLatch(1);
        Room room = Video.connect(mediaTestActivity, connectOptions, listener);
        boolean connected =
                listener.onConnectedLatch.await(
                        TestUtils.STATE_TRANSITION_TIMEOUT, TimeUnit.SECONDS);

        // Call disconnect before failing to ensure native memory released
        if (!connected) {
            room.disconnect();

            fail("Failed to connect to room");
        }

        return room;
    }

    protected void roomTearDown(Room room) throws InterruptedException {
        if (room != null && room.getState() != Room.State.DISCONNECTED) {
            CallbackHelper.FakeRoomListener roomListener = new CallbackHelper.FakeRoomListener();
            roomListener.onDisconnectedLatch = new CountDownLatch(1);
            room.disconnect();
            roomListener.onDisconnectedLatch.await(
                    TestUtils.STATE_TRANSITION_TIMEOUT, TimeUnit.SECONDS);
        }
    }
}
