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

package com.twilio.video;

import static com.twilio.video.TestUtils.ICE_TIMEOUT;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static org.apache.commons.lang3.RandomStringUtils.random;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.LargeTest;
import com.twilio.video.base.BaseParticipantTest;
import com.twilio.video.helper.CallbackHelper;
import com.twilio.video.model.VideoRoom;
import com.twilio.video.testcategories.ParticipantTest;
import com.twilio.video.util.Constants;
import com.twilio.video.util.CredentialsUtils;
import com.twilio.video.util.FakeVideoCapturer;
import com.twilio.video.util.RoomUtils;
import com.twilio.video.util.Topology;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@ParticipantTest
@RunWith(Parameterized.class)
@LargeTest
public class RemoteParticipantTopologyParameterizedTest extends BaseParticipantTest {
    @Parameterized.Parameters(name = "{0}")
    public static Iterable<Object[]> data() {
        return Arrays.asList(new Object[][] {{Topology.P2P}, {Topology.GROUP}});
    }

    private Context context;
    private String tokenOne;
    private String tokenTwo;
    private String roomName;
    private Room room;
    private final CallbackHelper.FakeRoomListener roomListener =
            new CallbackHelper.FakeRoomListener();
    private Room otherRoom;
    private final CallbackHelper.FakeRoomListener otherRoomListener =
            new CallbackHelper.FakeRoomListener();
    private final Topology topology;
    private VideoRoom videoRoom;

    public RemoteParticipantTopologyParameterizedTest(Topology topology) {
        this.topology = topology;
    }

    @Before
    public void setup() throws InterruptedException {
        super.baseSetup(topology);
        roomName = random(Constants.ROOM_NAME_LENGTH);
        videoRoom = RoomUtils.createRoom(roomName, topology);
        assertNotNull(videoRoom);
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        tokenOne = CredentialsUtils.getAccessToken(Constants.PARTICIPANT_ALICE);
        tokenTwo = CredentialsUtils.getAccessToken(Constants.PARTICIPANT_BOB);
    }

    @After
    public void teardown() throws InterruptedException {
        super.teardown();
        disconnect(room, roomListener);
        disconnect(otherRoom, otherRoomListener);
        /*
         * After all participants have disconnected complete the room to clean up backend
         * resources.
         */
        if (room != null) {
            RoomUtils.completeRoom(room);
        }
        if (otherRoom != null) {
            RoomUtils.completeRoom(otherRoom);
        }
        RoomUtils.completeRoom(videoRoom);
        assertTrue(MediaFactory.isReleased());
    }

    @Test
    public void participantCanConnect() throws InterruptedException {
        roomListener.onConnectedLatch = new CountDownLatch(1);
        roomListener.onDisconnectedLatch = new CountDownLatch(1);
        roomListener.onParticipantConnectedLatch = new CountDownLatch(1);
        IceOptions iceOptions =
                new IceOptions.Builder()
                        .iceServersTimeout(ICE_TIMEOUT)
                        .abortOnIceServersTimeout(true)
                        .build();
        ConnectOptions connectOptions =
                new ConnectOptions.Builder(tokenOne)
                        .roomName(roomName)
                        .iceOptions(iceOptions)
                        .build();
        room = Video.connect(context, connectOptions, roomListener);
        assertTrue(
                roomListener.onConnectedLatch.await(
                        TestUtils.STATE_TRANSITION_TIMEOUT, TimeUnit.SECONDS));
        assertEquals(Room.State.CONNECTED, room.getState());

        connectOptions =
                new ConnectOptions.Builder(tokenTwo)
                        .roomName(roomName)
                        .iceOptions(iceOptions)
                        .build();
        otherRoomListener.onDisconnectedLatch = new CountDownLatch(1);
        otherRoom = Video.connect(context, connectOptions, otherRoomListener);
        assertTrue(
                roomListener.onParticipantConnectedLatch.await(
                        TestUtils.STATE_TRANSITION_TIMEOUT, TimeUnit.SECONDS));
        assertEquals(1, room.getRemoteParticipants().size());
    }

    @Test
    public void participantCanDisconnect() throws InterruptedException {
        roomListener.onConnectedLatch = new CountDownLatch(1);
        roomListener.onDisconnectedLatch = new CountDownLatch(1);
        roomListener.onParticipantDisconnectedLatch = new CountDownLatch(1);
        roomListener.onParticipantConnectedLatch = new CountDownLatch(1);
        IceOptions iceOptions =
                new IceOptions.Builder()
                        .iceServersTimeout(ICE_TIMEOUT)
                        .abortOnIceServersTimeout(true)
                        .build();
        ConnectOptions connectOptions =
                new ConnectOptions.Builder(tokenOne)
                        .roomName(roomName)
                        .iceOptions(iceOptions)
                        .build();
        room = Video.connect(context, connectOptions, roomListener);
        assertTrue(
                roomListener.onConnectedLatch.await(
                        TestUtils.STATE_TRANSITION_TIMEOUT, TimeUnit.SECONDS));
        assertEquals(Room.State.CONNECTED, room.getState());

        ConnectOptions connectOptions2 =
                new ConnectOptions.Builder(tokenTwo)
                        .roomName(roomName)
                        .iceOptions(iceOptions)
                        .build();
        otherRoomListener.onConnectedLatch = new CountDownLatch(1);
        otherRoomListener.onDisconnectedLatch = new CountDownLatch(1);
        otherRoom = Video.connect(context, connectOptions2, otherRoomListener);

        assertTrue(
                otherRoomListener.onConnectedLatch.await(
                        TestUtils.STATE_TRANSITION_TIMEOUT, TimeUnit.SECONDS));

        List<RemoteParticipant> client2RemoteParticipants =
                new ArrayList<>(otherRoom.getRemoteParticipants());
        RemoteParticipant client1RemoteParticipant = client2RemoteParticipants.get(0);

        assertEquals(1, client2RemoteParticipants.size());
        assertTrue(client1RemoteParticipant.isConnected());
        assertTrue(
                roomListener.onParticipantConnectedLatch.await(
                        TestUtils.STATE_TRANSITION_TIMEOUT, TimeUnit.SECONDS));

        List<RemoteParticipant> client1RemoteParticipants =
                new ArrayList<>(room.getRemoteParticipants());
        RemoteParticipant client2RemoteParticipant = client1RemoteParticipants.get(0);

        assertEquals(1, client1RemoteParticipants.size());
        assertTrue(client2RemoteParticipant.isConnected());

        otherRoom.disconnect();
        assertTrue(
                otherRoomListener.onDisconnectedLatch.await(
                        TestUtils.STATE_TRANSITION_TIMEOUT, TimeUnit.SECONDS));
        assertTrue(
                roomListener.onParticipantDisconnectedLatch.await(
                        TestUtils.STATE_TRANSITION_TIMEOUT, TimeUnit.SECONDS));
        assertFalse(client2RemoteParticipant.isConnected());
        assertTrue(room.getRemoteParticipants().isEmpty());

        room.disconnect();
        assertTrue(
                roomListener.onDisconnectedLatch.await(
                        TestUtils.STATE_TRANSITION_TIMEOUT, TimeUnit.SECONDS));
        assertFalse(client1RemoteParticipant.isConnected());
    }

    @Test
    public void participantShouldHaveValidIdentity() throws InterruptedException {
        String expectedIdentity = random(50);
        tokenOne = CredentialsUtils.getAccessToken(expectedIdentity);
        roomListener.onConnectedLatch = new CountDownLatch(1);
        roomListener.onDisconnectedLatch = new CountDownLatch(1);
        roomListener.onParticipantDisconnectedLatch = new CountDownLatch(1);
        roomListener.onParticipantConnectedLatch = new CountDownLatch(1);
        IceOptions iceOptions =
                new IceOptions.Builder()
                        .iceServersTimeout(ICE_TIMEOUT)
                        .abortOnIceServersTimeout(true)
                        .build();
        ConnectOptions connectOptions =
                new ConnectOptions.Builder(tokenOne)
                        .roomName(roomName)
                        .iceOptions(iceOptions)
                        .build();
        room = Video.connect(context, connectOptions, roomListener);
        assertTrue(
                roomListener.onConnectedLatch.await(
                        TestUtils.STATE_TRANSITION_TIMEOUT, TimeUnit.SECONDS));
        assertEquals(Room.State.CONNECTED, room.getState());

        ConnectOptions connectOptions2 =
                new ConnectOptions.Builder(tokenTwo)
                        .roomName(roomName)
                        .iceOptions(iceOptions)
                        .build();
        otherRoomListener.onConnectedLatch = new CountDownLatch(1);
        otherRoomListener.onDisconnectedLatch = new CountDownLatch(1);
        otherRoom = Video.connect(context, connectOptions2, otherRoomListener);

        assertTrue(
                otherRoomListener.onConnectedLatch.await(
                        TestUtils.STATE_TRANSITION_TIMEOUT, TimeUnit.SECONDS));

        List<RemoteParticipant> client2RemoteParticipants =
                new ArrayList<>(otherRoom.getRemoteParticipants());
        RemoteParticipant client1RemoteParticipant = client2RemoteParticipants.get(0);

        assertEquals(1, client2RemoteParticipants.size());
        assertTrue(client1RemoteParticipant.isConnected());
        assertTrue(
                roomListener.onParticipantConnectedLatch.await(
                        TestUtils.STATE_TRANSITION_TIMEOUT, TimeUnit.SECONDS));
        assertEquals(expectedIdentity, client1RemoteParticipant.getIdentity());

        List<RemoteParticipant> client1RemoteParticipants =
                new ArrayList<>(room.getRemoteParticipants());
        RemoteParticipant client2RemoteParticipant = client1RemoteParticipants.get(0);

        assertEquals(1, client1RemoteParticipants.size());
        assertTrue(client2RemoteParticipant.isConnected());

        otherRoom.disconnect();
        assertTrue(
                otherRoomListener.onDisconnectedLatch.await(
                        TestUtils.STATE_TRANSITION_TIMEOUT, TimeUnit.SECONDS));
        assertTrue(
                roomListener.onParticipantDisconnectedLatch.await(
                        TestUtils.STATE_TRANSITION_TIMEOUT, TimeUnit.SECONDS));
        assertFalse(client2RemoteParticipant.isConnected());
        assertTrue(room.getRemoteParticipants().isEmpty());

        room.disconnect();
        assertTrue(
                roomListener.onDisconnectedLatch.await(
                        TestUtils.STATE_TRANSITION_TIMEOUT, TimeUnit.SECONDS));
        assertFalse(client1RemoteParticipant.isConnected());
    }

    @Test
    public void shouldReceiveTrackEvents() throws InterruptedException {
        // Audio track added and subscribed
        CallbackHelper.FakeRemoteParticipantListener remoteParticipantListener =
                new CallbackHelper.FakeRemoteParticipantListener();
        remoteParticipantListener.onAudioTrackPublishedLatch = new CountDownLatch(1);
        remoteParticipantListener.onSubscribedToAudioTrackLatch = new CountDownLatch(1);
        bobRemoteParticipant.setListener(remoteParticipantListener);
        bobLocalAudioTrack = LocalAudioTrack.create(mediaTestActivity, true);
        assertTrue(bobRoom.getLocalParticipant().publishTrack(bobLocalAudioTrack));
        assertTrue(
                remoteParticipantListener.onAudioTrackPublishedLatch.await(
                        TestUtils.STATE_TRANSITION_TIMEOUT, TimeUnit.SECONDS));
        assertTrue(
                remoteParticipantListener.onSubscribedToAudioTrackLatch.await(
                        TestUtils.STATE_TRANSITION_TIMEOUT, TimeUnit.SECONDS));

        // Audio track disabled
        remoteParticipantListener.onAudioTrackDisabledLatch = new CountDownLatch(1);
        bobLocalAudioTrack.enable(false);
        assertTrue(
                remoteParticipantListener.onAudioTrackDisabledLatch.await(
                        TestUtils.STATE_TRANSITION_TIMEOUT, TimeUnit.SECONDS));

        // Audio track enabled
        remoteParticipantListener.onAudioTrackEnabledLatch = new CountDownLatch(1);
        bobLocalAudioTrack.enable(true);
        assertTrue(
                remoteParticipantListener.onAudioTrackEnabledLatch.await(
                        TestUtils.STATE_TRANSITION_TIMEOUT, TimeUnit.SECONDS));

        // Audio track removed and unsubscribed
        remoteParticipantListener.onAudioTrackUnpublishedLatch = new CountDownLatch(1);
        remoteParticipantListener.onUnsubscribedFromAudioTrackLatch = new CountDownLatch(1);
        bobRoom.getLocalParticipant().unpublishTrack(bobLocalAudioTrack);
        assertTrue(
                remoteParticipantListener.onUnsubscribedFromAudioTrackLatch.await(
                        TestUtils.STATE_TRANSITION_TIMEOUT, TimeUnit.SECONDS));
        assertTrue(
                remoteParticipantListener.onAudioTrackUnpublishedLatch.await(
                        TestUtils.STATE_TRANSITION_TIMEOUT, TimeUnit.SECONDS));

        // Video track added and subscribed
        remoteParticipantListener.onVideoTrackPublishedLatch = new CountDownLatch(1);
        remoteParticipantListener.onSubscribedToVideoTrackLatch = new CountDownLatch(1);
        bobLocalVideoTrack =
                LocalVideoTrack.create(mediaTestActivity, true, new FakeVideoCapturer());
        assertTrue(bobRoom.getLocalParticipant().publishTrack(bobLocalVideoTrack));
        assertTrue(
                remoteParticipantListener.onVideoTrackPublishedLatch.await(
                        TestUtils.STATE_TRANSITION_TIMEOUT, TimeUnit.SECONDS));
        assertTrue(
                remoteParticipantListener.onSubscribedToVideoTrackLatch.await(
                        TestUtils.STATE_TRANSITION_TIMEOUT, TimeUnit.SECONDS));

        // Video track disabled
        remoteParticipantListener.onVideoTrackDisabledLatch = new CountDownLatch(1);
        bobLocalVideoTrack.enable(false);
        assertTrue(
                remoteParticipantListener.onVideoTrackDisabledLatch.await(
                        TestUtils.STATE_TRANSITION_TIMEOUT, TimeUnit.SECONDS));

        // Video track enabled
        remoteParticipantListener.onVideoTrackEnabledLatch = new CountDownLatch(1);
        bobLocalVideoTrack.enable(true);
        assertTrue(
                remoteParticipantListener.onVideoTrackEnabledLatch.await(
                        TestUtils.STATE_TRANSITION_TIMEOUT, TimeUnit.SECONDS));

        // Video track removed and unsubscribed
        remoteParticipantListener.onVideoTrackUnpublishedLatch = new CountDownLatch(1);
        remoteParticipantListener.onUnsubscribedFromVideoTrackLatch = new CountDownLatch(1);
        bobRoom.getLocalParticipant().unpublishTrack(bobLocalVideoTrack);
        assertTrue(
                remoteParticipantListener.onUnsubscribedFromVideoTrackLatch.await(
                        TestUtils.STATE_TRANSITION_TIMEOUT, TimeUnit.SECONDS));
        assertTrue(
                remoteParticipantListener.onVideoTrackUnpublishedLatch.await(
                        TestUtils.STATE_TRANSITION_TIMEOUT, TimeUnit.SECONDS));

        // Data track published and subscribed
        remoteParticipantListener.onDataTrackPublishedLatch = new CountDownLatch(1);
        remoteParticipantListener.onSubscribedToDataTrackLatch = new CountDownLatch(1);
        bobLocalDataTrack = LocalDataTrack.create(mediaTestActivity);
        assertTrue(bobRoom.getLocalParticipant().publishTrack(bobLocalDataTrack));
        assertTrue(
                remoteParticipantListener.onDataTrackPublishedLatch.await(
                        TestUtils.STATE_TRANSITION_TIMEOUT, TimeUnit.SECONDS));
        assertTrue(
                remoteParticipantListener.onSubscribedToDataTrackLatch.await(
                        TestUtils.STATE_TRANSITION_TIMEOUT, TimeUnit.SECONDS));

        // Data track unsubscribed and unpublished
        remoteParticipantListener.onDataTrackUnpublishedLatch = new CountDownLatch(1);
        remoteParticipantListener.onUnsubscribedFromDataTrackLatch = new CountDownLatch(1);
        bobRoom.getLocalParticipant().unpublishTrack(bobLocalDataTrack);
        assertTrue(
                remoteParticipantListener.onUnsubscribedFromDataTrackLatch.await(
                        TestUtils.STATE_TRANSITION_TIMEOUT, TimeUnit.SECONDS));
        assertTrue(
                remoteParticipantListener.onDataTrackUnpublishedLatch.await(
                        TestUtils.STATE_TRANSITION_TIMEOUT, TimeUnit.SECONDS));

        // Validate the order of events
        List<String> expectedParticipantEvents =
                Arrays.asList(
                        "onAudioTrackPublished",
                        "onAudioTrackSubscribed",
                        "onAudioTrackDisabled",
                        "onAudioTrackEnabled",
                        "onAudioTrackUnsubscribed",
                        "onAudioTrackUnpublished",
                        "onVideoTrackPublished",
                        "onVideoTrackSubscribed",
                        "onVideoTrackDisabled",
                        "onVideoTrackEnabled",
                        "onVideoTrackUnsubscribed",
                        "onVideoTrackUnpublished",
                        "onDataTrackPublished",
                        "onDataTrackSubscribed",
                        "onDataTrackUnsubscribed",
                        "onDataTrackUnpublished");
        assertArrayEquals(
                expectedParticipantEvents.toArray(),
                remoteParticipantListener.remoteParticipantEvents.toArray());
    }

    @Test
    public void shouldHaveTracksAfterDisconnected() throws InterruptedException {
        // Add audio and video tracks
        CallbackHelper.FakeRemoteParticipantListener remoteParticipantListener =
                new CallbackHelper.FakeRemoteParticipantListener();
        remoteParticipantListener.onSubscribedToAudioTrackLatch = new CountDownLatch(1);
        this.bobRemoteParticipant.setListener(remoteParticipantListener);
        bobLocalAudioTrack = LocalAudioTrack.create(mediaTestActivity, false);
        assertTrue(bobRoom.getLocalParticipant().publishTrack(bobLocalAudioTrack));
        assertTrue(
                remoteParticipantListener.onSubscribedToAudioTrackLatch.await(
                        TestUtils.STATE_TRANSITION_TIMEOUT, TimeUnit.SECONDS));
        remoteParticipantListener.onSubscribedToVideoTrackLatch = new CountDownLatch(1);
        bobLocalVideoTrack =
                LocalVideoTrack.create(mediaTestActivity, true, new FakeVideoCapturer());
        assertTrue(bobRoom.getLocalParticipant().publishTrack(bobLocalVideoTrack));
        assertTrue(
                remoteParticipantListener.onSubscribedToVideoTrackLatch.await(
                        TestUtils.STATE_TRANSITION_TIMEOUT, TimeUnit.SECONDS));

        // Cache bobRemoteParticipant two tracks
        List<RemoteAudioTrackPublication> remoteAudioTrackPublications =
                aliceRoomListener.getRemoteParticipant().getRemoteAudioTracks();
        List<AudioTrackPublication> audioTrackPublications =
                aliceRoomListener.getRemoteParticipant().getAudioTracks();
        List<RemoteVideoTrackPublication> remoteVideoTrackPublications =
                aliceRoomListener.getRemoteParticipant().getRemoteVideoTracks();
        List<VideoTrackPublication> videoTrackPublications =
                aliceRoomListener.getRemoteParticipant().getVideoTracks();

        // RemoteParticipant two disconnects
        aliceRoomListener.onParticipantDisconnectedLatch = new CountDownLatch(1);
        bobRoom.disconnect();
        assertTrue(
                aliceRoomListener.onParticipantDisconnectedLatch.await(
                        TestUtils.STATE_TRANSITION_TIMEOUT, TimeUnit.SECONDS));
        RemoteParticipant remoteParticipant = aliceRoomListener.getRemoteParticipant();

        // Validate enabled matches last known state
        assertEquals(
                remoteAudioTrackPublications.get(0).isTrackEnabled(),
                remoteParticipant.getRemoteAudioTracks().get(0).isTrackEnabled());
        assertEquals(
                audioTrackPublications.get(0).isTrackEnabled(),
                remoteParticipant.getAudioTracks().get(0).isTrackEnabled());
        assertEquals(
                remoteVideoTrackPublications.get(0).isTrackEnabled(),
                remoteParticipant.getRemoteVideoTracks().get(0).isTrackEnabled());
        assertEquals(
                videoTrackPublications.get(0).isTrackEnabled(),
                remoteParticipant.getVideoTracks().get(0).isTrackEnabled());

        // Validate alice is no longer subscribed to bob tracks
        assertFalse(remoteParticipant.getRemoteAudioTracks().get(0).isTrackSubscribed());
        assertEquals(
                remoteAudioTrackPublications.get(0).isTrackSubscribed(),
                remoteParticipant.getRemoteAudioTracks().get(0).isTrackSubscribed());
        assertFalse(remoteParticipant.getRemoteVideoTracks().get(0).isTrackSubscribed());
        assertEquals(
                remoteVideoTrackPublications.get(0).isTrackSubscribed(),
                remoteParticipant.getRemoteVideoTracks().get(0).isTrackSubscribed());

        // Validate the track objects are equal
        assertEquals(
                remoteAudioTrackPublications.get(0),
                remoteParticipant.getRemoteAudioTracks().get(0));
        assertEquals(audioTrackPublications.get(0), remoteParticipant.getAudioTracks().get(0));
        assertEquals(
                remoteVideoTrackPublications.get(0),
                remoteParticipant.getRemoteVideoTracks().get(0));
        assertEquals(videoTrackPublications.get(0), remoteParticipant.getVideoTracks().get(0));
    }
}
